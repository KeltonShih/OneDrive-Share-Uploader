package com.example.ui

import android.app.Activity
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.auth.AuthState
import com.example.data.model.ConflictBehavior

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Upload Center"
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
            // Section 1: Microsoft Authentication Status
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
                            contentDescription = "OneDrive Cloud",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "OneDrive Login Status",
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
                                "Not signed in to Microsoft account.",
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
                                    Text("Sign In with Microsoft")
                                }
                            }
                        }
                        is AuthState.SignedIn -> {
                            Text(
                                "Signed in as:",
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
                                    Text("Sign Out")
                                }
                            }
                        }
                        is AuthState.Error -> {
                            Text(
                                text = "Authentication Error:\n${auth.exception.message}",
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
                                Text("Try Sign In Again")
                            }
                        }
                    }
                }
            }

            // Section 2: Conflict Behavior
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
                            contentDescription = "Conflict behavior option",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Conflict Resolution Behaviour",
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
                                        ConflictBehavior.RENAME -> "Rename Automatically (RENAME)"
                                        ConflictBehavior.REPLACE -> "Overwrite/Replace existing (REPLACE)"
                                        ConflictBehavior.FAIL -> "Mark task as Failed (FAIL)"
                                    },
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = when (behavior) {
                                        ConflictBehavior.RENAME -> "Renames target to prevent overwrites (default)"
                                        ConflictBehavior.REPLACE -> "Overwrites target destination file instantly"
                                        ConflictBehavior.FAIL -> "Fails the job without altering existing files"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Section 3: Network & Smart routing constraints
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
                            contentDescription = "Network Constraints",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Constraints & Advanced Routing",
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
                            Text("Only Upload on Wi-Fi", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Suspends the upload queue if connected to mobile/metered connections",
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
                            Text("Enable Smart Folder Routing", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Auto routes image/*, video/*, PDFs, and Excel sheets to categorized subfolders (Priority 5 placeholder)",
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

            // Section 4: Log clearance and DB actions
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
                        text = "History Clean Actions",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "This removes all past items that successfully finished or were canceled from the local Room tracking database.",
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
                            Text("Clear Completed History Logs")
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
                            text = "About",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    supportingContent = {
                        Text(
                            text = "Developer and version information",
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
}
