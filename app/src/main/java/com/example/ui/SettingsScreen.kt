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
import com.example.auth.OneDriveAccount
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
    val accounts = when (val auth = authState) {
        is AuthState.AccountsLoaded -> auth.accounts
        else -> emptyList()
    }
    val folderPickerState by viewModel.folderPickerState.collectAsState()
    val selectedLanguage = AppLanguage.fromCode(settings.languageCode)
    var showLanguageDialog by remember { mutableStateOf(false) }
    var editingDestination by remember { mutableStateOf<UploadDestination?>(null) }
    var destinationDraftIsNew by remember { mutableStateOf(true) }
    var destinationPendingDelete by remember { mutableStateOf<UploadDestination?>(null) }
    var showDestinationDialog by remember { mutableStateOf(false) }
    var folderPickerDestinationId by remember { mutableStateOf<String?>(null) }
    var folderPickerReturnsToDialog by remember { mutableStateOf(false) }
    var destinationDraftName by remember { mutableStateOf("") }
    var destinationDraftNameTouched by remember { mutableStateOf(false) }
    var destinationDraftPath by remember { mutableStateOf("") }
    var destinationDraftAccountId by remember { mutableStateOf<String?>(null) }
    var destinationDraftAccountLabel by remember { mutableStateOf<String?>(null) }
    var accountAliasTarget by remember { mutableStateOf<OneDriveAccount?>(null) }
    var accountAliasDraft by remember { mutableStateOf("") }
    var accountPendingRemove by remember { mutableStateOf<OneDriveAccount?>(null) }
    var showManageAccountsDialog by remember { mutableStateOf(false) }
    var showDestinationAccountDialog by remember { mutableStateOf(false) }
    var selectAccountThenOpenFolder by remember { mutableStateOf(false) }
    val draftDestinationId = "__draft_destination__"

    fun openFolderPickerForAccount(account: OneDriveAccount) {
        destinationDraftAccountId = account.id
        destinationDraftAccountLabel = account.label
        folderPickerReturnsToDialog = true
        folderPickerDestinationId = if (destinationDraftIsNew) {
            draftDestinationId
        } else {
            editingDestination?.id ?: draftDestinationId
        }
        showDestinationDialog = false
        viewModel.openFolderPicker(account)
    }

    fun browseWithSelectedAccount() {
        val selectedAccount = accounts.firstOrNull { it.id == destinationDraftAccountId }
        when {
            selectedAccount != null -> openFolderPickerForAccount(selectedAccount)
            accounts.size == 1 -> openFolderPickerForAccount(accounts.first())
            else -> {
                selectAccountThenOpenFolder = true
                showDestinationAccountDialog = true
            }
        }
    }

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
                                    viewModel.signIn(activity) { result ->
                                        result.getOrNull()?.let { account ->
                                            accountAliasTarget = account
                                            accountAliasDraft = account.label
                                        }
                                    }
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
                        is AuthState.AccountsLoaded -> {
                            auth.accounts.forEach { account ->
                                AccountStatusRow(
                                    account = account,
                                    onAlias = {
                                        accountAliasTarget = account
                                        accountAliasDraft = account.label
                                    },
                                    onRemove = { accountPendingRemove = account }
                                )
                            }
                            Button(
                                onClick = {
                                    val activity = context as? Activity ?: return@Button
                                    viewModel.signIn(activity) { result ->
                                        result.getOrNull()?.let { account ->
                                            accountAliasTarget = account
                                            accountAliasDraft = account.label
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.PersonAdd, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.add_account))
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
                                    viewModel.retryAuthInitialization()
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
                                destinationDraftIsNew = true
                                destinationDraftName = ""
                                destinationDraftNameTouched = false
                                destinationDraftPath = ""
                                destinationDraftAccountId = null
                                destinationDraftAccountLabel = null
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
                                destinationDraftIsNew = isDefaultPlaceholderDestination(destination)
                                destinationDraftName = destination.displayName
                                destinationDraftNameTouched = !isDefaultPlaceholderDestination(destination)
                                destinationDraftPath = destination.folderPath
                                destinationDraftAccountId = destination.driveAccountId
                                destinationDraftAccountLabel = destination.driveAccountLabel
                                showDestinationDialog = true
                            },
                            onDelete = {
                                destinationPendingDelete = destination
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
        val visibleDestinationDraftName = if (
            shouldAutoNameDestination(destinationDraftName, destinationDraftPath, destinationDraftNameTouched)
        ) {
            folderNameFromPath(destinationDraftPath)
        } else {
            destinationDraftName
        }
        DestinationEditDialog(
            destination = editingDestination,
            name = visibleDestinationDraftName,
            path = destinationDraftPath,
            accountLabel = destinationDraftAccountLabel,
            onNameChange = { name ->
                destinationDraftName = name
                destinationDraftNameTouched = true
            },
            onDismiss = { showDestinationDialog = false },
            onBrowse = {
                if (destinationDraftIsNew) {
                    destinationDraftName = ""
                    destinationDraftNameTouched = false
                }
                browseWithSelectedAccount()
            },
            onChooseAccount = {
                selectAccountThenOpenFolder = false
                showDestinationAccountDialog = true
            },
            onSave = { name, path ->
                val accountId = destinationDraftAccountId ?: return@DestinationEditDialog
                val accountLabel = destinationDraftAccountLabel ?: return@DestinationEditDialog
                val editing = editingDestination
                val savedName = if (
                    shouldAutoNameDestination(name, path, destinationDraftNameTouched)
                ) {
                    folderNameFromPath(path)
                } else {
                    name
                }
                if (editing == null) {
                    viewModel.addDestination(savedName, path, accountId, accountLabel)
                } else {
                    viewModel.updateDestination(
                        editing.copy(
                            displayName = savedName,
                            folderPath = path,
                            driveAccountId = accountId,
                            driveAccountLabel = accountLabel,
                            isEnabled = true
                        )
                    )
                }
                showDestinationDialog = false
            }
        )
    }

    if (showManageAccountsDialog) {
        ManageAccountsDialog(
            accounts = accounts,
            onDismiss = { showManageAccountsDialog = false },
            onRename = { account, alias ->
                viewModel.renameAccount(account.id, alias) { }
            },
            onRemove = { account ->
                viewModel.removeAccount(account.id) { }
            }
        )
    }

    if (showDestinationAccountDialog) {
        OneDriveAccountPickerDialog(
            accounts = accounts,
            onDismiss = {
                showDestinationAccountDialog = false
                selectAccountThenOpenFolder = false
            },
            onSelect = { account ->
                showDestinationAccountDialog = false
                destinationDraftAccountId = account.id
                destinationDraftAccountLabel = account.label
                if (selectAccountThenOpenFolder) {
                    selectAccountThenOpenFolder = false
                    openFolderPickerForAccount(account)
                }
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
                    if (
                        shouldAutoNameDestination(
                            destinationDraftName,
                            selectedPath,
                            destinationDraftNameTouched
                        )
                    ) {
                        destinationDraftName = folderNameFromPath(selectedPath)
                        destinationDraftNameTouched = false
                    }
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

    val destinationToDelete = destinationPendingDelete
    if (destinationToDelete != null) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.confirm_delete_destination_title),
            message = stringResource(
                R.string.confirm_delete_destination_message,
                destinationToDelete.displayName
            ),
            confirmText = stringResource(R.string.delete),
            onDismiss = { destinationPendingDelete = null },
            onConfirm = {
                viewModel.deleteDestination(destinationToDelete.id)
                destinationPendingDelete = null
            }
        )
    }

    val accountToAlias = accountAliasTarget
    if (accountToAlias != null) {
        AccountAliasDialog(
            account = accountToAlias,
            alias = accountAliasDraft,
            onAliasChange = { accountAliasDraft = it },
            onDismiss = { accountAliasTarget = null },
            onSave = {
                viewModel.renameAccount(accountToAlias.id, accountAliasDraft) { }
                accountAliasTarget = null
            }
        )
    }

    val accountToRemove = accountPendingRemove
    if (accountToRemove != null) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.confirm_remove_account_title),
            message = stringResource(
                R.string.confirm_remove_account_message,
                accountToRemove.label
            ),
            confirmText = stringResource(R.string.remove),
            onDismiss = { accountPendingRemove = null },
            onConfirm = {
                viewModel.removeAccount(accountToRemove.id) { }
                accountPendingRemove = null
            }
        )
    }
}

