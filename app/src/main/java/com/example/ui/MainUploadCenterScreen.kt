package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.auth.AuthState
import com.example.data.model.UploadJobEntity
import com.example.data.model.UploadStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MainUploadCenterScreen(
    viewModel: MainViewModel,
    onNavigateToSettings: () -> Unit,
    onPickFiles: () -> Unit
) {
    val uploadTasks by viewModel.uploadTasks.collectAsState()
    val authState by viewModel.authState.collectAsState()
    val settings by viewModel.appSettings.collectAsState()
    val folderPickerState by viewModel.folderPickerState.collectAsState()
    val completedCount = uploadTasks.count {
        it.status == UploadStatus.SUCCESS || it.status == UploadStatus.CANCELED
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_launcher_full),
                            contentDescription = null,
                            modifier = Modifier
                                .size(52.dp)
                                .padding(end = 12.dp)
                        )
                        Text(
                            text = stringResource(R.string.main_title),
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.testTag("settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.settings_title)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.primary
                )
            )
        },
        bottomBar = {
            val emailText = when (val auth = authState) {
                is AuthState.SignedIn -> auth.account.username
                else -> stringResource(R.string.not_logged_in)
            }
            val statusColor = when (authState) {
                is AuthState.SignedIn -> Color(0xFF48BB78) // Green
                is AuthState.SignedOut -> Color(0xFFE53E3E) // Red
                else -> Color(0xFFDD6B20) // Orange
            }
            val statusText = when (authState) {
                is AuthState.SignedIn -> stringResource(R.string.onedrive_connected)
                is AuthState.SignedOut -> stringResource(R.string.disconnected)
                else -> stringResource(R.string.initializing)
            }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                color = MaterialTheme.colorScheme.surfaceVariant, // #F3EDF7
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline) // #CAC4D0
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = emailText,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // High-priority login warnings
            AnimatedVisibility(visible = authState is AuthState.SignedOut) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF2B8B5)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ReportProblem,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.signin_required),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                stringResource(R.string.signin_required_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        TextButton(
                            onClick = onNavigateToSettings,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        ) {
                            Text(stringResource(R.string.sign_in_short), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Dual Sections list
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.current_upload_folder),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = settings.defaultFolder,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            FilledTonalIconButton(
                                onClick = { viewModel.openFolderPicker() },
                                enabled = authState is AuthState.SignedIn,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = stringResource(R.string.choose_upload_folder)
                                )
                            }
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.upload_queue),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (completedCount > 0) {
                                TextButton(
                                    onClick = { viewModel.clearHistory() },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(stringResource(R.string.clear_done), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            FilledTonalButton(
                                onClick = onPickFiles,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.upload), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            if (uploadTasks.isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = if (uploadTasks.size == 1) {
                                            stringResource(R.string.item_count_one, uploadTasks.size)
                                        } else {
                                            stringResource(R.string.item_count_many, uploadTasks.size)
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }
                }

                if (uploadTasks.isEmpty()) {
                    item {
                        EmptyStatePlaceholder(
                            title = stringResource(R.string.no_upload_tasks),
                            description = stringResource(R.string.no_upload_tasks_desc),
                            icon = Icons.Default.DriveFolderUpload
                        )
                    }
                } else {
                    items(uploadTasks, key = { it.id }) { job ->
                        UploadTaskCard(
                            job = job,
                            onRetry = { viewModel.retryJob(job) },
                            onDelete = { viewModel.deleteJob(job) },
                            onCancel = { viewModel.cancelJob(job) },
                            modifier = Modifier.animateItemPlacement()
                        )
                    }
                }
            }
        }
    }

    if (folderPickerState.isOpen) {
        OneDriveFolderPickerDialog(
            state = folderPickerState,
            manualPath = settings.defaultFolder,
            onDismiss = { viewModel.closeFolderPicker() },
            onOpenFolder = { folder ->
                viewModel.loadFolderChildren(folder.id, folder.path)
            },
            onUseCurrentFolder = {
                viewModel.selectFolderFromPicker(folderPickerState.currentPath)
            },
            onUseManualFolder = { path ->
                viewModel.selectFolderFromPicker(path)
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
fun UploadTaskCard(
    job: UploadJobEntity,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isFinished = job.status == UploadStatus.SUCCESS || job.status == UploadStatus.CANCELED
    val isFailed = job.status == UploadStatus.FAILED
    val savedAsName = job.uploadedFileName?.takeIf {
        it.isNotBlank() && it != job.sanitizedFileName
    }
    val cardColor = when (job.status) {
        UploadStatus.FAILED -> Color(0xFFFFF1F1)
        UploadStatus.SUCCESS -> Color(0xFFEFF8F0)
        UploadStatus.CANCELED -> Color(0xFFF3F4F6)
        else -> MaterialTheme.colorScheme.surface
    }
    val borderColor = when (job.status) {
        UploadStatus.FAILED -> Color(0xFFF2B8B5)
        UploadStatus.SUCCESS -> Color(0xFFB7DDBB)
        else -> MaterialTheme.colorScheme.outline
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("upload_task_card_${job.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = job.originalFileName,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(R.string.folder_label, job.targetFolder),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = when {
                            isFailed -> stringResource(
                                R.string.error_label,
                                job.errorMessage ?: stringResource(R.string.upload_failed_default)
                            )
                            job.status == UploadStatus.SUCCESS -> stringResource(
                                R.string.completed_at,
                                formatDateTime(job.completedAt)
                            )
                            job.status == UploadStatus.CANCELED -> job.errorMessage ?: stringResource(R.string.canceled)
                            !job.errorMessage.isNullOrBlank() -> job.errorMessage
                            else -> statusLabel(job.status)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isFailed) Color(0xFFB3261E) else MaterialTheme.colorScheme.primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (job.status == UploadStatus.SUCCESS && savedAsName != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFB26A00),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = stringResource(R.string.renamed_label, savedAsName),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF8A5200),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    StatusBadge(status = job.status)
                    Spacer(modifier = Modifier.height(4.dp))
                    when {
                        job.status == UploadStatus.QUEUED || job.status == UploadStatus.UPLOADING || job.status == UploadStatus.COPYING -> {
                            TextButton(
                                onClick = onCancel,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.cancel), fontSize = 12.sp)
                            }
                        }
                        isFailed && job.localCachePath.isNotBlank() -> {
                            Button(
                                onClick = onRetry,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFB3261E),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text(stringResource(R.string.retry), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        isFinished || isFailed -> {
                            TextButton(
                                onClick = onDelete,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.clear), fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            if (!isFinished && !isFailed) {
                val progressFloat = job.progress / 100f
                LinearProgressIndicator(
                    progress = { progressFloat },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .testTag("progress_bar_${job.id}"),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color(0xFFE6E1E5)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatTaskSizeText(job),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (isFinished || isFailed) statusLabel(job.status) else "${job.progress}%",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun HistoryJobRow(
    job: UploadJobEntity,
    modifier: Modifier = Modifier
) {
    val isSuccess = job.status == UploadStatus.SUCCESS
    val circleBg = if (isSuccess) Color(0xFFC8E6C9) else Color(0xFFFFCDD2)
    val circleText = if (isSuccess) "✓" else "✕"
    val circleColor = if (isSuccess) Color(0xFF2E7D32) else Color(0xFFC62828)
    
    val formattedSize = formatBytes(job.fileSize).let { "${it.first} ${it.second}" }
    val metaText = if (isSuccess) {
        "${stringResource(R.string.completed)} • ${formatDateTime(job.completedAt)} • $formattedSize"
    } else {
        "${job.errorMessage ?: stringResource(R.string.failed)} • ${formatDateTime(job.completedAt)}"
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("history_job_card_${job.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(circleBg),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = circleText,
                    color = circleColor,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = job.originalFileName,
                    fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = metaText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSuccess) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFFC62828),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun StatusBadge(status: UploadStatus) {
    val colorPair = when (status) {
        UploadStatus.COPYING -> Pair(Color(0xFFE0F7FA), Color(0xFF006064))
        UploadStatus.QUEUED -> Pair(Color(0xFFFFF3E0), Color(0xFFE65100))
        UploadStatus.UPLOADING -> Pair(Color(0xFFE8F5E9), Color(0xFF1B5E20))
        UploadStatus.SUCCESS -> Pair(Color(0xFFE8F5E9), Color(0xFF2E7D32))
        UploadStatus.FAILED -> Pair(Color(0xFFFFEBEE), Color(0xFFC62828))
        UploadStatus.CANCELED -> Pair(Color(0xFFECEFF1), Color(0xFF37474F))
    }

    Box(
        modifier = Modifier
            .background(colorPair.first, CircleShape)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = statusLabel(status),
            color = colorPair.second,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun EmptyStatePlaceholder(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                modifier = Modifier.size(40.dp)
            )
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

// Helpers
fun formatMemorySize(uploadedBytes: Long, fileSize: Long): String {
    if (fileSize <= 0) return "Calculating size..."
    val (up, upUnit) = formatBytes(uploadedBytes)
    val (tot, totUnit) = formatBytes(fileSize)
    return "$up $upUnit / $tot $totUnit"
}

@Composable
fun formatTaskSizeText(job: UploadJobEntity): String {
    val (total, totalUnit) = formatBytes(job.fileSize)
    val totalText = "$total $totalUnit"
    val isDone = job.status == UploadStatus.SUCCESS ||
        job.status == UploadStatus.CANCELED ||
        job.status == UploadStatus.FAILED

    if (isDone) {
        return stringResource(R.string.size_label, totalText)
    }

    if (job.errorMessage?.contains("Finalizing", ignoreCase = true) == true) {
        return stringResource(R.string.sent_finalizing, totalText)
    }

    val (uploaded, uploadedUnit) = formatBytes(job.uploadedBytes)
    return stringResource(R.string.uploaded_of, "$uploaded $uploadedUnit", totalText)
}

fun formatBytes(bytes: Long): Pair<String, String> {
    if (bytes < 1024) return Pair("$bytes", "B")
    val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
    val unit = "KMGTPE"[exp - 1] + "B"
    val valForm = String.format(Locale.US, "%.1f", bytes / Math.pow(1024.0, exp.toDouble()))
    return Pair(valForm, unit)
}

fun formatDateTime(timestamp: Long?): String {
    if (timestamp == null) return ""
    val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    return df.format(Date(timestamp))
}

@Composable
fun statusLabel(status: UploadStatus): String {
    return when (status) {
        UploadStatus.COPYING -> stringResource(R.string.copying)
        UploadStatus.QUEUED -> stringResource(R.string.queued)
        UploadStatus.UPLOADING -> stringResource(R.string.uploading)
        UploadStatus.SUCCESS -> stringResource(R.string.completed)
        UploadStatus.FAILED -> stringResource(R.string.failed)
        UploadStatus.CANCELED -> stringResource(R.string.canceled)
    }
}
