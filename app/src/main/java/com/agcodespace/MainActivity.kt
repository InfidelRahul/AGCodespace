package com.agcodespace

import android.os.Bundle
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Automatically start downloading and installing rootfs as soon as app opens
        ProotRuntime.autoInstallIfNeeded(this)
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
                    onClick = { nav.navigate("home") },
                    icon = { Icon(Icons.Default.Code, contentDescription = null) },
                    label = { Text("Home") }
                )
                NavigationBarItem(
                    selected = nav.currentDestination?.route == "terminal",
                    onClick = { nav.navigate("terminal") },
                    icon = { Icon(Icons.Default.Terminal, contentDescription = null) },
                    label = { Text("Terminal") }
                )
                NavigationBarItem(
                    selected = nav.currentDestination?.route == "agy",
                    onClick = { nav.navigate("agy") },
                    icon = { Icon(Icons.Default.Code, contentDescription = null) },
                    label = { Text("Antigravity") }
                )
                NavigationBarItem(
                    selected = nav.currentDestination?.route == "settings",
                    onClick = { nav.navigate("settings") },
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
