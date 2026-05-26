package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.auth.AuthState
import com.example.auth.MsalAuthManager
import com.example.data.local.AppDatabase
import com.example.data.local.DataStoreManager
import com.example.data.model.UploadJobEntity
import com.example.data.model.UploadStatus
import com.example.util.FileHelper
import com.example.util.FolderResolver
import com.example.worker.UploadWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class ShareUploadActivity : ComponentActivity() {

    companion object {
        private const val TAG = "ShareUploadActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Adding shared file(s) to OneDrive queue...",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "Copying to local private storage...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }

        handleShareIntent()
    }

    private fun handleShareIntent() {
        val action = intent.action
        val type = intent.type

        val uris = mutableListOf<Uri>()

        if (Intent.ACTION_SEND == action && type != null) {
            val streamUri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            if (streamUri != null) {
                uris.add(streamUri)
            }
        } else if (Intent.ACTION_SEND_MULTIPLE == action && type != null) {
            val list = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
            if (list != null) {
                uris.addAll(list.filterNotNull())
            }
        }

        if (uris.isEmpty()) {
            Toast.makeText(this, "No shareable file URI found in intent.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        processAndQueueSharedUris(uris)
    }

    private fun processAndQueueSharedUris(uris: List<Uri>) {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(this@ShareUploadActivity)
            val repository = db.uploadJobDao()
            val dataStore = DataStoreManager(this@ShareUploadActivity)
            val authManager = MsalAuthManager.getInstance(this@ShareUploadActivity)

            // 1. Fetch current settings & authentication status
            val settings = dataStore.appSettingsFlow.first()
            val authState = authManager.authStateFlow.first()
            val isLoggedIn = authState is AuthState.SignedIn

            var successCopiedCount = 0
            val jobsToInsert = mutableListOf<UploadJobEntity>()

            withContext(Dispatchers.IO) {
                for (uri in uris) {
                    try {
                        val originalName = FileHelper.queryDisplayName(this@ShareUploadActivity, uri)
                        val sanitizedName = FileHelper.sanitizeFileName(originalName)
                        val fileSize = FileHelper.queryFileSize(this@ShareUploadActivity, uri)
                        val mimeType = FileHelper.getMimeType(this@ShareUploadActivity, uri)
                        val targetFolder = FolderResolver.resolveTargetFolder(sanitizedName, mimeType, settings)

                        // Copy to secure private local app cache first (MANDATORY standard)
                        val cacheFile = FileHelper.copyUriToCache(this@ShareUploadActivity, uri, sanitizedName)
                        
                        // Decide initial status
                        val (initialStatus, errorText) = if (isLoggedIn) {
                            Pair(UploadStatus.QUEUED, null)
                        } else {
                            // Authentic required as per instruction Section XIII
                            Pair(UploadStatus.FAILED, "Microsoft account not signed in. Please log in in Settings.")
                        }

                        val now = System.currentTimeMillis()
                        val uploaderJob = UploadJobEntity(
                            id = UUID.randomUUID().toString(),
                            localCachePath = cacheFile.absolutePath,
                            originalFileName = originalName,
                            sanitizedFileName = sanitizedName,
                            mimeType = mimeType,
                            fileSize = if (fileSize > 0) fileSize else cacheFile.length(),
                            targetFolder = targetFolder,
                            status = initialStatus,
                            progress = 0,
                            uploadedBytes = 0L,
                            totalBytes = if (fileSize > 0) fileSize else cacheFile.length(),
                            errorMessage = errorText,
                            createdAt = now,
                            updatedAt = now,
                            completedAt = null,
                            retryCount = 0
                        )

                        repository.insertJob(uploaderJob)
                        successCopiedCount++
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed copy for shared URI: $uri", e)
                    }
                }
            }

            // Toast feedback
            if (successCopiedCount > 0) {
                if (isLoggedIn) {
                    Toast.makeText(
                        this@ShareUploadActivity,
                        "Added $successCopiedCount file(s) to OneDrive queue!",
                        Toast.LENGTH_LONG
                    ).show()

                    // Start background uploading worker
                    triggerWorkManager(settings.wifiOnly)

                    // Return to MainActivity
                    val mainIntent = Intent(this@ShareUploadActivity, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    }
                    startActivity(mainIntent)
                } else {
                    Toast.makeText(
                        this@ShareUploadActivity,
                        "Parsed $successCopiedCount file(s), but Microsoft Login is required.",
                        Toast.LENGTH_LONG
                    ).show()

                    // Redirect to Settings Screen in MainActivity
                    val mainIntent = Intent(this@ShareUploadActivity, MainActivity::class.java).apply {
                        putExtra("NAVIGATE_TO_SETTINGS", true)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    }
                    startActivity(mainIntent)
                }
            } else {
                Toast.makeText(
                    this@ShareUploadActivity,
                    "Failed to process shared files.",
                    Toast.LENGTH_LONG
                ).show()
                // Redirect back to main
                val mainIntent = Intent(this@ShareUploadActivity, MainActivity::class.java)
                startActivity(mainIntent)
            }

            finish()
        }
    }

    private fun triggerWorkManager(wifiOnly: Boolean) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(
                if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
            )
            .build()

        val uploadWorkRequest = OneTimeWorkRequestBuilder<UploadWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this)
            .enqueueUniqueWork("OneDriveUploader", ExistingWorkPolicy.KEEP, uploadWorkRequest)
    }
}
