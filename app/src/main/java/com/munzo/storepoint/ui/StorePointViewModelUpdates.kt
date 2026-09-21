package com.munzo.storepoint.ui

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.munzo.storepoint.util.APP_VERSION
import com.munzo.storepoint.util.AppUpdateInfo
import com.munzo.storepoint.util.AppUpdateManager
import com.munzo.storepoint.util.UpdateDownloadState
import java.io.File
import kotlinx.coroutines.launch

// --- In-App Updates Handlers ---
internal fun StorePointViewModel.updatesCheckForAppUpdatesImpl(currentVersion: String = APP_VERSION, onComplete: (AppUpdateInfo?) -> Unit = {}) {
    if (updateCheckInProgress.value) return
    updateCheckInProgress.value = true
    updateErrorMessage.value = null
    viewModelScope.launch {
        val result = AppUpdateManager.checkForUpdates(currentVersionName = currentVersion)
        updateCheckInProgress.value = false
        result.onSuccess { info ->
            updateInfo.value = info
            onComplete(info)
        }.onFailure { err ->
            updateErrorMessage.value = err.localizedMessage ?: "Failed to check for updates"
            onComplete(null)
        }
    }
}

internal fun StorePointViewModel.updatesStartUpdateDownloadImpl(context: Context, downloadUrl: String, fileName: String = "StorePoint-update.apk") {
    viewModelScope.launch {
        AppUpdateManager.downloadApk(
            context = context,
            downloadUrl = downloadUrl,
            fileName = fileName,
            expectedSha256 = updateInfo.value?.sha256Checksum ?: ""
        ) { state ->
            updateDownloadState.value = state
        }
    }
}

internal fun StorePointViewModel.updatesInstallDownloadedApkImpl(context: Context, apkFile: File) {
    AppUpdateManager.installApk(context, apkFile)
}

internal fun StorePointViewModel.updatesClearUpdateStateImpl() {
    updateInfo.value = null
    updateErrorMessage.value = null
    updateDownloadState.value = UpdateDownloadState.Idle
}
