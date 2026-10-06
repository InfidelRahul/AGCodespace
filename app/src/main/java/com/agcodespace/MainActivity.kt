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
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.*
import com.agcodespace.screens.*
import com.agcodespace.ui.AppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { AppTheme { AGCodespaceApp() } } }
}
@Composable private fun AGCodespaceApp() {
    val nav=rememberNavController()
    Scaffold(bottomBar={ NavigationBar { NavigationBarItem(nav.currentDestination?.route=="home",{nav.navigate("home")},icon={Icon(Icons.Default.Code,null)},label={Text("Home")}); NavigationBarItem(nav.currentDestination?.route=="terminal",{nav.navigate("terminal")},icon={Icon(Icons.Default.Terminal,null)},label={Text("Terminal")}); NavigationBarItem(nav.currentDestination?.route=="agy",{nav.navigate("agy")},icon={Icon(Icons.Default.Code,null)},label={Text("Antigravity")}); NavigationBarItem(nav.currentDestination?.route=="settings",{nav.navigate("settings")},icon={Icon(Icons.Default.Settings,null)},label={Text("Settings")}) } }) { padding ->
        NavHost(nav,"home",Modifier.padding(padding)) { composable("home"){HomeScreen(nav)}; composable("terminal"){TerminalScreen()}; composable("agy"){AntigravityScreen()}; composable("settings"){SettingsScreen()} }
    }
}
