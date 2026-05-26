package com.example.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.auth.MsalAuthManager
import com.example.data.local.AppDatabase
import com.example.data.local.DataStoreManager
import com.example.data.model.ConflictBehavior
import com.example.data.model.UploadJobEntity
import com.example.data.model.UploadStatus
import com.example.util.FileHelper
import com.example.util.ProgressRequestBody
import kotlinx.coroutines.flow.first
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.util.concurrent.TimeUnit

class UploadWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "UploadWorker"
        private const val CHANNEL_ID = "UploadsChannel"
        private const val NOTIFICATION_ID = 1001

        // Chunk size for upload session: must be a multiple of 320 KiB (327,680 bytes)
        // 5 * 320 KiB = 1.6 MiB is an excellent responsive chunk size for mobile.
        private const val CHUNK_SIZE = 327680 * 5 
    }

    private val db = AppDatabase.getDatabase(context)
    private val repository = db.uploadJobDao()
    private val dataStore = DataStoreManager(context)
    private val msalAuthManager = MsalAuthManager.getInstance(context)
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
    }

    override suspend fun doWork(): Result {
        // Run as Foreground Service to keep it alive
        setForeground(getForegroundInfo())

        var loop = true
        while (loop) {
            val job = repository.getNextQueuedJob() ?: break
            
            // Logically process files
            try {
                Log.d(TAG, "Processing job: ${job.id} - ${job.originalFileName}")
                processUpload(job)
            } catch (e: Exception) {
                Log.e(TAG, "Job failed with exception: ${job.id}", e)
                markJobFailed(job, e.localizedMessage ?: e.message ?: "Unknown error")
            }

            // Small delay to prevent tight spin loops on failure
            kotlinx.coroutines.delay(500)
        }

        // Post result summary notification
        showSummaryNotification()
        return Result.success()
    }

    private suspend fun processUpload(job: UploadJobEntity) {
        // 1. Mark job as UPLOADING
        val uploadingJob = job.copy(
            status = UploadStatus.UPLOADING,
            progress = 0,
            updatedAt = System.currentTimeMillis()
        )
        repository.updateJob(uploadingJob)
        updateForegroundNotification(uploadingJob, "Initializing...")

        // 2. Fetch MSAL Access Token
        val token = msalAuthManager.getAccessTokenSilently()
        if (token == null) {
            val error = "Microsoft account not signed in. Please sign in in Settings."
            markJobFailed(uploadingJob, error)
            return
        }

        val cacheFile = File(uploadingJob.localCachePath)
        if (!cacheFile.exists()) {
            markJobFailed(uploadingJob, "Temp cache file not found")
            return
        }

        val fileLength = cacheFile.length()
        if (fileLength <= 0) {
            markJobFailed(uploadingJob, "Local cache file is empty")
            return
        }

        // Read settings for behavior
        val settings = dataStore.appSettingsFlow.first()
        val conflictBehaviorStr = when (settings.conflictBehavior) {
            ConflictBehavior.RENAME -> "rename"
            ConflictBehavior.REPLACE -> "replace"
            ConflictBehavior.FAIL -> "fail"
        }

        // 3. Resolve destination URL path
        val cleanFolder = uploadingJob.targetFolder.trim('/')
        val encodedPath = if (cleanFolder.isEmpty()) {
            FileHelper.encodeGraphPathSegment(uploadingJob.sanitizedFileName)
        } else {
            "${FileHelper.encodeGraphFolderPath(cleanFolder)}/${FileHelper.encodeGraphPathSegment(uploadingJob.sanitizedFileName)}"
        }

        // Decide Upload API: Small PUT (<= 250MB) vs chunked Session (> 250MB)
        if (fileLength <= 250 * 1024 * 1024) {
            uploadSmallFile(uploadingJob, token, cacheFile, encodedPath, conflictBehaviorStr)
        } else {
            uploadLargeFile(uploadingJob, token, cacheFile, encodedPath, conflictBehaviorStr)
        }
    }

    private suspend fun uploadSmallFile(
        job: UploadJobEntity,
        token: String,
        file: File,
        encodedPath: String,
        conflictBehavior: String
    ) {
        // PUT https://graph.microsoft.com/v1.0/me/drive/root:/folder/file:/content?@microsoft.graph.conflictBehavior=rename
        val url = "https://graph.microsoft.com/v1.0/me/drive/root:/$encodedPath:/content?@microsoft.graph.conflictBehavior=$conflictBehavior"

        val mediaType = (job.mimeType ?: "application/octet-stream").toMediaTypeOrNull()
        
        var lastUpdate = 0L
        val progressBody = ProgressRequestBody(file, mediaType) { written, total ->
            val progressPercent = if (total > 0L) ((written * 100) / total).toInt() else 0
            val now = System.currentTimeMillis()
            if (now - lastUpdate > 500L || progressPercent == 100) {
                lastUpdate = now
                val updated = job.copy(
                    progress = progressPercent,
                    uploadedBytes = written,
                    totalBytes = total,
                    updatedAt = now
                )
                // Direct DB update using runBlocking from OkHttp's writer thread pool context
                kotlinx.coroutines.runBlocking {
                    repository.updateJob(updated)
                    updateForegroundNotification(updated, "Uploading small file... $progressPercent%")
                }
            }
        }

        val request = Request.Builder()
            .url(url)
            .put(progressBody)
            .addHeader("Authorization", "Bearer $token")
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                markJobSuccess(job)
            } else {
                val errorBody = response.body?.string() ?: ""
                val errorMessage = parseErrorMessage(response, errorBody)
                markJobFailed(job, errorMessage)
            }
        }
    }

    private suspend fun uploadLargeFile(
        job: UploadJobEntity,
        token: String,
        file: File,
        encodedPath: String,
        conflictBehavior: String
    ) {
        // 1. Create Upload Session
        // POST https://graph.microsoft.com/v1.0/me/drive/root:/folder/file:/createUploadSession
        val url = "https://graph.microsoft.com/v1.0/me/drive/root:/$encodedPath:/createUploadSession"
        updateForegroundNotification(job, "Creating large file session...")

        val bodyJson = JSONObject().apply {
            put("item", JSONObject().apply {
                put("@microsoft.graph.conflictBehavior", conflictBehavior)
                put("name", job.sanitizedFileName)
            })
        }

        val sessionRequest = Request.Builder()
            .url(url)
            .post(bodyJson.toString().toRequestBody("application/json".toMediaTypeOrNull()))
            .addHeader("Authorization", "Bearer $token")
            .build()

        var uploadUrl: String? = null
        okHttpClient.newCall(sessionRequest).execute().use { response ->
            if (response.isSuccessful) {
                val respJson = JSONObject(response.body?.string() ?: "")
                uploadUrl = respJson.getString("uploadUrl")
            } else {
                val errorBody = response.body?.string() ?: ""
                val errorMessage = "Session Creation Error: " + parseErrorMessage(response, errorBody)
                markJobFailed(job, errorMessage)
                return
            }
        }

        val destinationUrl = uploadUrl
        if (destinationUrl == null) {
            markJobFailed(job, "Could not obtain upload URL session")
            return
        }

        // 2. Chunks Loop
        val totalBytes = file.length()
        val buffer = ByteArray(CHUNK_SIZE)
        val fileReader = RandomAccessFile(file, "r")
        var bytesUploaded: Long = 0

        try {
            while (bytesUploaded < totalBytes) {
                if (isStopped) {
                    markJobFailed(job, "Worker stopped/cancelled")
                    return
                }

                fileReader.seek(bytesUploaded)
                val bytesRead = fileReader.read(buffer)
                if (bytesRead <= 0) break

                val chunkStart = bytesUploaded
                val chunkEnd = bytesUploaded + bytesRead - 1

                val chunkRequestBody = buffer.toRequestBody("application/octet-stream".toMediaTypeOrNull(), 0, bytesRead)
                val chunkRequest = Request.Builder()
                    .url(destinationUrl)
                    .put(chunkRequestBody)
                    .addHeader("Content-Range", "bytes $chunkStart-$chunkEnd/$totalBytes")
                    .addHeader("Content-Length", bytesRead.toString())
                    .build()

                okHttpClient.newCall(chunkRequest).execute().use { chunkResponse ->
                    val code = chunkResponse.code
                    if (code == 200 || code == 201 || code == 202) {
                        bytesUploaded += bytesRead
                        val progressPercent = ((bytesUploaded * 100) / totalBytes).toInt()
                        val updated = job.copy(
                            progress = progressPercent,
                            uploadedBytes = bytesUploaded,
                            totalBytes = totalBytes,
                            updatedAt = System.currentTimeMillis()
                        )
                        repository.updateJob(updated)
                        updateForegroundNotification(updated, "Uploading chunk... $progressPercent%")
                    } else {
                        val errorBody = chunkResponse.body?.string() ?: ""
                        val chunkError = "Chunk upload failed: ${chunkResponse.message} - $errorBody"
                        markJobFailed(job, chunkError)
                        return
                    }
                }
            }

            // Finish
            markJobSuccess(job)
        } finally {
            try { fileReader.close() } catch (ignored: Exception) {}
        }
    }

    private suspend fun markJobSuccess(job: UploadJobEntity) {
        val successJob = job.copy(
            status = UploadStatus.SUCCESS,
            progress = 100,
            updatedAt = System.currentTimeMillis(),
            completedAt = System.currentTimeMillis()
        )
        repository.updateJob(successJob)
        
        // Delete local cache file
        try {
            val file = File(successJob.localCachePath)
            if (file.exists()) file.delete()
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting cache file", e)
        }
    }

    private suspend fun markJobFailed(job: UploadJobEntity, error: String) {
        val failedJob = job.copy(
            status = UploadStatus.FAILED,
            errorMessage = error,
            retryCount = job.retryCount + 1,
            updatedAt = System.currentTimeMillis(),
            completedAt = System.currentTimeMillis()
        )
        repository.updateJob(failedJob)
    }

    private fun parseErrorMessage(response: Response, body: String): String {
        return try {
            val obj = JSONObject(body)
            if (obj.has("error")) {
                val err = obj.getJSONObject("error")
                err.getString("message")
            } else {
                "HTTP ${response.code}: ${response.message}"
            }
        } catch (e: Exception) {
            "HTTP ${response.code}: ${response.message} (Raw: $body)"
        }
    }

    // ==========================================
    // NOTIFICATION & FOREGROUND INFO MANAGEMENT
    // ==========================================

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return ForegroundInfo(NOTIFICATION_ID, createNotification("Queue is initialized", "Preparing uploads..."))
    }

    private fun createNotification(title: String, text: String): Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private suspend fun updateForegroundNotification(currentJob: UploadJobEntity, detail: String) {
        val title = "Uploading to OneDrive"
        val text = "File: ${currentJob.originalFileName} ($detail)"
        
        val notification = createNotification(title, text)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun showSummaryNotification() {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val completedNotification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("OneDrive Upload Portfolio Finished")
            .setContentText("All pending and queued uploads are done.")
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(NOTIFICATION_ID + 1, completedNotification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Uploads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live progress of OneDrive Share Menu uploads."
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
}
