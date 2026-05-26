package com.example.ui

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    onNavigateToSettings: () -> Unit
) {
    val activeQueue by viewModel.activeQueue.collectAsState()
    val history by viewModel.history.collectAsState()
    val authState by viewModel.authState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "OneDrive Share Uploader",
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
                            contentDescription = "Preferences and Settings"
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
                else -> "Not logged in"
            }
            val statusColor = when (authState) {
                is AuthState.SignedIn -> Color(0xFF48BB78) // Green
                is AuthState.SignedOut -> Color(0xFFE53E3E) // Red
                else -> Color(0xFFDD6B20) // Orange
            }
            val statusText = when (authState) {
                is AuthState.SignedIn -> "OneDrive Connected"
                is AuthState.SignedOut -> "Disconnected"
                else -> "Initializing..."
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
                            contentDescription = "Not signed in warning",
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Microsoft Account Sign-In Required",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                "Sharing uploads will stay paused until account log-in is complete.",
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
                            Text("SIGN IN", fontWeight = FontWeight.Bold)
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
                // SECTION A: Currently Active Queue Title
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Active Upload Queue",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (activeQueue.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "${activeQueue.size} " + if (activeQueue.size == 1) "Item" else "Items",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }

                if (activeQueue.isEmpty()) {
                    item {
                        EmptyStatePlaceholder(
                            title = "No Active Uploads",
                            description = "Files shared to this app will appear here while uploading.",
                            icon = Icons.Default.DriveFolderUpload
                        )
                    }
                } else {
                    items(activeQueue, key = { it.id }) { job ->
                        ActiveJobCard(
                            job = job,
                            onRetry = { viewModel.retryJob(job) },
                            onDelete = { viewModel.deleteJob(job) },
                            modifier = Modifier.animateItemPlacement()
                        )
                    }
                }

                // SECTION B: Completed History Log Title
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Upload History Record",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        if (history.isNotEmpty()) {
                            TextButton(
                                onClick = { viewModel.clearHistory() },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text("Clear Past Logs", fontSize = 12.sp)
                            }
                        }
                    }
                }

                if (history.isEmpty()) {
                    item {
                        EmptyStatePlaceholder(
                            title = "No Completed Uploads Yet",
                            description = "Your successful and canceled file transfer history records will accumulate here.",
                            icon = Icons.Default.History
                        )
                    }
                } else {
                    items(history, key = { it.id }) { historyJob ->
                        HistoryJobRow(
                            job = historyJob,
                            modifier = Modifier.animateItemPlacement()
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ActiveJobCard(
    job: UploadJobEntity,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (job.status == UploadStatus.FAILED) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .testTag("active_job_card_${job.id}"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFFFFF1F1) // PolishErrorContainer
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF2B8B5)) // PolishErrorBorder
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
                            fontWeight = FontWeight.Medium,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF1D1B1E)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Error: ${job.errorMessage ?: "Upload operation failed."}",
                            color = Color(0xFFB3261E),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Normal,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    
                    Column(horizontalAlignment = Alignment.End) {
                        Button(
                            onClick = onRetry,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFB3261E),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .height(36.dp)
                                .testTag("retry_button_${job.id}")
                        ) {
                            Text("RETRY", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        
                        Spacer(modifier = Modifier.height(6.dp))
                        
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("delete_fail_button_${job.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Job",
                                tint = Color(0xFFB3261E).copy(alpha = 0.7f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    } else {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .testTag("active_job_card_${job.id}"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Header Name & Action Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = job.originalFileName,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "To: ${job.targetFolder}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    StatusBadge(status = job.status)
                }

                // Normal progress bars
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatMemorySize(job.uploadedBytes, job.fileSize),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${job.progress}%",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
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
        "Completed • ${formatDateTime(job.completedAt)} • $formattedSize"
    } else {
        "${job.errorMessage ?: "Cancelled/Failed"} • ${formatDateTime(job.completedAt)}"
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
            text = status.name,
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
