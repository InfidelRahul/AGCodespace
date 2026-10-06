package com.agcodespace.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.agcodespace.antigravity.AntigravityState
import com.agcodespace.runtime.ProotRuntime
import com.agcodespace.runtime.RuntimeStatus
import com.agcodespace.terminal.AgTerminalViewClient
import com.agcodespace.terminal.TerminalService
import com.agcodespace.web.AntigravityWebView
import com.termux.view.TerminalView

@Composable
fun HomeScreen(nav: NavController) {
    val context = LocalContext.current
    val runtime = remember { ProotRuntime(context) }
    val status by ProotRuntime.status.collectAsState()
    val isInstalled = runtime.isInstalled()

    LaunchedEffect(Unit) {
        if (!isInstalled) {
            ProotRuntime.autoInstallIfNeeded(context.applicationContext)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("AGCodespace", style = MaterialTheme.typography.headlineLarge)
            Text(
                "Real Linux userspace (Ubuntu 26.04) + GitHub Codespaces + Antigravity",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            RuntimeStatusCard(
                status = status,
                isInstalled = isInstalled,
                onRetry = { ProotRuntime.autoInstallIfNeeded(context.applicationContext) },
                onOpenTerminal = { nav.navigate("terminal") }
            )
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("GitHub Codespaces", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Manage and connect to your remote cloud environments directly using the GitHub CLI from the real Linux terminal.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Antigravity Integration", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Launch interactive remote control workflows with full PTY session support and auto-detected authentication.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        item {
            Button(
                onClick = { nav.navigate("terminal") },
                modifier = Modifier.fillMaxWidth(),
                enabled = isInstalled || status is RuntimeStatus.Ready
            ) {
                Icon(Icons.Default.Terminal, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (isInstalled || status is RuntimeStatus.Ready) "Open Linux Terminal" else "Installing Linux Runtime…")
            }
        }
    }
}

@Composable
private fun RuntimeStatusCard(
    status: RuntimeStatus,
    isInstalled: Boolean,
    onRetry: () -> Unit,
    onOpenTerminal: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isInstalled || status is RuntimeStatus.Ready) {
                MaterialTheme.colorScheme.primaryContainer
            } else if (status is RuntimeStatus.Error) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when {
                isInstalled || status is RuntimeStatus.Ready -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Linux Userspace: Ready",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Text(
                        "Real Ubuntu 26.04 guest environment is fully installed and configured via PRoot.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                status is RuntimeStatus.Downloading -> {
                    val dl = status
                    val mbDownloaded = dl.bytesDownloaded / (1024f * 1024f)
                    val mbTotal = dl.totalBytes / (1024f * 1024f)
                    val safeProgress = if (dl.progress.isNaN() || dl.progress.isInfinite()) 0f else dl.progress.coerceIn(0f, 1f)
                    val percent = (safeProgress * 100).toInt()

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Downloading Ubuntu Linux Rootfs…", style = MaterialTheme.typography.titleMedium)
                    }
                    LinearProgressIndicator(
                        progress = { safeProgress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "%.1f MB / %.1f MB (%d%%)".format(mbDownloaded, mbTotal, percent),
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                status is RuntimeStatus.Extracting -> {
                    val ext = status
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Extracting Linux Rootfs…", style = MaterialTheme.typography.titleMedium)
                    }
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        "Unpacking filesystem (${ext.count} files processed)…",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                status is RuntimeStatus.Configuring -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Configuring Guest Environment…", style = MaterialTheme.typography.titleMedium)
                    }
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        "Setting up users, networking, bash shell, and launcher scripts…",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                status is RuntimeStatus.Checking -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(status.message, style = MaterialTheme.typography.titleMedium)
                    }
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                status is RuntimeStatus.Error -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Installation Error",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    Text(
                        status.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Button(onClick = onRetry, colors = ButtonDefaults.buttonColors()) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Retry Download & Install")
                    }
                }

                else -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Initializing Linux Environment…", style = MaterialTheme.typography.titleMedium)
                    }
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

private fun openBrowser(context: Context, url: String) {
    runCatching {
        CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url))
    }.onFailure {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}

