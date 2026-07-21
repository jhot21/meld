package me.jhot.meld.ui.globalSettings

import android.net.Uri
import com.google.gson.Gson
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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
class GlobalSettingsViewModelImportExportTest {

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

    private fun viewModel(
        importExportService: ImportExportService = mockk(relaxed = true),
        modeRepository: ModeRepository = mockk<ModeRepository> {
            every { getAllModes() } returns flowOf(emptyList())
            every { getAllGroups() } returns flowOf(emptyList())
        },
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
        val json = """{"exportVersion":2,"exportedAt":0,"modes":[]}"""
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
        val importedModes = listOf(ModeExport("Work", isDefault = false, priority = 50, settings = ModeSettings()))
        val svc = mockk<ImportExportService> {
            coEvery { parseImport(any()) } returns ImportResult.Ready(importedModes)
        }
        val vm = viewModel(importExportService = svc)

        vm.onImportFilePicked(mockk<Uri>())

        assertTrue(vm.importResult.value is ImportResult.Ready)
    }

    @Test
    fun onImportFilePicked_setsImportResult_toConflictsDetected() = runTest {
        val importedModes = listOf(ModeExport("Work", isDefault = false, priority = 50, settings = ModeSettings()))
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
        val importedModes = listOf(ModeExport("Work", isDefault = false, priority = 50, settings = ModeSettings(brightness = 200)))
        val svc = mockk<ImportExportService> {
            coEvery { parseImport(any()) } returns ImportResult.Ready(importedModes)
        }
        val repo = mockk<ModeRepository>(relaxed = true) {
            every { getAllModes() } returns flowOf(emptyList())
        }
        val vm = viewModel(importExportService = svc, modeRepository = repo)

        vm.onImportFilePicked(mockk<Uri>())
        vm.onImportConfirmed()

        coVerify {
            repo.importModes(match { list ->
                list.size == 1 && list[0].name == "Work" && list[0].settings.brightness == 200
            }, any())
        }
        assertNull(vm.importResult.value)
    }

    @Test
    fun onImportConfirmed_worksWithConflictsDetectedResult() = runTest {
        val importedModes = listOf(ModeExport("Work", isDefault = false, priority = 50, settings = ModeSettings()))
        val svc = mockk<ImportExportService> {
            coEvery { parseImport(any()) } returns ImportResult.ConflictsDetected(importedModes, listOf("Work"))
        }
        val repo = mockk<ModeRepository>(relaxed = true) {
            every { getAllModes() } returns flowOf(emptyList())
        }
        val vm = viewModel(importExportService = svc, modeRepository = repo)

        vm.onImportFilePicked(mockk<Uri>())
        vm.onImportConfirmed()

        coVerify { repo.importModes(match { it.size == 1 && it[0].name == "Work" }, any()) }
        assertNull(vm.importResult.value)
    }

    @Test
    fun onImportCancelled_clearsResult() = runTest {
        val svc = mockk<ImportExportService> {
            coEvery { parseImport(any()) } returns ImportResult.MalformedJson
        }
        val vm = viewModel(importExportService = svc)

        vm.onImportFilePicked(mockk<Uri>())
        vm.onImportCancelled()

        assertNull(vm.importResult.value)
    }

    @Test
    fun allModes_includesDefaultAndPrimaryModes() = runTest(testDispatcher) {
        val modes = listOf(
            Mode(id = 1, name = "Default", isDefault = true, priority = 0),
            Mode(id = 2, name = "Work", priority = 50),
        )
        val repo = mockk<ModeRepository> {
            every { getAllModes() } returns flowOf(modes)
            every { getAllGroups() } returns flowOf(emptyList())
        }
        val vm = viewModel(modeRepository = repo)
        assertEquals(modes, vm.allModes.value)
    }

    @Test
    fun onIndividualExportModeSelected_emitsFileNameAndJson() = runTest(testDispatcher) {
        val mode = Mode(id = 1, name = "Work Mode", priority = 50)
        val json = """{"exportVersion":2,"exportedAt":0,"modes":[{"name":"Work Mode","isDefault":false,"priority":50,"settings":{},"groupNames":[]}]}"""
        val svc = mockk<ImportExportService> {
            coEvery { exportSingleMode(mode) } returns json
            every { singleModeExportFileName("Work Mode") } returns "meld-export-Work_Mode-2026-04-06.json"
        }
        val vm = viewModel(importExportService = svc)

        var emitted: Pair<String, String>? = null
        val job = launch { vm.singleModeExportReady.collect { emitted = it } }

        vm.onIndividualExportModeSelected(mode)

        job.cancel()
        assertEquals("meld-export-Work_Mode-2026-04-06.json" to json, emitted)
    }

    @Test
    fun onIndividualExportModeSelected_emittedJson_containsOnlySelectedMode() = runTest(testDispatcher) {
        val mode = Mode(id = 2, name = "Work", priority = 50)
        val singleModeJson = """{"exportVersion":2,"exportedAt":0,"modes":[{"name":"Work","isDefault":false,"priority":50,"settings":{},"groupNames":[]}]}"""
        val svc = mockk<ImportExportService> {
            coEvery { exportSingleMode(mode) } returns singleModeJson
            every { singleModeExportFileName(any()) } returns "meld-export-Work-2026-04-06.json"
        }
        val vm = viewModel(importExportService = svc)

        var emitted: Pair<String, String>? = null
        val job = launch { vm.singleModeExportReady.collect { emitted = it } }

        vm.onIndividualExportModeSelected(mode)

        job.cancel()
        val parsed = Gson().fromJson(emitted!!.second, Map::class.java)
        assertEquals(1.0, (parsed["modes"] as List<*>).size.toDouble(), 0.0)
    }
}
