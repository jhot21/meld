package me.jhot.meld.service

import android.media.AudioManager
import android.provider.Settings
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import me.jhot.meld.data.model.ModeSettings
import org.junit.Before
import org.junit.Test
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher

class SettingsApplierBluetoothTest {

    private lateinit var context: Context
    private lateinit var permissionChecker: PermissionChecker
    private lateinit var btManager: BluetoothLifecycleManager
    private lateinit var applier: SettingsApplier

    @Before
    fun setUp() {
        context = mockk<Context>(relaxed = true)
        permissionChecker = mockk<PermissionChecker>(relaxed = true)
        val overrideSessionStore = mockk<OverrideSessionStore>(relaxed = true)
        btManager = mockk(relaxed = true)
        val scope = CoroutineScope(UnconfinedTestDispatcher())

        every { permissionChecker.canWriteSettings() } returns false
        every { permissionChecker.canWriteNotificationPolicy() } returns false
        every { permissionChecker.canWriteSecureSettings() } returns false

        applier = SettingsApplier(context, permissionChecker, overrideSessionStore, scope, btManager)
    }

    @Test
    fun `setDesired called with true when bluetooth is true`() {
        applier.apply(ModeSettings(bluetooth = true))
        verify { btManager.setDesired(true) }
    }

    @Test
    fun `setDesired called with false when bluetooth is false`() {
        applier.apply(ModeSettings(bluetooth = false))
        verify { btManager.setDesired(false) }
    }

    @Test
    fun `setDesired called with null when bluetooth is null`() {
        applier.apply(ModeSettings(bluetooth = null))
        verify { btManager.setDesired(null) }
    }

    @Test
    fun `setMediaVolume routes through Shizuku when permission is available`() {
        mockkObject(ShizukuGranter)
        try {
            every { ShizukuGranter.hasPermission() } returns true
            coEvery { ShizukuGranter.setMediaVolumeDirect(any()) } returns true
            every { permissionChecker.canWriteSettings() } returns true

            val audioManager = mockk<AudioManager>()
            every { context.getSystemService(Context.AUDIO_SERVICE) } returns audioManager
            every { audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) } returns 5

            applier.apply(ModeSettings(volumeMedia = 8))

            coVerify(exactly = 1) { ShizukuGranter.setMediaVolumeDirect(8) }
        } finally {
            unmockkObject(ShizukuGranter)
        }
    }

    @Test
    fun `setMediaVolume falls back to AudioManager when Shizuku unavailable`() {
        mockkObject(ShizukuGranter)
        try {
            every { ShizukuGranter.hasPermission() } returns false
            every { permissionChecker.canWriteSettings() } returns true

            val audioManager = mockk<AudioManager>()
            every { context.getSystemService(Context.AUDIO_SERVICE) } returns audioManager
            every { audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) } returns 5
            every { audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 8, 0) } just runs

            applier.apply(ModeSettings(volumeMedia = 8))

            verify(exactly = 1) { audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 8, 0) }
            coVerify(exactly = 0) { ShizukuGranter.setMediaVolumeDirect(any()) }
        } finally {
            unmockkObject(ShizukuGranter)
        }
    }

    @Test
    fun `failed secure settings are batched into a single putSettings call`() {
        mockkObject(ShizukuGranter)
        mockkStatic(Settings.Secure::class)
        try {
            every { ShizukuGranter.hasPermission() } returns true
            coEvery { ShizukuGranter.putSettings(any()) } returns true
            every { permissionChecker.canWriteSecureSettings() } returns true
            every {
                Settings.Secure.putInt(any(), "reduce_bright_colors_activated", 1)
            } throws SecurityException("blocked")

            applier.apply(ModeSettings(extraDim = true))

            coVerify(exactly = 1) {
                ShizukuGranter.putSettings(match { changes ->
                    changes.size == 1 &&
                        changes[0].namespace == "secure" &&
                        changes[0].key == "reduce_bright_colors_activated" &&
                        changes[0].value == 1
                })
            }
        } finally {
            unmockkObject(ShizukuGranter)
            unmockkStatic(Settings.Secure::class)
        }
    }

    @Test
    fun `multiple failed secure settings produce one putSettings call`() {
        mockkObject(ShizukuGranter)
        mockkStatic(Settings.Secure::class)
        mockkStatic(Settings.System::class)
        try {
            every { ShizukuGranter.hasPermission() } returns true
            coEvery { ShizukuGranter.putSettings(any()) } returns true
            every { permissionChecker.canWriteSecureSettings() } returns true
            every {
                Settings.Secure.putInt(any(), "reduce_bright_colors_activated", 1)
            } throws SecurityException("blocked")
            every {
                Settings.System.getInt(any(), "keyboard_vibration_enabled", -1)
            } returns -1
            every {
                Settings.System.putInt(any(), "keyboard_vibration_enabled", 1)
            } throws SecurityException("blocked")

            applier.apply(ModeSettings(extraDim = true, keyboardVibration = true))

            coVerify(exactly = 1) {
                ShizukuGranter.putSettings(match { changes ->
                    changes.size == 2 &&
                        changes.any { it.namespace == "secure" && it.key == "reduce_bright_colors_activated" } &&
                        changes.any { it.namespace == "system" && it.key == "keyboard_vibration_enabled" }
                })
            }
        } finally {
            unmockkObject(ShizukuGranter)
            unmockkStatic(Settings.Secure::class)
            unmockkStatic(Settings.System::class)
        }
    }
}
