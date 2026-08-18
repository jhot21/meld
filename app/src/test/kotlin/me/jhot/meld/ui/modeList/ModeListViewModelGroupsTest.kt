package me.jhot.meld.ui.modeList

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ModeListViewModelGroupsTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() { Dispatchers.setMain(testDispatcher) }

    @After
    fun teardown() { Dispatchers.resetMain() }

    @Test
    fun modesWithActiveState_includesGroupsForEachMode() {
        val work = Mode(id = 1L, name = "Work", priority = 50)
        val context = ExclusivityGroup(id = 100L, name = "Context")
        val repository = mockk<ModeRepository> {
            every { modesWithActiveState } returns MutableStateFlow(listOf(work to true))
            every { getAllGroups() } returns flowOf(listOf(context))
            every { getGroupIdsPerMode() } returns flowOf(mapOf(1L to listOf(100L)))
        }
        val permissionChecker = mockk<PermissionChecker>(relaxed = true)
        val vm = ModeListViewModel(repository, permissionChecker)

        val items = vm.modesWithActiveState.value
        assertEquals(1, items.size)
        assertEquals(listOf(context), items[0].groups)
    }
}
