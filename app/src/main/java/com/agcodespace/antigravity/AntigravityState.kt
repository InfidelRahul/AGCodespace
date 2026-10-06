package com.agcodespace.antigravity

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.agcodespace.terminal.TerminalEvents

object AntigravityState {
    var remoteUrl by mutableStateOf("")
    var authUrl by mutableStateOf("")
    private var lastUrl = ""
    init {
        TerminalEvents.listeners += { event ->
            event.remoteUrl?.let { if (it != lastUrl) { lastUrl = it; remoteUrl = it } }
            Regex("https://[^\\s]+", RegexOption.IGNORE_CASE).find(event.text)?.value?.trimEnd('.', ',', ')', ']')?.let { candidate ->
                if (event.remoteUrl == null && candidate != lastUrl) { lastUrl = candidate; authUrl = candidate }
            }
        }
    }
}
