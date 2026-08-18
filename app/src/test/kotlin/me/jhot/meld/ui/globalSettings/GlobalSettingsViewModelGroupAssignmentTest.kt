package me.jhot.meld.ui.globalSettings

import android.net.Uri
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.jhot.meld.data.model.ExclusivityGroup
import me.jhot.meld.data.model.Mode
import me.jhot.meld.data.model.ModeSettings
import me.jhot.meld.service.ImportExportService
import me.jhot.meld.service.ImportResult
import me.jhot.meld.service.ModeExport
import me.jhot.meld.service.ModeRepository
import me.jhot.meld.service.PermissionChecker
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GlobalSettingsViewModelGroupAssignmentTest {

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

    private fun permissionChecker() = mockk<PermissionChecker> {
        every { canWriteSettings() } returns true
        every { canWriteNotificationPolicy() } returns true
        every { canWriteSecureSettings() } returns true
        every { resetSecureSettingsCache() } just Runs
    }

    @Test
    fun onImportConfirmed_withLegacyPrimaryModes_transitionsToNeedsExclusivityGroupAssignment() = runTest {
        val importedModes = listOf(ModeExport("Work", isDefault = false, priority = 50, settings = ModeSettings()))
        val svc = mockk<ImportExportService> {
            coEvery { parseImport(any()) } returns ImportResult.Ready(importedModes, legacyPrimaryNames = listOf("Work"))
        }
        val repo = mockk<ModeRepository>(relaxed = true) {
            every { getAllModes() } returns flowOf(emptyList())
        }
        val vm = GlobalSettingsViewModel(permissionChecker(), "me.jhot.meld", svc, repo)

        vm.onImportFilePicked(mockk<Uri>())
        vm.onImportConfirmed()

        val result = vm.importResult.value
        assertTrue(result is ImportResult.NeedsExclusivityGroupAssignment)
        assertEquals(listOf("Work"), (result as ImportResult.NeedsExclusivityGroupAssignment).legacyPrimaryNames)
    }

    @Test
    fun onImportConfirmed_legacyPrimary_conflictingWithExistingMode_preselectsItsGroups() = runTest {
        val existing = Mode(id = 1L, name = "Work", priority = 40)
        val importedModes = listOf(ModeExport("Work", isDefault = false, priority = 50, settings = ModeSettings()))
        val svc = mockk<ImportExportService> {
            coEvery { parseImport(any()) } returns ImportResult.Ready(importedModes, legacyPrimaryNames = listOf("Work"))
        }
        val repo = mockk<ModeRepository>(relaxed = true) {
            every { getAllModes() } returns flowOf(listOf(existing))
            coEvery { getGroupsForMode(1L) } returns listOf(ExclusivityGroup(id = 100L, name = "Context"))
        }
        val vm = GlobalSettingsViewModel(permissionChecker(), "me.jhot.meld", svc, repo)

        vm.onImportFilePicked(mockk<Uri>())
        vm.onImportConfirmed()

        val result = vm.importResult.value as ImportResult.NeedsExclusivityGroupAssignment
        assertEquals(listOf("Context"), result.preselectedGroupNames["Work"])
    }

    @Test
    fun onExclusivityGroupsAssigned_callsImportModes_withChosenGroupNames() = runTest {
        val importedModes = listOf(ModeExport("Work", isDefault = false, priority = 50, settings = ModeSettings()))
        val svc = mockk<ImportExportService> {
            coEvery { parseImport(any()) } returns ImportResult.Ready(importedModes, legacyPrimaryNames = listOf("Work"))
        }
        val repo = mockk<ModeRepository>(relaxed = true) {
            every { getAllModes() } returns flowOf(emptyList())
        }
        val vm = GlobalSettingsViewModel(permissionChecker(), "me.jhot.meld", svc, repo)

        vm.onImportFilePicked(mockk<Uri>())
        vm.onImportConfirmed()
        vm.onExclusivityGroupsAssigned(mapOf("Work" to listOf("Context")))

        coVerify {
            repo.importModes(
                match { it.size == 1 && it[0].name == "Work" },
                match { it["Work"] == listOf("Context") },
            )
        }
        assertNull(vm.importResult.value)
    }
}
