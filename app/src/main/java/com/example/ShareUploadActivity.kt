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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import com.example.ui.MainViewModel
import com.example.util.LocaleHelper

class ShareUploadActivity : ComponentActivity() {

    companion object {
        private const val TAG = "ShareUploadActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        setContent {
            val settings by viewModel.appSettings.collectAsState()
            val baseContext = LocalContext.current
            val localizedContext = remember(settings.languageCode) {
                LocaleHelper.localizedContext(baseContext, settings.languageCode)
            }

            CompositionLocalProvider(LocalContext provides localizedContext) {
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
                                stringResource(R.string.share_adding_title),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                stringResource(R.string.share_adding_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
        }

        handleShareIntent(viewModel)
    }

    private fun handleShareIntent(viewModel: MainViewModel) {
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
            val textContext = localizedTextContext(viewModel)
            val messageRes = if (hasNonFileShareContent()) {
                R.string.toast_unsupported_share_content
            } else {
                R.string.toast_no_share_uri
            }
            Toast.makeText(this, textContext.getString(messageRes), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        processAndQueueSharedUris(viewModel, uris.toList())
    }

    private fun processAndQueueSharedUris(viewModel: MainViewModel, uris: List<Uri>) {
        viewModel.addSelectedFiles(uris) { result ->
            val textContext = localizedTextContext(viewModel)
            try {
                when {
                    result.addedCount > 0 -> {
                        Toast.makeText(
                            this@ShareUploadActivity,
                            if (result.failedCount > 0) {
                                textContext.getString(
                                    R.string.toast_files_added_with_failures,
                                    result.addedCount,
                                    result.failedCount
                                )
                            } else {
                                textContext.getString(R.string.toast_files_added, result.addedCount)
                            },
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    else -> {
                        Toast.makeText(
                            this@ShareUploadActivity,
                            textContext.getString(R.string.toast_process_shared_failed),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected share processing failure", e)
                Toast.makeText(
                    this@ShareUploadActivity,
                    textContext.getString(R.string.toast_share_failed, e.message ?: "Unknown error"),
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                finish()
            }
        }
    }

    private fun localizedTextContext(viewModel: MainViewModel) =
        LocaleHelper.localizedContext(this, viewModel.appSettings.value.languageCode)

    private fun hasNonFileShareContent(): Boolean {
        val type = intent.type.orEmpty()
        return type.startsWith("text/") ||
            intent.hasExtra(Intent.EXTRA_TEXT) ||
            intent.hasExtra(Intent.EXTRA_HTML_TEXT) ||
            intent.hasExtra(Intent.EXTRA_SUBJECT)
    }
}
