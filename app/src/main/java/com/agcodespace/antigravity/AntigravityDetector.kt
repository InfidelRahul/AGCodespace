package com.agcodespace.antigravity

import android.net.Uri

object AntigravityDetector {
    private val url = Regex("https://[^\\s\\u001b\\\"<>]+", RegexOption.IGNORE_CASE)
    private val auth = Regex("(?i)(select login method|google oauth|signing in|not signed in|verification code|device code)")

    data class Event(val remoteUrl: String? = null, val authRequired: Boolean = false, val text: String)

    fun inspect(text: String): Event {
        val candidate = url.findAll(text).map { it.value.trimEnd('.', ',', ')', ']', '>', '\\') }.firstOrNull()
        val normalized = candidate?.let { runCatching { Uri.parse(it) }.getOrNull() }
        val isRemote = normalized?.host?.endsWith("antigravity.google.com") == true || candidate?.contains("remote", true) == true
        return Event(if (isRemote) candidate else null, auth.containsMatchIn(text), text)
    }
}