@Composable
fun TerminalScreen() {
    val context = LocalContext.current
    val runtime = remember { ProotRuntime(context) }
    val status by ProotRuntime.status.collectAsState()
    val isInstalled = runtime.isInstalled()

    var serviceBound by remember { mutableStateOf(false) }
    var binder by remember { mutableStateOf<TerminalService.LocalBinder?>(null) }
    val terminalClient = remember { AgTerminalViewClient() }

    var ctrlPressed by remember { mutableStateOf(false) }
    var altPressed by remember { mutableStateOf(false) }

    val connection = remember {
        object : android.content.ServiceConnection {
            override fun onServiceConnected(name: android.content.ComponentName?, service: android.os.IBinder?) {
                binder = service as? TerminalService.LocalBinder
                serviceBound = true
            }

            override fun onServiceDisconnected(name: android.content.ComponentName?) {
                binder = null
                serviceBound = false
            }
        }
    }

    DisposableEffect(Unit) {
        val appContext = context.applicationContext
        if (!isInstalled) {
            ProotRuntime.autoInstallIfNeeded(appContext)
        }
        val serviceIntent = Intent(appContext, TerminalService::class.java)
        runCatching {
            appContext.startForegroundService(serviceIntent)
        }.onFailure {
            runCatching { appContext.startService(serviceIntent) }
        }
        runCatching {
            appContext.bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE)
        }
        onDispose {
            if (serviceBound) {
                runCatching { appContext.unbindService(connection) }
                serviceBound = false
            }
        }
    }

    val session = binder?.session()

    Column(Modifier.fillMaxSize()) {
        // Quick Actions Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilledTonalButton(
                onClick = { binder?.session()?.write("gh auth login --web\n") },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text("GitHub Login")
            }
            FilledTonalButton(
                onClick = { binder?.session()?.write("gh codespace list\n") },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text("Codespaces")
            }
            FilledTonalButton(
                onClick = { binder?.session()?.write("agy --remote-control --dangerously-skip-permissions\n") },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text("Start Antigravity")
            }
            FilledTonalButton(
                onClick = { binder?.session()?.write("apt update\n") },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text("apt update")
            }
            FilledTonalButton(
                onClick = { binder?.session()?.write("clear\n") },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text("Clear")
            }
            FilledTonalButton(
                onClick = { binder?.restart() },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text("Restart")
            }
        }

        // Terminal Main Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (session != null) {
                AndroidView(
                    factory = { ctx ->
                        TerminalView(ctx, null).apply {
                            terminalClient.terminalView = this
                            setTerminalViewClient(terminalClient)
                            setTextSize(14)
                            runCatching { attachSession(session) }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    RuntimeStatusCard(
                        status = status,
                        isInstalled = isInstalled,
                        onRetry = {
                            ProotRuntime.autoInstallIfNeeded(context.applicationContext)
                            binder?.restart()
                        },
                        onOpenTerminal = {}
                    )
                }
            }
        }

        // Extra Keys Bar (Termux-style virtual keyboard keys)
        if (session != null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 4.dp,
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AssistChip(
                        onClick = { session.write("\u001b") },
                        label = { Text("ESC") }
                    )
                    AssistChip(
                        onClick = { session.write("\t") },
                        label = { Text("TAB") }
                    )
                    FilterChip(
                        selected = ctrlPressed,
                        onClick = {
                            ctrlPressed = terminalClient.toggleCtrl()
                        },
                        label = { Text("CTRL") }
                    )
                    FilterChip(
                        selected = altPressed,
                        onClick = {
                            altPressed = terminalClient.toggleAlt()
                        },
                        label = { Text("ALT") }
                    )
                    AssistChip(
                        onClick = { session.write("\u001b[A") },
                        label = { Text("↑") }
                    )
                    AssistChip(
                        onClick = { session.write("\u001b[B") },
                        label = { Text("↓") }
                    )
                    AssistChip(
                        onClick = { session.write("\u001b[D") },
                        label = { Text("←") }
                    )
                    AssistChip(
                        onClick = { session.write("\u001b[C") },
                        label = { Text("→") }
                    )
                    AssistChip(
                        onClick = { session.write("\u0003") },
                        label = { Text("Ctrl+C") }
                    )
                    AssistChip(
                        onClick = { session.write("\u0004") },
                        label = { Text("Ctrl+D") }
                    )
                    IconButton(
                        onClick = { terminalClient.showSoftKeyboard() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Keyboard, contentDescription = "Toggle Keyboard")
                    }
                }
            }
        }
    }
}

@Composable
fun AntigravityScreen() {
    val context = LocalContext.current
    val url = AntigravityState.remoteUrl
    val authUrl = AntigravityState.authUrl

    Column(Modifier.fillMaxSize()) {
        if (authUrl.isNotBlank()) {
            AssistChip(
                onClick = { openBrowser(context, authUrl) },
                label = { Text("Open detected authentication URL") },
                modifier = Modifier.padding(12.dp)
            )
        }
        if (url.isBlank()) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Waiting for Antigravity Remote Control", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Start Antigravity from the terminal tab using the button or run:\n\n" +
                        "agy --remote-control --dangerously-skip-permissions\n\n" +
                        "Interactive auth prompts and CLI outputs will be detected automatically."
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { AntigravityState.remoteUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Remote Control URL") }
                )
            }
        } else {
            AndroidView(
                factory = { AntigravityWebView(context) { openBrowser(context, it) } },
                modifier = Modifier.fillMaxSize(),
                update = { it.open(url) }
            )
        }
    }
}

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val runtime = remember { ProotRuntime(context) }
    val isInstalled = runtime.isInstalled()
    val abi = remember { runtime.getAbi() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)
        Text("AGCodespace 1.0.0")
        Text("PRoot source: LinuxDroidapp/proot (pinned at release packaging time). LinuxDroid is used as the Android userspace reference.")
        Text("Architecture: $abi")
        Text(if (isInstalled) "Linux runtime: Ready (Ubuntu 26.04)" else "Linux runtime: Setup required")

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { ProotRuntime.autoInstallIfNeeded(context) }
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (isInstalled) "Reinstall Linux Rootfs" else "Install Linux Rootfs")
        }
    }
}
