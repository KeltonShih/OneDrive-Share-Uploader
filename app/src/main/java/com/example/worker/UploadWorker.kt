package com.example.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.pm.ServiceInfo
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.R
import com.example.auth.MsalAuthManager
import com.example.data.local.AppDatabase
import com.example.data.local.DataStoreManager
import com.example.data.model.ConflictBehavior
import com.example.data.model.UploadJobEntity
import com.example.data.model.UploadStatus
import com.example.util.FileHelper
import com.example.util.LocaleHelper
import com.example.util.ProgressRequestBody
import kotlinx.coroutines.flow.first
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Call
import okhttp3.ConnectionPool
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.withTimeoutOrNull

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
        .callTimeout(90, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
        .build()

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    @Volatile
    private var activeCall: Call? = null
    @Volatile
    private var textContext: Context = context

    init {
        createNotificationChannel()
    }

    override suspend fun doWork(): Result {
        textContext = LocaleHelper.localizedContext(
            context,
            dataStore.appSettingsFlow.first().languageCode
        )
        // Run as Foreground Service to keep it alive
        setForeground(getForegroundInfo())
        repository.resetUploadingJobsToQueued(System.currentTimeMillis())

        var loop = true
        while (loop) {
            val job = repository.getNextQueuedJob() ?: break
            
            // Logically process files
            try {
                Log.d(TAG, "Processing job: ${job.id} - ${job.originalFileName}")
                processUpload(job)
            } catch (e: Exception) {
                Log.e(TAG, "Job failed with exception: ${job.id}", e)
                markJobFailed(job, e.localizedMessage ?: e.message ?: t(R.string.error_unknown))
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
            errorMessage = t(R.string.upload_stage_initializing),
            updatedAt = System.currentTimeMillis()
        )
        repository.updateJob(uploadingJob)
        updateForegroundNotification(uploadingJob, t(R.string.initializing))

        // 2. Fetch MSAL Access Token
        updateJobStage(uploadingJob, t(R.string.upload_stage_get_token))
        val token = withTimeoutOrNull(30_000L) {
            msalAuthManager.getAccessTokenSilently()
        }
        if (token == null) {
            val error = t(R.string.error_token_required)
            markJobFailed(uploadingJob, error)
            return
        }

        val cacheFile = File(uploadingJob.localCachePath)
        updateJobStage(uploadingJob, t(R.string.upload_stage_checking_cache))
        if (!cacheFile.exists()) {
            markJobFailed(uploadingJob, t(R.string.error_cache_missing))
            return
        }

        val fileLength = cacheFile.length()
        if (fileLength <= 0) {
            markJobFailed(uploadingJob, t(R.string.error_cache_empty))
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
        val encodedPath = buildEncodedPath(cleanFolder, uploadingJob.sanitizedFileName)

        // Decide Upload API: Small PUT (<= 250MB) vs chunked Session (> 250MB)
        if (fileLength <= 250 * 1024 * 1024) {
            updateJobStage(uploadingJob, t(R.string.upload_stage_graph))
            uploadSmallFile(uploadingJob, token, cacheFile, encodedPath, conflictBehaviorStr)
        } else {
            updateJobStage(uploadingJob, t(R.string.upload_stage_large_session))
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
            val rawProgress = if (total > 0L) ((written * 100) / total).toInt() else 0
            val progressPercent = rawProgress.coerceAtMost(99)
            val now = System.currentTimeMillis()
            if (now - lastUpdate > 500L) {
                lastUpdate = now
                val updated = job.copy(
                    progress = progressPercent,
                    uploadedBytes = written,
                    totalBytes = total,
                    errorMessage = t(R.string.upload_stage_small_progress, progressPercent),
                    updatedAt = now
                )
                // Direct DB update using runBlocking from OkHttp's writer thread pool context
                kotlinx.coroutines.runBlocking {
                    repository.updateJob(updated)
                    updateForegroundNotification(updated, t(R.string.upload_stage_small_progress, progressPercent))
                }
            }
        }

        val request = Request.Builder()
            .url(url)
            .put(progressBody)
            .addHeader("Authorization", "Bearer $token")
            .build()

        executeRequest(request).use { response ->
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                markJobSuccess(job, parseDriveItemName(responseBody))
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
        updateForegroundNotification(job, t(R.string.upload_stage_large_session))

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
        executeRequest(sessionRequest).use { response ->
            if (response.isSuccessful) {
                val respJson = JSONObject(response.body?.string() ?: "")
                uploadUrl = respJson.getString("uploadUrl")
            } else {
                val errorBody = response.body?.string() ?: ""
                val errorMessage = t(R.string.error_session_creation, parseErrorMessage(response, errorBody))
                markJobFailed(job, errorMessage)
                return
            }
        }

        val destinationUrl = uploadUrl
        if (destinationUrl == null) {
            markJobFailed(job, t(R.string.error_upload_url_missing))
            return
        }

        // 2. Chunks Loop
        val totalBytes = file.length()
        val buffer = ByteArray(CHUNK_SIZE)
        val fileReader = RandomAccessFile(file, "r")
        var bytesUploaded: Long = 0
        var uploadedFileName: String? = null

        try {
            while (bytesUploaded < totalBytes) {
                if (isStopped) {
                    markJobFailed(job, t(R.string.error_worker_stopped))
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

                executeRequest(chunkRequest).use { chunkResponse ->
                    val code = chunkResponse.code
                    if (code == 200 || code == 201 || code == 202) {
                        val responseBody = chunkResponse.body?.string()
                        if (code == 200 || code == 201) {
                            uploadedFileName = parseDriveItemName(responseBody)
                        }
                        bytesUploaded += bytesRead
                        val rawProgress = ((bytesUploaded * 100) / totalBytes).toInt()
                        val progressPercent = rawProgress.coerceAtMost(99)
                        val updated = job.copy(
                            progress = progressPercent,
                            uploadedBytes = bytesUploaded,
                            totalBytes = totalBytes,
                            errorMessage = if (rawProgress >= 100) {
                                t(R.string.upload_stage_finalizing)
                            } else {
                                t(R.string.upload_stage_chunk_progress, progressPercent)
                            },
                            updatedAt = System.currentTimeMillis()
                        )
                        repository.updateJob(updated)
                        updateForegroundNotification(
                            updated,
                            if (rawProgress >= 100) {
                                t(R.string.notification_finalizing)
                            } else {
                                t(R.string.upload_stage_chunk_progress, progressPercent)
                            }
                        )
                    } else {
                        val errorBody = chunkResponse.body?.string() ?: ""
                        val chunkError = t(R.string.error_chunk_failed, chunkResponse.message, errorBody)
                        markJobFailed(job, chunkError)
                        return
                    }
                }
            }

            // Finish
            markJobSuccess(job, uploadedFileName)
        } finally {
            try { fileReader.close() } catch (ignored: Exception) {}
        }
    }

    private fun buildEncodedPath(cleanFolder: String, fileName: String): String {
        return if (cleanFolder.isEmpty()) {
            FileHelper.encodeGraphPathSegment(fileName)
        } else {
            "${FileHelper.encodeGraphFolderPath(cleanFolder)}/${FileHelper.encodeGraphPathSegment(fileName)}"
        }
    }

    private suspend fun markJobSuccess(job: UploadJobEntity, uploadedFileName: String?) {
        val successJob = job.copy(
            status = UploadStatus.SUCCESS,
            progress = 100,
            errorMessage = null,
            uploadedFileName = uploadedFileName ?: job.uploadedFileName,
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
            completedAt = null
        )
        repository.updateJob(failedJob)
    }

    private suspend fun updateJobStage(job: UploadJobEntity, message: String) {
        repository.updateJob(
            job.copy(
                errorMessage = message,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    private fun executeRequest(request: Request): Response {
        if (isStopped) {
            throw InterruptedException("Upload was cancelled")
        }
        val call = okHttpClient.newCall(request)
        activeCall = call
        return try {
            call.execute()
        } finally {
            if (activeCall === call) {
                activeCall = null
            }
        }
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

    private fun parseDriveItemName(body: String?): String? {
        if (body.isNullOrBlank()) return null
        return runCatching {
            JSONObject(body).optString("name").takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    // ==========================================
    // NOTIFICATION & FOREGROUND INFO MANAGEMENT
    // ==========================================

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = createNotification(
            t(R.string.notification_queue_initialized),
            t(R.string.notification_preparing_uploads)
        )
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
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
        val cancelIntent = WorkManager.getInstance(context).createCancelPendingIntent(id)

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, t(R.string.notification_cancel), cancelIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private suspend fun updateForegroundNotification(currentJob: UploadJobEntity, detail: String) {
        val title = t(R.string.notification_uploading_title)
        val text = t(R.string.notification_file_detail, currentJob.originalFileName, detail)
        
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
            .setContentTitle(t(R.string.notification_summary_title))
            .setContentText(t(R.string.notification_summary_text))
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
                t(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = t(R.string.notification_channel_desc)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun t(resId: Int, vararg args: Any): String {
        return textContext.getString(resId, *args)
    }

}
