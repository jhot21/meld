package me.jhot.meld.ui.modeEditor

import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import me.jhot.meld.data.model.Mode
import me.jhot.meld.data.model.ModeType
import me.jhot.meld.service.ModeRepository
import me.jhot.meld.service.PermissionChecker
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ModeEditorViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun mode(id: Long, name: String) =
        Mode(id = id, name = name, type = ModeType.PRIMARY, priority = 50)

    private fun viewModel(modes: List<Mode> = emptyList()): ModeEditorViewModel {
        val repository = mockk<ModeRepository> {
            every { getAllModes() } returns flowOf(modes)
            every { getModeByIdNow(any()) } answers { modes.find { it.id == firstArg<Long>() } }
        }
        val permissionChecker = mockk<PermissionChecker> {
            every { canWriteSecureSettings() } returns false
        }
        return ModeEditorViewModel(repository, permissionChecker, maxMediaVolume = 15, maxNotificationVolume = 7)
    }

    @Test
    fun nameConflict_isFalse_whenNameFieldIsBlank() {
        val vm = viewModel(modes = listOf(mode(1L, "Work")))
        // draft starts with name = ""
        assertFalse(vm.nameConflict.value)
    }

    @Test
    fun nameConflict_isFalse_whenNewModeNameIsUnique() {
        val vm = viewModel(modes = listOf(mode(1L, "Work")))
        vm.updateName("Home")
        assertFalse(vm.nameConflict.value)
    }

    @Test
    fun nameConflict_isTrue_whenNewModeNameMatchesExisting_exactCase() {
        val vm = viewModel(modes = listOf(mode(1L, "Work")))
        vm.updateName("Work")
        assertTrue(vm.nameConflict.value)
    }

    @Test
    fun nameConflict_isTrue_whenNewModeNameMatchesExisting_differentCase() {
        val vm = viewModel(modes = listOf(mode(1L, "Work")))
        vm.updateName("work")
        assertTrue(vm.nameConflict.value)
    }

    @Test
    fun nameConflict_isFalse_whenEditingModeWithUnchangedName() {
        // loadMode sets draft.id = 1 and draft.name = "Work"
        // same id is excluded from conflict check
        val existing = mode(1L, "Work")
        val vm = viewModel(modes = listOf(existing))
        vm.loadMode(1L)  // completes synchronously: flowOf emits eagerly under UnconfinedTestDispatcher
        assertFalse(vm.nameConflict.value)
    }

    @Test
    fun nameConflict_isTrue_whenEditingModeChangesNameToExistingName() {
        val work = mode(1L, "Work")
        val home = mode(2L, "Home")
        val vm = viewModel(modes = listOf(work, home))
        vm.loadMode(1L)  // completes synchronously: flowOf emits eagerly under UnconfinedTestDispatcher
        vm.updateName("Home")    // trying to rename to existing "Home" (id=2)
        assertTrue(vm.nameConflict.value)
    }

    @Test
    fun nameConflict_isFalse_whenEditingModeChangesNameToNewUniqueName() {
        val work = mode(1L, "Work")
        val vm = viewModel(modes = listOf(work))
        vm.loadMode(1L)  // completes synchronously: flowOf emits eagerly under UnconfinedTestDispatcher
        vm.updateName("Office")
        assertFalse(vm.nameConflict.value)
    }

    @Test
    fun save_doesNotCallRepository_whenNameConflictIsTrue() {
        val repository = mockk<ModeRepository>(relaxed = true) {
            every { getAllModes() } returns flowOf(listOf(mode(1L, "Work")))
        }
        val permissionChecker = mockk<PermissionChecker> {
            every { canWriteSecureSettings() } returns false
        }
        val vm = ModeEditorViewModel(repository, permissionChecker, maxMediaVolume = 15, maxNotificationVolume = 7)
        vm.updateName("Work") // triggers conflict
        vm.save()
        coVerify(exactly = 0) { repository.insertMode(any()) }
        coVerify(exactly = 0) { repository.updateMode(any()) }
    }

    @Test
    fun newMode_defaultsToSecondaryType() {
        val vm = viewModel()
        assertEquals(ModeType.SECONDARY, vm.draft.value.type)
    }

    @Test
    fun updateExclusive_true_setsPrimaryType() {
        val vm = viewModel()
        vm.updateExclusive(true)
        assertEquals(ModeType.PRIMARY, vm.draft.value.type)
    }

    @Test
    fun updateExclusive_false_setsSecondaryType() {
        val vm = viewModel()
        vm.updateExclusive(false)
        assertEquals(ModeType.SECONDARY, vm.draft.value.type)
    }

    @Test
    fun loadMode_populatesDraftSynchronously_whenCachedModeExists() {
        val existing = mode(1L, "Focus")
        val vm = viewModel(modes = listOf(existing))
        vm.loadMode(1L)
        // With getModeByIdNow() returning the mode, draft is set before any coroutine runs
        assertEquals(existing, vm.draft.value)
        assertEquals(false, vm.isNew)
    }
}
