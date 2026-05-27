package com.example

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.AboutScreen
import com.example.ui.MainUploadCenterScreen
import com.example.ui.MainViewModel
import com.example.ui.SettingsScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Asynchronously check and query POST_NOTIFICATIONS permission (Android 13+)
        requestNotificationPermission()

        val viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        val openDocumentsLauncher = registerForActivityResult(
            ActivityResultContracts.OpenMultipleDocuments()
        ) { uris ->
            if (uris.isEmpty()) return@registerForActivityResult
            viewModel.addSelectedFiles(uris) { result ->
                when {
                    result.addedCount > 0 -> {
                        Toast.makeText(
                            this,
                            if (result.failedCount > 0) {
                                "Added ${result.addedCount} file(s). ${result.failedCount} file(s) need attention."
                            } else {
                                "Added ${result.addedCount} file(s) to OneDrive queue."
                            },
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    else -> {
                        Toast.makeText(
                            this,
                            "No files could be added.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }

        // Read intent extras for smart redirecting
        val shouldNavigateToSettings = intent.getBooleanExtra("NAVIGATE_TO_SETTINGS", false)

        setContent {
            MyApplicationTheme {
                val navController = rememberNavController()
                val startDestination = if (shouldNavigateToSettings) "settings" else "upload_center"

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
