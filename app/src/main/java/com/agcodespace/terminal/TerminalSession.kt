package com.agcodespace.terminal
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.*
import java.io.File

class TerminalSession {
    val output = mutableStateOf("")
    private var fd = -1
    private var job: Job? = null
    fun start() {
        if (fd >= 0) return
        val shell = listOf("/system/bin/sh", "/data/data/com.agcodespace/files/bin/bash").firstOrNull { File(it).exists() }
            ?: "/system/bin/sh"
        fd = NativePty.spawn(shell, 40, 120)
        job = CoroutineScope(Dispatchers.IO).launch {
            val buf = ByteArray(8192)
            while (isActive && fd >= 0) {
                val n = NativePty.read(fd, buf)
                if (n <= 0) break
                val s = String(buf,0,n,Charsets.UTF_8)
                withContext(Dispatchers.Main) { output.value = (output.value + s).takeLast(50000) }
            }
        }
    }
}
