package com.agcodespace

import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.*
import com.agcodespace.runtime.ProotRuntime
import com.agcodespace.screens.*
import com.agcodespace.ui.AppTheme

class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Protect process from sudden termination due to uncaught background exceptions
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "Uncaught exception on thread: ${thread.name}", throwable)
            previousHandler?.uncaughtException(thread, throwable)
        }

        // Request notification permission for Foreground Service on Android 13+ (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }

        // Automatically start downloading and installing rootfs as soon as app opens
        ProotRuntime.autoInstallIfNeeded(applicationContext)

        setContent {
            AppTheme {
                AGCodespaceApp()
            }
        }
    }
}

@Composable
private fun AGCodespaceApp() {
    val nav = rememberNavController()
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = nav.currentDestination?.route == "home",
                    onClick = {
                        if (nav.currentDestination?.route != "home") {
                            nav.navigate("home") {
                                popUpTo("home") { inclusive = true }
                            }
                        }
                    },
                    icon = { Icon(Icons.Default.Code, contentDescription = null) },
                    label = { Text("Home") }
                )
                NavigationBarItem(
                    selected = nav.currentDestination?.route == "terminal",
                    onClick = {
                        if (nav.currentDestination?.route != "terminal") {
                            nav.navigate("terminal") {
                                launchSingleTop = true
                            }
                        }
                    },
                    icon = { Icon(Icons.Default.Terminal, contentDescription = null) },
                    label = { Text("Terminal") }
                )
                NavigationBarItem(
                    selected = nav.currentDestination?.route == "agy",
                    onClick = {
                        if (nav.currentDestination?.route != "agy") {
                            nav.navigate("agy") {
                                launchSingleTop = true
                            }
                        }
                    },
                    icon = { Icon(Icons.Default.Code, contentDescription = null) },
                    label = { Text("Antigravity") }
                )
                NavigationBarItem(
                    selected = nav.currentDestination?.route == "settings",
                    onClick = {
                        if (nav.currentDestination?.route != "settings") {
                            nav.navigate("settings") {
                                launchSingleTop = true
                            }
                        }
                    },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("Settings") }
                )
            }
        }
    ) { padding ->
        NavHost(navController = nav, startDestination = "home", modifier = Modifier.padding(padding)) {
            composable("home") { HomeScreen(nav) }
            composable("terminal") { TerminalScreen() }
            composable("agy") { AntigravityScreen() }
            composable("settings") { SettingsScreen() }
        }
    }
}
