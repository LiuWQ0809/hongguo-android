package com.hongguo.android

import android.app.Activity
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

/** Separate, user-selected fallback for an episode that the native player cannot prepare. */
class WebPlaybackActivity : Activity() {
    private lateinit var browser: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val series = intent.getStringExtra("series").orEmpty()
        val episode = intent.getStringExtra("episode").orEmpty()
        if (!series.matches(Regex("[0-9]+")) || !episode.matches(Regex("[0-9]+"))) { finish(); return }
        browser = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = true
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val uri = request.url
                    return uri.scheme != "https" || uri.host != "hongguoduanju.com"
                }
            }
            loadUrl("https://hongguoduanju.com/player/$series/$episode")
        }
        setContentView(browser)
    }

    @Deprecated("Android back navigation")
    override fun onBackPressed() {
        if (::browser.isInitialized && browser.canGoBack()) browser.goBack() else super.onBackPressed()
    }

    override fun onPause() { if (::browser.isInitialized) browser.onPause(); super.onPause() }
    override fun onResume() { super.onResume(); if (::browser.isInitialized) browser.onResume() }
    override fun onDestroy() { if (::browser.isInitialized) { browser.stopLoading(); browser.destroy() }; super.onDestroy() }
}
