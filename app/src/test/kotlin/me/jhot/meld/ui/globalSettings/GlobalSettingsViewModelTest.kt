package me.jhot.meld.ui.globalSettings

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.Runs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import me.jhot.meld.service.ImportExportService
import me.jhot.meld.service.ModeRepository
import me.jhot.meld.service.PermissionChecker
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GlobalSettingsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(rikka.shizuku.Shizuku::class)
        every { rikka.shizuku.Shizuku.addRequestPermissionResultListener(any()) } just Runs
        every { rikka.shizuku.Shizuku.removeRequestPermissionResultListener(any()) } returns true
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
        unmockkStatic(rikka.shizuku.Shizuku::class)
    }

    private fun viewModel(): GlobalSettingsViewModel {
        val permissionChecker = mockk<PermissionChecker> {
            every { canWriteSettings() } returns true
            every { canWriteNotificationPolicy() } returns true
            every { canWriteSecureSettings() } returns true
            every { resetSecureSettingsCache() } just Runs
        }
        return GlobalSettingsViewModel(
            permissionChecker = permissionChecker,
            packageName = "me.jhot.meld",
            importExportService = mockk(relaxed = true),
            modeRepository = mockk<ModeRepository> {
                every { getAllModes() } returns flowOf(emptyList())
            },
        )
    }

    @Test
    fun init_populatesPermissionStates_fromPermissionChecker() {
        // viewModel() mocks permissionChecker returning true for all permissions.
        // init {} calls refresh(), so values are set before any explicit refresh() call.
        val vm = viewModel()
        assertTrue(vm.writeSettingsGranted.value)
        assertTrue(vm.notificationPolicyGranted.value)
        assertTrue(vm.secureSettingsGranted.value)
    }
}
