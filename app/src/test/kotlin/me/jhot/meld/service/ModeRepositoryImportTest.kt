package me.jhot.meld.service

import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import me.jhot.meld.data.db.dao.ActiveModeDao
import me.jhot.meld.data.db.dao.ModeDao
import me.jhot.meld.data.model.Mode
import me.jhot.meld.data.model.ModeSettings
import me.jhot.meld.data.model.ModeType
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ModeRepositoryImportTest {

    private fun repository(
        modeDao: ModeDao,
        activeModeDao: ActiveModeDao = mockk(relaxed = true) {
            every { getActiveModeIds() } returns flowOf(emptySet())
        },
        settingsApplier: SettingsApplier = mockk(relaxed = true),
    ) = ModeRepository(modeDao, activeModeDao, settingsApplier, CoroutineScope(UnconfinedTestDispatcher()))

    @Test
    fun importModes_callsReplaceByName_withConvertedModeEntities() = runTest {
        val modeDao = mockk<ModeDao>(relaxed = true) {
            every { getAll() } returns flowOf(emptyList())
        }
        val repo = repository(modeDao)

        val modes = listOf(
            Mode(name = "Work", type = ModeType.PRIMARY, priority = 50, settings = ModeSettings(brightness = 200)),
            Mode(name = "Default", type = ModeType.DEFAULT, priority = 0),
        )
        repo.importModes(modes)

        coVerify {
            modeDao.replaceByName(match { list ->
                list.size == 2 &&
                list.any { it.name == "Work" && it.priority == 50 } &&
                list.any { it.name == "Default" && it.type == ModeType.DEFAULT }
            })
        }
    }

    @Test
    fun importModes_triggersResolveAndApply() = runTest {
        val modeDao = mockk<ModeDao>(relaxed = true) {
            every { getAll() } returns flowOf(emptyList())
        }
        val settingsApplier = mockk<SettingsApplier>(relaxed = true)
        val repo = repository(modeDao, settingsApplier = settingsApplier)

        repo.importModes(listOf(Mode(name = "Work", type = ModeType.PRIMARY, priority = 50)))

        coVerify { settingsApplier.apply(any()) }
    }
}