@Composable
private fun AccountStatusRow(
    account: OneDriveAccount,
    onAlias: () -> Unit,
    onRemove: () -> Unit
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
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFF48BB78))
                    )
                    Text(
                        text = stringResource(R.string.connected),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = account.label,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (account.username != account.label) {
                    Text(
                        text = account.username,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
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
                TextButton(onClick = onAlias) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.edit))
                }
                TextButton(
                    onClick = onRemove,
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
                        if (destination.driveAccountId.isNullOrBlank()) {
                            stringResource(R.string.no_onedrive_account_selected)
                        } else {
                            destination.driveAccountLabel ?: UploadDestination.CURRENT_ACCOUNT_LABEL
                        }
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
    name: String,
    path: String,
    accountLabel: String?,
    onNameChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onBrowse: () -> Unit,
    onChooseAccount: () -> Unit,
    onSave: (String, String) -> Unit
) {
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
                    onValueChange = onNameChange,
                    label = { Text(stringResource(R.string.destination_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = onChooseAccount,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.ManageAccounts, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        accountLabel?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.no_onedrive_account_selected),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                OutlinedButton(
                    onClick = onBrowse,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !accountLabel.isNullOrBlank()
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.choose_upload_folder))
                }
                Text(
                    text = path.ifBlank { stringResource(R.string.no_folder_selected) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (path.isBlank()) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Text(
                    text = stringResource(R.string.destination_account_required_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, path) },
                enabled = name.isNotBlank() && path.isNotBlank() && !accountLabel.isNullOrBlank()
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
private fun ManageAccountsDialog(
    accounts: List<OneDriveAccount>,
    onDismiss: () -> Unit,
    onRename: (OneDriveAccount, String) -> Unit,
    onRemove: (OneDriveAccount) -> Unit
) {
    var renamingAccount by remember { mutableStateOf<OneDriveAccount?>(null) }
    var accountPendingRemove by remember { mutableStateOf<OneDriveAccount?>(null) }
    var aliasDraft by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.manage_accounts)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (accounts.isEmpty()) {
                    Text(
                        text = stringResource(R.string.no_onedrive_accounts),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    accounts.forEach { account ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.AccountCircle, contentDescription = null)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = account.label,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (account.username != account.label) {
                                    Text(
                                        text = account.username,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            TextButton(
                                onClick = {
                                    renamingAccount = account
                                    aliasDraft = account.label
                                }
                            ) {
                                Text(stringResource(R.string.alias))
                            }
                            TextButton(
                                onClick = { accountPendingRemove = account },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text(stringResource(R.string.remove))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.done))
            }
        }
    )

    val accountToRename = renamingAccount
    if (accountToRename != null) {
        AlertDialog(
            onDismissRequest = { renamingAccount = null },
            title = { Text(stringResource(R.string.account_alias_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = accountToRename.username,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = aliasDraft,
                        onValueChange = { aliasDraft = it },
                        label = { Text(stringResource(R.string.account_alias_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRename(accountToRename, aliasDraft)
                        renamingAccount = null
                    }
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { renamingAccount = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    val accountToRemove = accountPendingRemove
    if (accountToRemove != null) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.confirm_remove_account_title),
            message = stringResource(
                R.string.confirm_remove_account_message,
                accountToRemove.label
            ),
            confirmText = stringResource(R.string.remove),
            onDismiss = { accountPendingRemove = null },
            onConfirm = {
                onRemove(accountToRemove)
                accountPendingRemove = null
            }
        )
    }
}

@Composable
private fun AccountAliasDialog(
    account: OneDriveAccount,
    alias: String,
    onAliasChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.account_alias_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = account.username,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = alias,
                    onValueChange = onAliasChange,
                    label = { Text(stringResource(R.string.account_alias_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = onSave) {
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
private fun ConfirmDeleteDialog(
    title: String,
    message: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Text(confirmText)
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
private fun OneDriveAccountPickerDialog(
    accounts: List<OneDriveAccount>,
    onDismiss: () -> Unit,
    onSelect: (OneDriveAccount) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.choose_onedrive_account)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (accounts.isEmpty()) {
                    Text(
                        text = stringResource(R.string.no_onedrive_accounts),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    accounts.forEach { account ->
                        ListItem(
                            headlineContent = {
                                Text(
                                    account.label,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            leadingContent = {
                                Icon(Icons.Default.AccountCircle, contentDescription = null)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(account) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {},
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

private fun folderNameFromPath(path: String): String {
    return path.trim()
        .trim('/')
        .substringAfterLast('/')
        .ifBlank { UploadDestination.DEFAULT_NAME }
}

private fun isDefaultPlaceholderDestination(destination: UploadDestination): Boolean {
    return destination.id == UploadDestination.DEFAULT_ID ||
        destination.displayName == UploadDestination.DEFAULT_NAME
}

private fun shouldAutoNameDestination(name: String, path: String, nameTouched: Boolean): Boolean {
    return path.isNotBlank() &&
        (!nameTouched || name.isBlank() || name == UploadDestination.DEFAULT_NAME)
}
