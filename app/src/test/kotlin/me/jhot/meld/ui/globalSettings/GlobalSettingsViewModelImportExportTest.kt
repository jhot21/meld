package me.jhot.meld.ui.globalSettings

import android.net.Uri
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.Runs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.jhot.meld.data.model.Mode
import me.jhot.meld.data.model.ModeSettings
import me.jhot.meld.data.model.ModeType
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
class GlobalSettingsViewModelImportExportTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        // GlobalSettingsViewModel.init calls Shizuku static methods; mock them for JVM tests.
        mockkStatic(rikka.shizuku.Shizuku::class)
        every { rikka.shizuku.Shizuku.addRequestPermissionResultListener(any()) } just Runs
        every { rikka.shizuku.Shizuku.removeRequestPermissionResultListener(any()) } returns true
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
        unmockkStatic(rikka.shizuku.Shizuku::class)
    }

    private fun viewModel(
        importExportService: ImportExportService = mockk(relaxed = true),
        modeRepository: ModeRepository = mockk(relaxed = true),
    ): GlobalSettingsViewModel {
        val permissionChecker = mockk<PermissionChecker> {
            every { canWriteSettings() } returns true
            every { canWriteNotificationPolicy() } returns true
            every { canWriteSecureSettings() } returns true
            every { resetSecureSettingsCache() } just Runs
        }
        return GlobalSettingsViewModel(
            permissionChecker = permissionChecker,
            packageName = "me.jhot.meld",
            importExportService = importExportService,
            modeRepository = modeRepository,
        )
    }

    @Test
    fun onExportClicked_emitsFileNameAndJson() = runTest(testDispatcher) {
        val json = """{"exportVersion":1,"exportedAt":0,"modes":[]}"""
        val svc = mockk<ImportExportService> {
            coEvery { export() } returns json
            every { exportFileName() } returns "meld-export-2026-04-06.json"
        }
        val vm = viewModel(importExportService = svc)

        var emitted: Pair<String, String>? = null
        val job = launch { vm.exportReady.collect { emitted = it } }

        vm.onExportClicked()

        job.cancel()
        assertEquals("meld-export-2026-04-06.json" to json, emitted)
    }

    @Test
    fun onImportFilePicked_setsImportResult_toReady() = runTest {
        val importedModes = listOf(ModeExport("Work", ModeType.PRIMARY, 50, ModeSettings()))
        val svc = mockk<ImportExportService> {
            coEvery { parseImport(any()) } returns ImportResult.Ready(importedModes)
        }
        val vm = viewModel(importExportService = svc)

        vm.onImportFilePicked(mockk<Uri>())

        assertTrue(vm.importResult.value is ImportResult.Ready)
    }

    @Test
    fun onImportFilePicked_setsImportResult_toConflictsDetected() = runTest {
        val importedModes = listOf(ModeExport("Work", ModeType.PRIMARY, 50, ModeSettings()))
        val svc = mockk<ImportExportService> {
            coEvery { parseImport(any()) } returns ImportResult.ConflictsDetected(importedModes, listOf("Work"))
        }
        val vm = viewModel(importExportService = svc)

        vm.onImportFilePicked(mockk<Uri>())

        val result = vm.importResult.value
        assertTrue(result is ImportResult.ConflictsDetected)
        assertEquals(listOf("Work"), (result as ImportResult.ConflictsDetected).conflictingNames)
    }

    @Test
    fun onImportFilePicked_setsImportResult_toMalformedJson() = runTest {
        val svc = mockk<ImportExportService> {
            coEvery { parseImport(any()) } returns ImportResult.MalformedJson
        }
        val vm = viewModel(importExportService = svc)

        vm.onImportFilePicked(mockk<Uri>())

        assertEquals(ImportResult.MalformedJson, vm.importResult.value)
    }

    @Test
    fun onImportConfirmed_callsImportModes_andClearsResult() = runTest {
        val importedModes = listOf(ModeExport("Work", ModeType.PRIMARY, 50, ModeSettings(brightness = 200)))
        val svc = mockk<ImportExportService> {
            coEvery { parseImport(any()) } returns ImportResult.Ready(importedModes)
        }
        val repo = mockk<ModeRepository>(relaxed = true)
        val vm = viewModel(importExportService = svc, modeRepository = repo)

        vm.onImportFilePicked(mockk<Uri>())  // sets importResult to Ready
        vm.onImportConfirmed()

        coVerify {
            repo.importModes(match { list ->
                list.size == 1 && list[0].name == "Work" && list[0].settings.brightness == 200
            })
        }
        assertNull(vm.importResult.value)
    }

    @Test
    fun onImportConfirmed_worksWithConflictsDetectedResult() = runTest {
        val importedModes = listOf(ModeExport("Work", ModeType.PRIMARY, 50, ModeSettings()))
        val svc = mockk<ImportExportService> {
            coEvery { parseImport(any()) } returns ImportResult.ConflictsDetected(importedModes, listOf("Work"))
        }
        val repo = mockk<ModeRepository>(relaxed = true)
        val vm = viewModel(importExportService = svc, modeRepository = repo)

        vm.onImportFilePicked(mockk<Uri>())  // sets importResult to ConflictsDetected
        vm.onImportConfirmed()

        coVerify { repo.importModes(match { it.size == 1 && it[0].name == "Work" }) }
        assertNull(vm.importResult.value)
    }

    @Test
    fun onImportCancelled_clearsResult() = runTest {
        val svc = mockk<ImportExportService> {
            coEvery { parseImport(any()) } returns ImportResult.MalformedJson
        }
        val vm = viewModel(importExportService = svc)

        vm.onImportFilePicked(mockk<Uri>())  // sets importResult
        vm.onImportCancelled()

        assertNull(vm.importResult.value)
    }
}
