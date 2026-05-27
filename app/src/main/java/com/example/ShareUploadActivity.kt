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
import androidx.lifecycle.ViewModelProvider
import com.example.ui.MainViewModel
import com.example.worker.UploadWorker

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

        val uris = linkedSetOf<Uri>()

        if (Intent.ACTION_SEND == action) {
            intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let { uris.add(it) }
        } else if (Intent.ACTION_SEND_MULTIPLE == action) {
            val list = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
            if (list != null) {
                uris.addAll(list.filterNotNull())
            }
        } else if (Intent.ACTION_VIEW == action) {
            intent.data?.let { uris.add(it) }
        }

        intent.clipData?.let { clipData ->
            for (index in 0 until clipData.itemCount) {
                clipData.getItemAt(index).uri?.let { uris.add(it) }
            }
        }

        if (uris.isEmpty()) {
            Toast.makeText(this, "No shareable file URI found in intent.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        processAndQueueSharedUris(uris.toList())
    }

    private fun processAndQueueSharedUris(uris: List<Uri>) {
        val viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        viewModel.addSelectedFiles(uris) { result ->
            try {
                when {
                    result.addedCount > 0 -> {
                        Toast.makeText(
                            this@ShareUploadActivity,
                            if (result.failedCount > 0) {
                                "Added ${result.addedCount} file(s). ${result.failedCount} file(s) need attention."
                            } else {
                                "Added ${result.addedCount} file(s) to OneDrive queue!"
                            },
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    else -> {
                        Toast.makeText(
                            this@ShareUploadActivity,
                            "Failed to process shared files.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected share processing failure", e)
                Toast.makeText(
                    this@ShareUploadActivity,
                    "Share failed: ${e.message ?: "Unknown error"}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                finish()
            }
        }
    }
}
