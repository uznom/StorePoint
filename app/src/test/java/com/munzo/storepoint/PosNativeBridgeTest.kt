package com.munzo.storepoint

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PosNativeBridgeTest {
    @Test
    fun bridgeExposesV2VersionEscPosKickAndKioskCallback() {
        var kioskEnabled = false
        val bridge = PosNativeBridge { enabled -> kioskEnabled = enabled }

        assertEquals("2.0.0", bridge.getVersionName())
        assertEquals("1B700019FA", bridge.getEscPosDrawerKickHex())
        assertTrue(bridge.getArchitectureMode().contains("matraic/m3e"))

        bridge.setKioskPinning(true)
        assertTrue(kioskEnabled)
    }
}
