package me.jhot.meld

import android.os.Build
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the [needsNotificationPermission] helper extracted from [MainActivity].
 *
 * Uses integer literals instead of [Build.VERSION_CODES] and [android.content.pm.PackageManager]
 * constants so this runs as a pure JVM unit test without Android stubs.
 * TIRAMISU = 33, PERMISSION_GRANTED = true (mapped by the helper).
 */
class MainActivityPermissionTest {

    @Test
    fun doesNotRequestPermission_belowTiramisu() {
        // API 32 is below Tiramisu (33) — should never request regardless of grant state
        assertFalse(needsNotificationPermission(sdkVersion = 32, isGranted = false))
        assertFalse(needsNotificationPermission(sdkVersion = 32, isGranted = true))
    }

    @Test
    fun doesNotRequestPermission_whenAlreadyGranted() {
        assertFalse(needsNotificationPermission(sdkVersion = 33, isGranted = true))
        assertFalse(needsNotificationPermission(sdkVersion = 35, isGranted = true))
    }

    @Test
    fun requestsPermission_whenNotGrantedOnTiramisuOrAbove() {
        assertTrue(needsNotificationPermission(sdkVersion = 33, isGranted = false))
        assertTrue(needsNotificationPermission(sdkVersion = 35, isGranted = false))
    }
}
