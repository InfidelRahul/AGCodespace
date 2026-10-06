package com.agcodespace.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.agcodespace.antigravity.AntigravityDetector
import com.agcodespace.antigravity.AntigravityState
import com.agcodespace.runtime.ProotRuntime
import com.agcodespace.terminal.TerminalService
import com.agcodespace.terminal.TerminalEvents
import com.agcodespace.web.AntigravityWebView
import com.termux.view.TerminalView

@Composable fun HomeScreen(nav: NavController) {
    val context = LocalContext.current
    var installed by remember { mutableStateOf(ProotRuntime(context).isInstalled()) }
    LaunchedEffect(Unit) { installed = withContext(Dispatchers.IO) { ProotRuntime(context).prepare() } }
    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("AGCodespace", style=MaterialTheme.typography.headlineLarge); Text("Android-native Linux + Codespaces + Antigravity") }
        item { StatusCard("Linux userspace", if (installed) "Installed" else "Setup required") }
        item { StatusCard("Codespaces", "Use GitHub CLI from the terminal") }
        item { StatusCard("Antigravity", "Interactive Remote Control") }
        item { Button(onClick={ nav.navigate("terminal") }, Modifier.fillMaxWidth()) { Icon(Icons.Default.Terminal, null); Spacer(Modifier.width(8.dp)); Text("Open Linux Terminal") } }
    }
}
@Composable private fun StatusCard(title:String, status:String) { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Text(title, style=MaterialTheme.typography.titleMedium); Text(status) } } }

private fun openBrowser(context: Context, url: String) {
    CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url))
}

@Composable fun TerminalScreen() {
    val context = LocalContext.current
    var serviceBound by remember { mutableStateOf(false) }
    var binder by remember { mutableStateOf<TerminalService.LocalBinder?>(null) }
    val connection = remember {
        object : android.content.ServiceConnection {
            override fun onServiceConnected(name: android.content.ComponentName?, service: android.os.IBinder?) { binder = service as TerminalService.LocalBinder; serviceBound = true }
            override fun onServiceDisconnected(name: android.content.ComponentName?) { binder = null; serviceBound = false }
        }
    }
    DisposableEffect(Unit) {
        context.startForegroundService(Intent(context, TerminalService::class.java))
        context.bindService(Intent(context, TerminalService::class.java), connection, Context.BIND_AUTO_CREATE)
        onDispose { if (serviceBound) context.unbindService(connection) }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick={ binder?.session()?.write("gh auth login --web\n") }) { Text("GitHub Login") }
            FilledTonalButton(onClick={ binder?.session()?.write("gh codespace list\n") }) { Text("List Codespaces") }
            FilledTonalButton(onClick={ binder?.session()?.write("agy --remote-control --dangerously-skip-permissions\n") }) { Text("Start Antigravity") }
        }
        if (serviceBound && binder?.session() != null) {
            AndroidView(factory={ TerminalView(context, null).apply { setTerminalViewClient(com.agcodespace.terminal.AgTerminalViewClient()); setTextSize(14); attachSession(binder!!.session()) } }, modifier=Modifier.fillMaxSize())
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment=androidx.compose.ui.Alignment.Center) { Text("Starting Linux session…") }
        }
    }
}

@Composable fun AntigravityScreen() {
    val context = LocalContext.current
    val url = AntigravityState.remoteUrl
    val authUrl = AntigravityState.authUrl
    Column(Modifier.fillMaxSize()) {
        if (authUrl.isNotBlank()) {
            AssistChip(onClick={ openBrowser(context, authUrl) }, label={ Text("Open detected authentication URL") }, modifier=Modifier.padding(12.dp))
        }
        if (url.isBlank()) {
            Column(Modifier.padding(20.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("Waiting for Antigravity Remote Control", style=MaterialTheme.typography.titleLarge)
                Text("Use the Terminal button to start: agy --remote-control --dangerously-skip-permissions. Auth prompts remain interactive in the real Linux PTY.")
                OutlinedTextField(
                    value = url, onValueChange = { AntigravityState.remoteUrl = it },
                    modifier=Modifier.fillMaxWidth(), singleLine=true, label={Text("Remote Control URL")}
                )
            }
        } else {
            AndroidView(factory={ AntigravityWebView(context){ openBrowser(context,it) } }, modifier=Modifier.fillMaxSize(), update={ it.open(url) })
        }
    }
}

@Composable fun SettingsScreen() {
    val context = LocalContext.current
    val runtime = remember { ProotRuntime(context) }
    val installed = remember { runtime.isInstalled() }
    Column(Modifier.padding(20.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("Settings", style=MaterialTheme.typography.headlineSmall)
        Text("AGCodespace 1.0.0")
        Text("PRoot source: LinuxDroidapp/proot (pinned at release packaging time). LinuxDroid is used as the Android userspace reference.")
        Text("Install the packaged Linux runtime before using the terminal. The app never needs root privileges.")
        Text(if (installed) "Linux runtime: ready" else "Linux runtime: not packaged for this ABI")
    }
}
