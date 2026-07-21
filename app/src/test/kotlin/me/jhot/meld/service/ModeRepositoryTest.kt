package me.jhot.meld.service

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import me.jhot.meld.data.db.dao.ActiveModeDao
import me.jhot.meld.data.db.dao.ExclusivityGroupDao
import me.jhot.meld.data.db.dao.ModeDao
import me.jhot.meld.data.model.Mode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ModeRepositoryTest {

    private fun mode(id: Long, name: String) =
        Mode(id = id, name = name, priority = 50)

    private fun repository(
        modes: List<Mode>,
        scope: kotlinx.coroutines.CoroutineScope,
    ): ModeRepository {
        val modeDao = mockk<ModeDao> {
            every { getAll() } returns flowOf(modes)
        }
        val activeModeDao = mockk<ActiveModeDao>(relaxed = true) {
            every { getActiveModeIds() } returns flowOf(emptySet())
        }
        val exclusivityGroupDao = mockk<ExclusivityGroupDao>(relaxed = true) {
            every { getAllCrossRefs() } returns flowOf(emptyList())
        }
        return ModeRepository(modeDao, activeModeDao, exclusivityGroupDao, mockk(relaxed = true), scope)
    }

    @Test
    fun getModeByIdNow_returnsMode_whenPresentInStateFlow() = runTest(UnconfinedTestDispatcher()) {
        val mode = mode(1L, "Focus")
        val repo = repository(listOf(mode), this)
        assertEquals(mode, repo.getModeByIdNow(1L))
    }

    @Test
    fun getModeByIdNow_returnsNull_whenIdNotPresent() = runTest(UnconfinedTestDispatcher()) {
        val repo = repository(listOf(mode(1L, "Focus")), this)
        assertNull(repo.getModeByIdNow(999L))
    }

    @Test
    fun getModeByIdNow_returnsNull_whenNoModesLoaded() = runTest(UnconfinedTestDispatcher()) {
        val repo = repository(emptyList(), this)
        assertNull(repo.getModeByIdNow(1L))
    }
}
