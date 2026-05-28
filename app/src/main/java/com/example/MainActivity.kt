package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.AboutScreen
import com.example.ui.MainUploadCenterScreen
import com.example.ui.MainViewModel
import com.example.ui.SettingsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.util.LocaleHelper
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Asynchronously check and query POST_NOTIFICATIONS permission (Android 13+)
        requestNotificationPermission()

        val viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        var pendingPickedUris by mutableStateOf<List<Uri>>(emptyList())

        fun queuePickedFiles(uris: List<Uri>, destinationId: String?) {
            viewModel.addSelectedFiles(uris, destinationId) { result ->
                val textContext = LocaleHelper.localizedContext(
                    this,
                    viewModel.appSettings.value.languageCode
                )
                when {
                    result.addedCount > 0 -> {
                        Toast.makeText(
                            this,
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
                            this,
                            textContext.getString(R.string.toast_no_files_added),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }

        val openDocumentsLauncher = registerForActivityResult(
            ActivityResultContracts.OpenMultipleDocuments()
        ) { uris ->
            if (uris.isEmpty()) return@registerForActivityResult
            lifecycleScope.launch {
                val enabledDestinations = viewModel.enabledDestinationsOnce()
                val textContext = LocaleHelper.localizedContext(
                    this@MainActivity,
                    viewModel.appSettings.value.languageCode
                )
                when {
                    enabledDestinations.isEmpty() -> {
                        Toast.makeText(
                            this@MainActivity,
                            textContext.getString(R.string.toast_no_upload_destinations),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    enabledDestinations.size == 1 -> {
                        queuePickedFiles(uris, enabledDestinations.first().id)
                    }
                    else -> {
                        pendingPickedUris = uris
                    }
                }
            }
        }

        // Read intent extras for smart redirecting
        val shouldNavigateToSettings = intent.getBooleanExtra("NAVIGATE_TO_SETTINGS", false)

        setContent {
            val settings by viewModel.appSettings.collectAsState()
            val baseContext = LocalContext.current
            val localizedContext = remember(settings.languageCode) {
                LocaleHelper.localizedContext(baseContext, settings.languageCode)
            }
            LaunchedEffect(settings.uploadDestinations) {
                viewModel.clearShareShortcuts()
            }

            CompositionLocalProvider(LocalContext provides localizedContext) {
                MyApplicationTheme {
                    val navController = rememberNavController()
                    val startDestination = if (shouldNavigateToSettings) "settings" else "upload_center"
                    val pendingUris = pendingPickedUris

                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        NavHost(
                            navController = navController,
                            startDestination = startDestination,
                            modifier = Modifier.padding(innerPadding)
                        ) {
                            composable("upload_center") {
                                MainUploadCenterScreen(
                                    viewModel = viewModel,
                                    onNavigateToSettings = {
                                        navController.navigate("settings")
                                    },
                                    onPickFiles = {
                                        openDocumentsLauncher.launch(arrayOf("*/*"))
                                    }
                                )
                            }
                            composable("settings") {
                                SettingsScreen(
                                    viewModel = viewModel,
                                    onNavigateBack = {
                                        if (navController.previousBackStackEntry != null) {
                                            navController.navigateUp()
                                        } else {
                                            navController.navigate("upload_center") {
                                                popUpTo("settings") { inclusive = true }
                                            }
                                        }
                                    },
                                    onNavigateToAbout = {
                                        navController.navigate("about")
                                    }
                                )
                            }
                            composable("about") {
                                AboutScreen(
                                    onNavigateBack = {
                                        navController.navigateUp()
                                    }
                                )
                            }
                        }
                    }

                    if (pendingUris.isNotEmpty()) {
                        Dialog(onDismissRequest = { pendingPickedUris = emptyList() }) {
                            androidx.compose.material3.Surface(
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                                tonalElevation = 6.dp
                            ) {
                                DestinationPickerContent(
                                    destinations = settings.enabledDestinations,
                                    onDestinationSelected = { destination ->
                                        pendingPickedUris = emptyList()
                                        queuePickedFiles(pendingUris, destination.id)
                                    },
                                    onCancel = { pendingPickedUris = emptyList() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
    }
}
