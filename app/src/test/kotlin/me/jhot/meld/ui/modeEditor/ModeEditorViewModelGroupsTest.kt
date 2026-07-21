package me.jhot.meld.ui.modeEditor

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import me.jhot.meld.data.model.ExclusivityGroup
import me.jhot.meld.data.model.Mode
import me.jhot.meld.service.ModeRepository
import me.jhot.meld.service.PermissionChecker
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ModeEditorViewModelGroupsTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() { Dispatchers.setMain(testDispatcher) }

    @After
    fun teardown() { Dispatchers.resetMain() }

    private fun viewModel(
        modes: List<Mode> = emptyList(),
        groups: List<ExclusivityGroup> = emptyList(),
        repository: ModeRepository = mockk {
            every { getAllModes() } returns flowOf(modes)
            every { getModeByIdNow(any()) } answers { modes.find { it.id == firstArg<Long>() } }
            every { getAllGroups() } returns flowOf(groups)
        },
    ) = ModeEditorViewModel(
        repository,
        mockk<PermissionChecker> { every { canWriteSecureSettings() } returns false },
        maxMediaVolume = 15,
        maxNotificationVolume = 7,
    )

    @Test
    fun draftGroupIds_startsEmpty_forNewMode() {
        val vm = viewModel()
        assertTrue(vm.draftGroupIds.value.isEmpty())
    }

    @Test
    fun toggleGroup_addsThenRemovesGroupId() {
        val vm = viewModel()
        vm.toggleGroup(1L)
        assertEquals(setOf(1L), vm.draftGroupIds.value)
        vm.toggleGroup(1L)
        assertTrue(vm.draftGroupIds.value.isEmpty())
    }

    @Test
    fun loadMode_populatesDraftGroupIds() {
        val existing = Mode(id = 1L, name = "Work", priority = 50)
        val repository = mockk<ModeRepository> {
            every { getAllModes() } returns flowOf(listOf(existing))
            every { getModeByIdNow(1L) } returns existing
            every { getAllGroups() } returns flowOf(emptyList())
            coEvery { getGroupsForMode(1L) } returns listOf(ExclusivityGroup(id = 100L, name = "Context"))
        }
        val vm = viewModel(repository = repository)
        vm.loadMode(1L)
        assertEquals(setOf(100L), vm.draftGroupIds.value)
    }

    @Test
    fun createAndJoinGroup_createsGroupThenTogglesItOn() {
        val repository = mockk<ModeRepository>(relaxed = true) {
            every { getAllModes() } returns flowOf(emptyList())
            every { getAllGroups() } returns flowOf(emptyList())
            coEvery { getOrCreateGroupByName("Context") } returns 100L
        }
        val vm = viewModel(repository = repository)
        vm.createAndJoinGroup("Context")
        coVerify { repository.getOrCreateGroupByName("Context") }
        assertEquals(setOf(100L), vm.draftGroupIds.value)
    }

    @Test
    fun save_persistsModeThenCallsUpdateModeGroups() {
        val repository = mockk<ModeRepository>(relaxed = true) {
            every { getAllModes() } returns flowOf(emptyList())
            every { getAllGroups() } returns flowOf(emptyList())
            coEvery { insertMode(any()) } returns 42L
        }
        val vm = viewModel(repository = repository)
        vm.updateName("Work")
        vm.toggleGroup(5L)
        vm.save()
        coVerify { repository.updateModeGroups(42L, listOf(5L)) }
    }
}
