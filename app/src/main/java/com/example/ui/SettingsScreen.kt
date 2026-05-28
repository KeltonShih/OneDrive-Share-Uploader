package com.example.ui

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.auth.AuthState
import com.example.data.model.AppLanguage
import com.example.data.model.ConflictBehavior
import com.example.data.model.UploadDestination

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToAbout: () -> Unit
) {
    val context = LocalContext.current
    val settings by viewModel.appSettings.collectAsState()
    val authState by viewModel.authState.collectAsState()
    val folderPickerState by viewModel.folderPickerState.collectAsState()
    val selectedLanguage = AppLanguage.fromCode(settings.languageCode)
    var showLanguageDialog by remember { mutableStateOf(false) }
    var editingDestination by remember { mutableStateOf<UploadDestination?>(null) }
    var showDestinationDialog by remember { mutableStateOf(false) }
    var folderPickerDestinationId by remember { mutableStateOf<String?>(null) }
    var folderPickerReturnsToDialog by remember { mutableStateOf(false) }
    var destinationDraftName by remember { mutableStateOf("") }
    var destinationDraftPath by remember { mutableStateOf("") }
    val draftDestinationId = "__draft_destination__"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back_to_upload_center)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudQueue,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.onedrive_login_status),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    HorizontalDivider()

                    when (val auth = authState) {
                        is AuthState.Uninitialized -> {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                        is AuthState.SignedOut -> {
                            Text(
                                stringResource(R.string.not_signed_in),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = {
                                    val activity = context as? Activity ?: return@Button
                                    viewModel.signIn(activity) { /* Handle Result Toast on MainActivity or inside Activity */ }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("msal_sign_in_button")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.Login, contentDescription = null)
                                    Text(stringResource(R.string.sign_in_with_microsoft))
                                }
                            }
                        }
                        is AuthState.SignedIn -> {
                            Text(
                                stringResource(R.string.signed_in_as),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = auth.account.username,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.testTag("msal_account_text")
                            )
                            OutlinedButton(
                                onClick = {
                                    viewModel.signOut { /* Done */ }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("msal_sign_out_button"),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.Logout, contentDescription = null)
                                    Text(stringResource(R.string.sign_out))
                                }
                            }
                        }
                        is AuthState.Error -> {
                            Text(
                                text = stringResource(
                                    R.string.auth_error,
                                    auth.exception.message.orEmpty()
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Button(
                                onClick = {
                                    val activity = context as? Activity ?: return@Button
                                    viewModel.signIn(activity) { }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.try_sign_in_again))
                            }
                        }
                    }
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showLanguageDialog = true },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                ListItem(
                    headlineContent = {
                        Text(
                            text = stringResource(R.string.language_title),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    supportingContent = {
                        Text(
                            text = languageLabel(selectedLanguage),
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingContent = {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null
                        )
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DriveFolderUpload,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = stringResource(R.string.upload_destinations),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        FilledTonalButton(
                            onClick = {
                                editingDestination = null
                                destinationDraftName = ""
                                destinationDraftPath = ""
                                showDestinationDialog = true
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.add_destination))
                        }
                    }

                    Text(
                        text = stringResource(R.string.upload_destinations_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    HorizontalDivider()

                    settings.uploadDestinations.sortedBy { it.sortOrder }.forEach { destination ->
                        DestinationSettingsRow(
                            destination = destination,
                            canDelete = settings.uploadDestinations.size > 1,
                            onEdit = {
                                editingDestination = destination
                                destinationDraftName = destination.displayName
                                destinationDraftPath = destination.folderPath
                                showDestinationDialog = true
                            },
                            onDelete = {
                                viewModel.deleteDestination(destination.id)
                            }
                        )
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.conflict_resolution),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    HorizontalDivider()

                    ConflictBehavior.values().forEach { behavior ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (settings.conflictBehavior == behavior),
                                onClick = { viewModel.updateConflictBehavior(behavior) },
                                modifier = Modifier.testTag("conflict_${behavior.name.lowercase()}_radio")
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = when (behavior) {
                                        ConflictBehavior.RENAME -> stringResource(R.string.conflict_rename_title)
                                        ConflictBehavior.REPLACE -> stringResource(R.string.conflict_replace_title)
                                        ConflictBehavior.FAIL -> stringResource(R.string.conflict_fail_title)
                                    },
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = when (behavior) {
                                        ConflictBehavior.RENAME -> stringResource(R.string.conflict_rename_desc)
                                        ConflictBehavior.REPLACE -> stringResource(R.string.conflict_replace_desc)
                                        ConflictBehavior.FAIL -> stringResource(R.string.conflict_fail_desc)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SettingsInputAntenna,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.network_constraints),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.wifi_only), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                stringResource(R.string.wifi_only_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.wifiOnly,
                            onCheckedChange = { viewModel.updateWifiOnly(it) },
                            modifier = Modifier.testTag("wifi_only_switch")
                        )
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.smart_routing), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                stringResource(R.string.smart_routing_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.rulesEnabled,
                            onCheckedChange = { viewModel.updateRulesEnabled(it) },
                            modifier = Modifier.testTag("rules_enabled_switch")
                        )
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFFFF1F1) // PolishErrorContainer
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF2B8B5)) // PolishErrorBorder
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.history_clean_actions),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = stringResource(R.string.history_clean_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { viewModel.clearHistory() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("clear_history_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.DeleteForever, contentDescription = null)
                            Text(stringResource(R.string.clear_completed_history))
                        }
                    }
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToAbout() },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                ListItem(
                    headlineContent = {
                        Text(
                            text = stringResource(R.string.about_title),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    supportingContent = {
                        Text(
                            text = stringResource(R.string.about_subtitle),
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingContent = {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null
                        )
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        }
    }

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(stringResource(R.string.language_dialog_title)) },
            text = {
                Column {
                    AppLanguage.entries.forEach { language ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.updateLanguage(language.code)
                                    showLanguageDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedLanguage == language,
                                onClick = {
                                    viewModel.updateLanguage(language.code)
                                    showLanguageDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(languageLabel(language))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showDestinationDialog) {
        DestinationEditDialog(
            destination = editingDestination,
            initialName = destinationDraftName,
            initialPath = destinationDraftPath,
            onDismiss = { showDestinationDialog = false },
            onBrowse = { name, path ->
                destinationDraftName = name
                destinationDraftPath = path
                folderPickerReturnsToDialog = true
                folderPickerDestinationId = editingDestination?.id ?: draftDestinationId
                showDestinationDialog = false
                viewModel.openFolderPicker()
            },
            onSave = { name, path ->
                val editing = editingDestination
                if (editing == null) {
                    viewModel.addDestination(name, path)
                } else {
                    viewModel.updateDestination(
                        editing.copy(
                            displayName = name,
                            folderPath = path
                        )
                    )
                }
                showDestinationDialog = false
            }
        )
    }

    if (folderPickerState.isOpen && folderPickerDestinationId != null) {
        OneDriveFolderPickerDialog(
            state = folderPickerState,
            onDismiss = {
                if (folderPickerReturnsToDialog) {
                    showDestinationDialog = true
                }
                folderPickerReturnsToDialog = false
                folderPickerDestinationId = null
                viewModel.closeFolderPicker()
            },
            onOpenFolder = { folder ->
                viewModel.loadFolderChildren(folder.id, folder.path)
            },
            onUseCurrentFolder = {
                val selectedPath = folderPickerState.currentPath.ifBlank { "/" }
                val id = folderPickerDestinationId
                if (folderPickerReturnsToDialog || id == draftDestinationId) {
                    destinationDraftPath = selectedPath
                    showDestinationDialog = true
                } else {
                    id?.let {
                        viewModel.updateDestinationFolder(it, selectedPath)
                    }
                }
                folderPickerReturnsToDialog = false
                folderPickerDestinationId = null
                viewModel.closeFolderPicker()
            },
            onCreateFolder = { folderName ->
                viewModel.createFolderInPicker(folderName)
            },
            onRefresh = {
                viewModel.loadFolderChildren(
                    folderPickerState.currentItemId,
                    folderPickerState.currentPath
                )
            },
            onRoot = {
                viewModel.loadFolderChildren(null, "")
            }
        )
    }
}

@Composable
private fun DestinationSettingsRow(
    destination: UploadDestination,
    canDelete: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = destination.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = destination.folderPath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(
                        R.string.destination_account_label,
                        destination.driveAccountLabel
                            ?: UploadDestination.CURRENT_ACCOUNT_LABEL
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(1.dp))
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                TextButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.edit))
                }
                if (canDelete) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.delete))
                    }
                }
            }
        }
    }
}

