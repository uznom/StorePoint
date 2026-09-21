package com.munzo.storepoint.util

import com.munzo.storepoint.BuildConfig

/**
 * Single source of truth for the app version shown in the UI and used by the in-app
 * update flow (H5). Synchronized dynamically from `versionName` in app/build.gradle.kts.
 */
val APP_VERSION: String get() = BuildConfig.VERSION_NAME