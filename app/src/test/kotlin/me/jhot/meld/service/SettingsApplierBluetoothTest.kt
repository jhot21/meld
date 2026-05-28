package me.jhot.meld.service

import io.mockk.mockk
import io.mockk.verify
import me.jhot.meld.data.model.ModeSettings
import org.junit.Before
import org.junit.Test
import android.content.Context
import io.mockk.every
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher

class SettingsApplierBluetoothTest {

    private lateinit var btManager: BluetoothLifecycleManager
    private lateinit var applier: SettingsApplier

    @Before
    fun setUp() {
        val context = mockk<Context>(relaxed = true)
        val permissionChecker = mockk<PermissionChecker>(relaxed = true)
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
}
