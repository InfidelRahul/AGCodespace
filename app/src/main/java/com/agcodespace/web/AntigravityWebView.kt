package com.agcodespace.web

import android.content.Context
import android.graphics.Color
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

class AntigravityWebView(context: Context, private val external: (String) -> Unit) : WebView(context) {
    private var loaded: String? = null
    init {
        setBackgroundColor(Color.BLACK)
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        webChromeClient = WebChromeClient()
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val host = request.url.host.orEmpty()
                return if (host.endsWith("antigravity.google.com") || host.endsWith("accounts.google.com")) false else { external(request.url.toString()); true }
            }
        }
        layoutParams = ViewGroup.LayoutParams(-1, -1)
    }
    fun open(url: String) { if (loaded != url) { loaded = url; loadUrl(url) } }
}
