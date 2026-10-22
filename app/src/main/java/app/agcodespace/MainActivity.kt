package app.agcodespace

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.agcodespace.runtime.LinuxRuntime
import app.agcodespace.runtime.NativePty
import app.agcodespace.runtime.VtScreen
import app.agcodespace.runtime.Utf8StreamDecoder
import app.agcodespace.runtime.TerminalSessionService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

private val Panel = Color(0xFF202633)
private val Accent = Color(0xFF8BA8FF)
private val Muted = Color(0xFF9AA5B5)
private val Canvas = Color(0xFF0D1118)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { WorkspaceApp() }
    }
}

@Composable
private fun WorkspaceApp() {
    val context = LocalContext.current
    val runtime = remember { LinuxRuntime(context) }
    val scope = rememberCoroutineScope()
    var page by remember { mutableStateOf("Workspace") }
    var installed by remember { mutableStateOf(runtime.installer.isInstalled()) }
    var installing by remember { mutableStateOf(false) }
    var installProgress by remember { mutableIntStateOf(0) }
    var installMessage by remember { mutableStateOf("Ubuntu ARM64 is not installed") }
    var currentPath by remember { mutableStateOf("/") }
    var error by remember { mutableStateOf<String?>(null) }
    var terminalOutput by remember { mutableStateOf("AGCodespace · POSIX PTY\r\n") }
    val vtScreen = remember { VtScreen(48, 24).apply { feed("AGCodespace · native PTY\r\nUbuntu shell not started.\r\nInstall Ubuntu and package the PRoot runtime.\r\n") } }
    val utf8Decoder = remember { Utf8StreamDecoder() }
    var terminalScreen by remember { mutableStateOf(vtScreen.render()) }
    var terminalInput by remember { mutableStateOf("") }
    val pty = remember { NativePty() }
    var terminalRunning by remember { mutableStateOf(pty.isAlive()) }
    var remoteConfirmed by remember { mutableStateOf(false) }
    var ctrlArmed by remember { mutableStateOf(false) }
    var altArmed by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val inputFocus = remember { FocusRequester() }
    val latestUrl = remember(terminalOutput) {
        Regex("""https?://[^\s<>"']+""").findAll(terminalOutput).lastOrNull()?.value?.substringBefore('\u001b')?.trimEnd('.', ',', ';')
    }

    fun sendTerminalCommand(command: String) {
        if (!terminalRunning) {
            error = "Start the Ubuntu shell before running remote commands."
            page = "Terminal"
            return
        }
        pty.write((command + "\r").toByteArray(Charsets.UTF_8))
        terminalInput = ""
        page = "Terminal"
    }

    fun startLinuxShell() {
        if (terminalRunning) return
        try {
            val argv = runtime.launchArgv()
            runtime.configureEnvironment(pty)
            vtScreen.reset()
            terminalScreen = vtScreen.render()
            val result = pty.start(argv, runtime.installer.rootfs.absolutePath, 48, 24)
            if (result < 0) throw IllegalStateException("PTY could not start (native error $result)")
            terminalRunning = true
            TerminalSessionService.start(context)
            terminalOutput += "Starting Ubuntu login shell…\r\n"
        } catch (t: Throwable) {
            try { pty.close() } catch (_: Throwable) { }
            terminalRunning = false
            try { TerminalSessionService.stop(context) } catch (_: Throwable) { }
            error = t.message ?: t.javaClass.simpleName
            val message = "\r\n[Startup error] ${error}\r\n"
            terminalOutput += message
            vtScreen.feed(message)
            terminalScreen = vtScreen.render()
        }
    }

    LaunchedEffect(terminalRunning) {
        while (terminalRunning) {
            val bytes = try { pty.read(8192) } catch (_: Throwable) { byteArrayOf() }
            if (bytes.isNotEmpty()) {
                val chunk = utf8Decoder.decode(bytes)
                terminalOutput = (terminalOutput + chunk).takeLast(50000)
                vtScreen.feed(chunk)
                terminalScreen = vtScreen.render()
            } else if (!pty.isAlive()) {
                terminalRunning = false
                TerminalSessionService.stop(context)
                terminalOutput = (terminalOutput + "\r\n[Process exited]\r\n").takeLast(50000)
                vtScreen.feed("\r\n[Process exited]\r\n")
                terminalScreen = vtScreen.render()
            }
            if (bytes.isEmpty()) delay(35) else delay(8)
        }
    }
    LaunchedEffect(terminalScreen) { scroll.animateScrollTo(scroll.maxValue) }

    MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Canvas, surface = Panel)) {
        Scaffold(containerColor = Canvas, bottomBar = {
            NavigationBar(containerColor = Panel) {
                listOf("Workspace", "Terminal", "Remote", "Files", "Settings").forEach { item ->
                    NavigationBarItem(selected = page == item, onClick = { page = item },
                        icon = { Icon(when (item) { "Terminal" -> Icons.Default.Terminal; "Remote" -> Icons.Default.Cloud; "Files" -> Icons.Default.Folder; "Settings" -> Icons.Default.Settings; else -> Icons.Default.Cloud }, item) },
                        label = { Text(item) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = Accent, selectedTextColor = Accent, indicatorColor = Color(0xFF151923), unselectedTextColor = Muted))
                }
            }
        }) { inset ->
            Column(Modifier.fillMaxSize().padding(inset).padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(44.dp).background(Accent.copy(alpha = .16f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Terminal, null, tint = Accent) }
                    Column(Modifier.weight(1f)) { Text("AGCodespace", fontSize = 22.sp, color = Color.White); Text("Rootless Ubuntu workspace", color = Muted, fontSize = 12.sp) }
                    Surface(color = if (installed) Color(0xFF203D35) else Color(0xFF4B3920), shape = RoundedCornerShape(50)) { Text(if (installed) "UBUNTU READY" else "SETUP", Modifier.padding(horizontal = 10.dp, vertical = 6.dp), color = if (installed) Color(0xFF9CE5C8) else Color(0xFFFFD38A), fontSize = 10.sp) }
                }
                when (page) {
                    "Terminal" -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Terminal", fontSize = 24.sp, color = Color.White, modifier = Modifier.weight(1f))
                            Text(if (terminalRunning) "● RUNNING" else "○ STOPPED", color = if (terminalRunning) Color(0xFF9CE5C8) else Muted, fontSize = 11.sp)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { startLinuxShell() }, enabled = !terminalRunning && installed && runtime.runtimeReady(), modifier = Modifier.weight(1f)) { Text("Start shell") }
                            OutlinedButton(onClick = { if (terminalRunning) { pty.write(byteArrayOf(3)); terminalOutput += "\r\n^C\r\n" } }, enabled = terminalRunning) { Text("Ctrl+C") }
                            OutlinedButton(onClick = { if (terminalRunning) { pty.close(); terminalRunning = false; TerminalSessionService.stop(context); terminalOutput += "\r\n[Session stopped]\r\n"; vtScreen.feed("\r\n[Session stopped]\r\n"); terminalScreen = vtScreen.render() } }, enabled = terminalRunning) { Text("Stop") }
                        }
                        Column(Modifier.weight(1f).fillMaxWidth().background(Color(0xFF080B10), RoundedCornerShape(16.dp)).padding(12.dp).verticalScroll(scroll)) {
                            Text(terminalScreen, modifier = Modifier.horizontalScroll(rememberScrollState()), color = Color(0xFFD9E2F0), fontFamily = FontFamily.Monospace, fontSize = 10.sp, lineHeight = 11.sp, softWrap = false)
                        }
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(onClick = { ctrlArmed = !ctrlArmed; altArmed = false }, enabled = terminalRunning, contentPadding = PaddingValues(horizontal = 9.dp, vertical = 4.dp)) { Text(if (ctrlArmed) "CTRL ✓" else "CTRL", fontSize = 11.sp) }
                            OutlinedButton(onClick = { altArmed = !altArmed; ctrlArmed = false }, enabled = terminalRunning, contentPadding = PaddingValues(horizontal = 9.dp, vertical = 4.dp)) { Text(if (altArmed) "ALT ✓" else "ALT", fontSize = 11.sp) }
                            listOf("TAB" to "\t", "ESC" to "\u001b", "←" to "\u001b[D", "↑" to "\u001b[A", "↓" to "\u001b[B", "→" to "\u001b[C", "HOME" to "\u001b[H", "END" to "\u001b[F", "CTRL+C" to "\u0003", "CTRL+D" to "\u0004", "CTRL+Z" to "\u001a", "|" to "|").forEach { (label, value) ->
                                OutlinedButton(onClick = { if (terminalRunning) pty.write(value.toByteArray()) }, enabled = terminalRunning, contentPadding = PaddingValues(horizontal = 9.dp, vertical = 4.dp)) { Text(label, fontSize = 11.sp) }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = terminalInput, onValueChange = { next ->
                                var consumed = false
                                if (terminalRunning) {
                                    val previous = terminalInput
                                    val inserted = if (next.startsWith(previous)) next.substring(previous.length) else ""
                                    if (inserted.isNotEmpty() && (ctrlArmed || altArmed)) {
                                        val payload = if (ctrlArmed) inserted.map { (it.uppercaseChar().code and 0x1f).toByte() }.toByteArray() else byteArrayOf(0x1b) + inserted.toByteArray(Charsets.UTF_8)
                                        pty.write(payload); ctrlArmed = false; altArmed = false; terminalInput = ""; consumed = true
                                    } else when {
                                        next.startsWith(previous) -> pty.write(inserted.toByteArray(Charsets.UTF_8))
                                        previous.startsWith(next) -> repeat(previous.length - next.length) { pty.write(byteArrayOf(0x7f.toByte())) }
                                        else -> { pty.write(byteArrayOf(0x15)); pty.write(next.toByteArray(Charsets.UTF_8)) }
                                    }
                                }
                                if (!consumed) terminalInput = next
                            }, modifier = Modifier.weight(1f).focusRequester(inputFocus), singleLine = true, placeholder = { Text(if (terminalRunning) "Type directly into shell…" else "Start the Ubuntu shell first") }, enabled = terminalRunning, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { if (terminalRunning) { pty.write(byteArrayOf(0x0d)); terminalInput = "" } }))
                            Button(onClick = { if (terminalRunning) { pty.write(byteArrayOf(0x0d)); terminalInput = ""; inputFocus.requestFocus() } }, enabled = terminalRunning) { Text("Enter") }
                        }
                        Text("A lightweight VT screen model handles common cursor and erase sequences. Full xterm color/style and advanced terminal protocol compatibility still require device testing.", color = Muted, fontSize = 11.sp, lineHeight = 15.sp)
                    }
                    "Remote" -> {
                        Text("GitHub Codespaces", fontSize = 24.sp, color = Color.White)
                        Text("Connect over SSH, then run Antigravity CLI on the remote Codespace. The local Ubuntu terminal must be running first.", color = Muted, fontSize = 13.sp, lineHeight = 18.sp)
                        if (latestUrl != null) {
                            Surface(color = Color(0xFF203D35), shape = RoundedCornerShape(14.dp)) {
                                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("URL detected in terminal output · verify before opening", color = Color(0xFF9CE5C8), fontSize = 14.sp)
                                    Text(latestUrl, color = Color.White, fontSize = 11.sp, maxLines = 3)
                                    Button(onClick = { try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(latestUrl))) } catch (t: Throwable) { error = "Could not open browser: ${t.message}" } }, modifier = Modifier.fillMaxWidth()) { Text("Open in Android browser") }
                                }
                            }
                        }
                        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            InfoCard("1 · Prepare local Ubuntu", "Run once", "Install GitHub CLI, SSH and curl inside the Ubuntu rootfs.")
                            Button(onClick = { sendTerminalCommand("apt-get update && apt-get install -y gh openssh-client curl ca-certificates") }, modifier = Modifier.fillMaxWidth(), enabled = terminalRunning) { Text("Install GitHub CLI + SSH") }
                            InfoCard("2 · Authenticate GitHub", "Browser/device flow", "GitHub CLI opens a device/browser sign-in flow. Complete it in Android's browser if requested.")
                            Button(onClick = { sendTerminalCommand("gh auth login --web") }, modifier = Modifier.fillMaxWidth(), enabled = terminalRunning) { Text("Sign in to GitHub") }
                            Button(onClick = { sendTerminalCommand("gh codespace list") }, modifier = Modifier.fillMaxWidth(), enabled = terminalRunning, colors = ButtonDefaults.outlinedButtonColors()) { Text("List available Codespaces") }
                            var codespaceName by remember { mutableStateOf("") }
                            OutlinedTextField(value = codespaceName, onValueChange = { codespaceName = it.filter { c -> c.isLetterOrDigit() || c == '-' } }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Codespace name") }, placeholder = { Text("your-codespace-name") })
                            Button(onClick = { if (codespaceName.isBlank()) error = "Enter the Codespace name shown by gh codespace list." else { remoteConfirmed = false; sendTerminalCommand("gh codespace ssh -c ${codespaceName}") } }, modifier = Modifier.fillMaxWidth(), enabled = terminalRunning && codespaceName.isNotBlank()) { Text("Connect over SSH") }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = remoteConfirmed, onCheckedChange = { remoteConfirmed = it })
                                Text("I have connected to the intended Codespace", color = Color.White, fontSize = 12.sp)
                            }
                            InfoCard("3 · Install Antigravity CLI remotely", "Official installer", "After SSH connects, install and launch Antigravity inside the remote shell. URLs printed by agy can be opened above; paste the returned code into the terminal.")
                            Button(onClick = { sendTerminalCommand("curl -fsSL https://antigravity.google/cli/install.sh | bash") }, modifier = Modifier.fillMaxWidth(), enabled = terminalRunning && remoteConfirmed) { Text("Install Antigravity CLI") }
                            Button(onClick = { sendTerminalCommand("agy --version") }, modifier = Modifier.fillMaxWidth(), enabled = terminalRunning && remoteConfirmed, colors = ButtonDefaults.outlinedButtonColors()) { Text("Verify agy installation") }
                            Surface(color = Color(0xFF4B3920), shape = RoundedCornerShape(12.dp)) {
                                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("Security-sensitive action", color = Color(0xFFFFD38A), fontSize = 14.sp)
                                    Text("This starts Antigravity Remote Control with permission checks skipped. Use only in a Codespace and repository you trust.", color = Muted, fontSize = 12.sp)
                                    Button(onClick = { sendTerminalCommand("agy --remote-control --dangerously-skip-permissions") }, modifier = Modifier.fillMaxWidth(), enabled = terminalRunning && remoteConfirmed, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF68452A))) { Text("Launch Remote Control") }
                                }
                            }
                            Text("These controls send commands to the currently running PTY. Connect to a Codespace first before running the Antigravity installer or launcher.", color = Muted, fontSize = 11.sp, lineHeight = 15.sp)
                        }
                    }
                    "Files" -> {
                        Text("Linux files", fontSize = 24.sp, color = Color.White)
                        InfoCard("Ubuntu root filesystem", if (installed) "Installed" else "Not installed", runtime.installer.rootfs.absolutePath)
                        InfoCard("Storage", "Persistent", "Root filesystem and installed packages live in the app's private files directory and survive app restarts.")
                        if (installed) {
                            val root = runtime.installer.rootfs.canonicalFile
                            val current = File(root, currentPath.removePrefix("/")).canonicalFile
                            val safeCurrent = if (current.path == root.path || current.path.startsWith(root.path + File.separator)) current else root
                            val listing = safeCurrent.listFiles()?.filter { candidate ->
                                val canonical = try { candidate.canonicalFile.path } catch (_: Exception) { "" }
                                canonical == root.path || canonical.startsWith(root.path + File.separator)
                            }?.sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() }))?.take(200).orEmpty()
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(currentPath, color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                TextButton(onClick = { currentPath = if (currentPath == "/") "/" else currentPath.substringBeforeLast('/', "").ifBlank { "/" }.let { if (it.startsWith("/")) it else "/$it" } }, enabled = currentPath != "/") { Text("Up") }
                            }
                            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).background(Panel, RoundedCornerShape(14.dp)).padding(8.dp)) {
                                listing.forEach { entry ->
                                    Row(Modifier.fillMaxWidth().clickable(enabled = entry.isDirectory) { currentPath = (if (currentPath == "/") "" else currentPath) + "/" + entry.name }.padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(if (entry.isDirectory) Icons.Default.Folder else Icons.Default.Terminal, null, tint = if (entry.isDirectory) Accent else Muted, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(10.dp))
                                        Text(entry.name, color = Color.White, modifier = Modifier.weight(1f), fontFamily = FontFamily.Monospace, fontSize = 12.sp, maxLines = 1)
                                        if (!entry.isDirectory) Text("${entry.length()} B", color = Muted, fontSize = 10.sp)
                                    }
                                    Divider(color = Color(0xFF303847))
                                }
                            }
                        }
                    }
                    "Settings" -> {
                        Text("Settings & diagnostics", fontSize = 24.sp, color = Color.White)
                        InfoCard("Device architecture", android.os.Build.SUPPORTED_ABIS.joinToString(), "This build packages the ARM64 PRoot runtime only.")
                        InfoCard("PRoot runtime", if (runtime.runtimeReady()) "Available" else "Missing", "Native libraries: ${context.applicationInfo.nativeLibraryDir}")
                        InfoCard("Ubuntu source", "Official Ubuntu Base", "26.04 Resolute ARM64. The archive is checked against Ubuntu's published SHA256SUMS before extraction.")
                        InfoCard("Runtime constraints", "Rootless userspace", "PRoot does not provide a real kernel, systemd/PID 1, kernel modules, or unrestricted mounts. Secret Service / D-Bus behavior may be limited in Android PRoot.")
                    }
                    else -> {
                        Text("Your Linux workspace", fontSize = 24.sp, color = Color.White)
                        Text("A persistent Ubuntu ARM64 userspace for Android, with a native PTY shell.", color = Muted, fontSize = 14.sp)
                        InfoCard("Ubuntu ARM64", if (installed) "Installed" else "Needs setup", if (installed) "Root filesystem verified and available." else "Download and verify Ubuntu Base, then extract it into persistent app storage.")
                        if (installing) {
                            LinearProgressIndicator(progress = { installProgress / 100f }, modifier = Modifier.fillMaxWidth())
                            Text("$installProgress% · $installMessage", color = Muted, fontSize = 12.sp)
                        } else if (!installed) {
                            Button(onClick = {
                                installing = true; error = null
                                scope.launch {
                                    try { runtime.installer.install { message, percent -> installMessage = message; installProgress = percent }; installed = runtime.installer.isInstalled() }
                                    catch (t: Throwable) { error = t.message ?: t.javaClass.simpleName }
                                    finally { installing = false }
                                }
                            }, modifier = Modifier.fillMaxWidth(), enabled = !installing) { Text("Install Ubuntu ARM64") }
                        }
                        InfoCard("Interactive terminal", if (terminalRunning) "Session active" else if (runtime.runtimeReady()) "Ready to start" else "Runtime not packaged", "Native POSIX PTY and PRoot shell. A complete VT renderer is needed for full-screen editors.")
                        InfoCard("Build pipeline", "GitHub Actions", "Pinned LinuxDroidapp/proot source build, Android unit tests, APK checksum, artifact upload retention: 1 day.")
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Button(onClick = { page = "Terminal" }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Default.Terminal, null); Spacer(Modifier.width(8.dp)); Text("Open terminal") }; OutlinedButton(onClick = { page = "Remote" }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Default.Cloud, null); Spacer(Modifier.width(8.dp)); Text("Codespaces") } }
                    }
                }
                error?.let { message ->
                    Surface(color = Color(0xFF47272B), shape = RoundedCornerShape(12.dp)) { Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Text(message, color = Color(0xFFFFC6C9), modifier = Modifier.weight(1f), fontSize = 12.sp); TextButton(onClick = { error = null }) { Text("Dismiss") } } }
                }
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, status: String, detail: String) {
    Surface(color = Panel, shape = RoundedCornerShape(18.dp), tonalElevation = 1.dp) {
        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, color = Color.White, fontSize = 15.sp)
            Text(status, color = Accent, fontSize = 12.sp)
            Text(detail, color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
        }
    }
}