@Composable
private fun DestinationEditDialog(
    destination: UploadDestination?,
    initialName: String,
    initialPath: String,
    onDismiss: () -> Unit,
    onBrowse: (String, String) -> Unit,
    onSave: (String, String) -> Unit
) {
    var name by remember(destination?.id) {
        mutableStateOf(initialName)
    }
    val path = remember(destination?.id, initialPath) {
        mutableStateOf(initialPath)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (destination == null) {
                    stringResource(R.string.add_destination)
                } else {
                    stringResource(R.string.edit_destination)
                }
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.destination_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = { onBrowse(name, path.value) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.choose_upload_folder))
                }
                Text(
                    text = path.value.ifBlank { stringResource(R.string.no_folder_selected) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (path.value.isBlank()) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Text(
                    text = stringResource(R.string.destination_account_future_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, path.value) },
                enabled = name.isNotBlank() && path.value.isNotBlank()
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun languageLabel(language: AppLanguage): String {
    return when (language) {
        AppLanguage.SYSTEM -> stringResource(R.string.language_system)
        AppLanguage.ZH_TW -> stringResource(R.string.language_zh_tw)
        AppLanguage.ZH_CN -> stringResource(R.string.language_zh_cn)
        AppLanguage.EN -> stringResource(R.string.language_en)
        AppLanguage.JA -> stringResource(R.string.language_ja)
        AppLanguage.KO -> stringResource(R.string.language_ko)
        AppLanguage.ES -> stringResource(R.string.language_es)
    }
}
