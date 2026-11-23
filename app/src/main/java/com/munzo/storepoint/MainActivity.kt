package com.munzo.storepoint

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * StorePoint v2 Android POS & Back-Office ERP Host Activity.
 * Renders the offline-first Material 3 Expressive (`matraic/m3e`) StorePoint v2 bundle
 * (`file:///android_asset/storepoint-v2-portal.html`) or connects to a self-hosted StorePoint v2 Gateway.
 */
class MainActivity : Activity() {
    private var webView: WebView? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val view = WebView(this).apply {
            webViewClient = WebViewClient()
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                cacheMode = WebSettings.LOAD_DEFAULT
                allowFileAccess = true
            }
            addJavascriptInterface(
                PosNativeBridge { enableKiosk ->
                    runOnUiThread {
                        if (enableKiosk) startLockTask() else stopLockTask()
                    }
                },
                "StorePointNative"
            )
            val gatewayUrl = intent?.getStringExtra("GATEWAY_URL")?.trim()
            if (!gatewayUrl.isNullOrEmpty()) {
                loadUrl(gatewayUrl)
            } else {
                loadUrl("file:///android_asset/storepoint-v2-portal.html")
            }
        }

        webView = view
        setContentView(view)
    }

    override fun onDestroy() {
        webView?.destroy()
        webView = null
        super.onDestroy()
    }
}
