package com.agcodespace.terminal

import android.net.Uri
import com.agcodespace.antigravity.AntigravityDetector
import java.util.concurrent.CopyOnWriteArraySet

object TerminalEvents {
    val listeners = CopyOnWriteArraySet<(AntigravityDetector.Event) -> Unit>()
    fun emit(text: String) {
        val event = AntigravityDetector.inspect(text)
        if (event.authRequired || event.remoteUrl != null || Regex("https://[^\\s]+", RegexOption.IGNORE_CASE).containsMatchIn(text)) listeners.forEach { it(event) }
    }
}
