package com.munzo.storepoint

import android.webkit.JavascriptInterface

/**
 * Native Android Hardware & Kiosk Bridge exposed as `window.StorePointNative`
 * to the StorePoint v2 Material 3 Expressive (`matraic/m3e`) ERP & Offline-First POS UI.
 */
class PosNativeBridge(
    private val onToggleKioskLockTask: (Boolean) -> Unit = {}
) {
    @JavascriptInterface
    fun getVersionName(): String = "2.0.0"

    @JavascriptInterface
    fun getArchitectureMode(): String =
        "StorePoint v2 Decoupled 7-Service Architecture + matraic/m3e Offline-First POS"

    @JavascriptInterface
    fun getEscPosDrawerKickHex(): String = "1B700019FA"

    @JavascriptInterface
    fun setKioskPinning(enabled: Boolean) {
        onToggleKioskLockTask(enabled)
    }
}
