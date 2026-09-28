package com.munzo.storepoint

import com.munzo.storepoint.util.AppUpdateManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateManagerTest {

    @Test
    fun isVersionNewer_detectsMajorIncrement() {
        assertTrue(AppUpdateManager.isVersionNewer("v3.0.0", "2.4.0"))
        assertTrue(AppUpdateManager.isVersionNewer("3.0.0", "2.9.9"))
    }

    @Test
    fun isVersionNewer_detectsMinorIncrement() {
        assertTrue(AppUpdateManager.isVersionNewer("v2.5.0", "2.4.0"))
        assertTrue(AppUpdateManager.isVersionNewer("2.10.0", "2.9.0"))
    }

    @Test
    fun isVersionNewer_detectsPatchIncrement() {
        assertTrue(AppUpdateManager.isVersionNewer("v2.4.1", "2.4.0"))
        assertTrue(AppUpdateManager.isVersionNewer("2.4.10", "2.4.9"))
    }

    @Test
    fun isVersionNewer_detectsSubPatchIncrement() {
        assertTrue(AppUpdateManager.isVersionNewer("v2.4.0.1", "2.4.0"))
    }

    @Test
    fun isVersionNewer_returnsFalseForIdenticalVersion() {
        assertFalse(AppUpdateManager.isVersionNewer("2.4.0", "2.4.0"))
        assertFalse(AppUpdateManager.isVersionNewer("v2.4.0", "2.4.0"))
        assertFalse(AppUpdateManager.isVersionNewer("V2.4.0", "v2.4.0"))
    }

    @Test
    fun isVersionNewer_returnsFalseForOlderRemoteVersion() {
        assertFalse(AppUpdateManager.isVersionNewer("v2.3.9", "2.4.0"))
        assertFalse(AppUpdateManager.isVersionNewer("1.9.9", "2.0.0"))
        assertFalse(AppUpdateManager.isVersionNewer("2.4.0", "2.4.1"))
    }

    @Test
    fun isVersionNewer_handlesEmptyAndMalformedStringsSafely() {
        assertFalse(AppUpdateManager.isVersionNewer("", "2.4.0"))
        assertFalse(AppUpdateManager.isVersionNewer("invalid", "2.4.0"))
        assertFalse(AppUpdateManager.isVersionNewer("   ", "2.4.0"))
    }

    @Test
    fun appVersion_matchesCurrentReleaseTag() {
        // Guards the in-app updater: `isVersionNewer()` compares the newest GitHub
        // release tag against BuildConfig.VERSION_NAME. If versionName drifts below the
        // shipped tag, a terminal that already installed the latest APK is prompted to
        // update forever. Bump this in lockstep with app/build.gradle.kts and the tag.
        org.junit.Assert.assertEquals("1.0.1.4", com.munzo.storepoint.util.APP_VERSION)
    }

    @Test
    fun appVersion_isNotNewerThanItsOwnTag() {
        // A device already running this build must not be told an update is available.
        val current = com.munzo.storepoint.util.APP_VERSION
        assertFalse(AppUpdateManager.isVersionNewer("v$current", current))
    }
}
