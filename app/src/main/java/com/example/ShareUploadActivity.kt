package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import com.example.data.model.UploadDestination
import com.example.ui.MainViewModel
import com.example.util.DestinationShortcutPublisher
import com.example.util.LocaleHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ShareUploadActivity : ComponentActivity() {

    companion object {
        private const val TAG = "ShareUploadActivity"
        const val EXTRA_DESTINATION_ID = "com.example.extra.DESTINATION_ID"
        const val EXTRA_DESTINATION_NAME = "com.example.extra.DESTINATION_NAME"
    }

    private var screenState by mutableStateOf<ShareScreenState>(ShareScreenState.Processing)

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
                    Surface(color = MaterialTheme.colorScheme.surface) {
                        when (val state = screenState) {
                            ShareScreenState.Processing -> ProcessingContent()
                            is ShareScreenState.PickDestination -> {
                                DestinationPickerContent(
                                    destinations = state.destinations,
                                    onDestinationSelected = { destination ->
                                        screenState = ShareScreenState.Processing
                                        processAndQueueSharedUris(
                                            viewModel,
                                            state.uris,
                                            destination.id
                                        )
                                    },
                                    onCancel = { finish() }
                                )
                            }
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
        val destinationId = destinationIdFromIntent()

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

        lifecycleScope.launch {
            val enabledDestinations = withContext(Dispatchers.IO) {
                viewModel.enabledDestinationsOnce()
            }
            val textContext = localizedTextContext(viewModel)

            when {
                destinationId != null -> processAndQueueSharedUris(viewModel, uris.toList(), destinationId)
                enabledDestinations.isEmpty() -> {
                    Toast.makeText(
                        this@ShareUploadActivity,
                        textContext.getString(R.string.toast_no_upload_destinations),
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                }
                enabledDestinations.size == 1 -> {
                    processAndQueueSharedUris(viewModel, uris.toList(), enabledDestinations.first().id)
                }
                else -> {
                    screenState = ShareScreenState.PickDestination(
                        destinations = enabledDestinations,
                        uris = uris.toList()
                    )
                }
            }
        }
    }

    private fun processAndQueueSharedUris(
        viewModel: MainViewModel,
        uris: List<Uri>,
        destinationId: String?
    ) {
        viewModel.addSelectedFiles(uris, destinationId) { result ->
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

    private fun destinationIdFromIntent(): String? {
        return intent.getStringExtra(EXTRA_DESTINATION_ID)
            ?: DestinationShortcutPublisher.destinationIdFromShortcutId(
                intent.getStringExtra(Intent.EXTRA_SHORTCUT_ID)
            )
    }
}

private sealed interface ShareScreenState {
    object Processing : ShareScreenState
    data class PickDestination(
        val destinations: List<UploadDestination>,
        val uris: List<Uri>
    ) : ShareScreenState
}

@Composable
private fun ProcessingContent() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            stringResource(R.string.share_adding_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            stringResource(R.string.share_adding_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary
        )
    }
}

@Composable
fun DestinationPickerContent(
    destinations: List<UploadDestination>,
    onDestinationSelected: (UploadDestination) -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.CloudUpload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                text = stringResource(R.string.share_choose_destination_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = stringResource(R.string.share_choose_destination_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        LazyColumn(
            modifier = Modifier.heightIn(max = 360.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(destinations, key = { it.id }) { destination ->
                Card(
                    onClick = { onDestinationSelected(destination) },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = destination.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = stringResource(
                                    R.string.destination_folder_label,
                                    if (destination.driveAccountId.isNullOrBlank()) {
                                        stringResource(R.string.no_onedrive_account_selected)
                                    } else {
                                        destination.driveAccountLabel
                                            ?: stringResource(R.string.no_onedrive_account_selected)
                                    },
                                    destination.folderPath
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
        TextButton(
            onClick = onCancel,
            modifier = Modifier.align(Alignment.End)
        ) {
            Text(stringResource(R.string.cancel))
        }
    }
}
