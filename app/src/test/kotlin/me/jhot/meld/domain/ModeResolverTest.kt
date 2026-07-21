package me.jhot.meld.domain

import me.jhot.meld.data.model.*
import org.junit.Assert.*
import org.junit.Test

class ModeResolverTest {

    private fun mode(
        id: Long,
        priority: Int,
        isDefault: Boolean = false,
        settings: ModeSettings = ModeSettings(),
    ) = Mode(id = id, name = "mode$id", isDefault = isDefault, priority = priority, settings = settings)

    @Test
    fun noModesAtAll_returnsEmptySettings() {
        val result = ModeResolver.resolve(emptyList(), emptySet(), emptyMap())
        assertEquals(ModeSettings(), result)
    }

    @Test
    fun ungroupedMode_alwaysMergesIn() {
        val home = mode(1, 10, settings = ModeSettings(volumeMedia = 7, darkMode = true))
        val result = ModeResolver.resolve(listOf(home), setOf(1L), emptyMap())
        assertEquals(7, result.volumeMedia)
        assertEquals(true, result.darkMode)
    }

    @Test
    fun twoModesInSameGroup_highestPriorityWins() {
        val home = mode(1, 10, settings = ModeSettings(volumeMedia = 5, darkMode = true))
        val work = mode(2, 50, settings = ModeSettings(volumeMedia = 8))
        val groups = mapOf(1L to listOf(100L), 2L to listOf(100L))
        val result = ModeResolver.resolve(listOf(home, work), setOf(1L, 2L), groups)
        assertEquals(8, result.volumeMedia)
        assertNull(result.darkMode)
    }

    @Test
    fun modesInDifferentGroups_bothIncluded() {
        val loud = mode(1, 50, settings = ModeSettings(ringerMode = RingerMode.SOUND))
        val work = mode(2, 50, settings = ModeSettings(darkMode = false))
        val groups = mapOf(1L to listOf(100L), 2L to listOf(200L))
        val result = ModeResolver.resolve(listOf(loud, work), setOf(1L, 2L), groups)
        assertEquals(RingerMode.SOUND, result.ringerMode)
        assertEquals(false, result.darkMode)
    }

    @Test
    fun modeInTwoGroups_winsOne_losesOther_isIncluded() {
        val home = mode(1, 10, settings = ModeSettings(volumeMedia = 5))
        val work = mode(2, 50, settings = ModeSettings(volumeMedia = 8))
        val loud = mode(3, 30, settings = ModeSettings(ringerMode = RingerMode.SOUND, darkMode = true))
        val groups = mapOf(1L to listOf(100L), 2L to listOf(100L), 3L to listOf(100L, 200L))
        val result = ModeResolver.resolve(listOf(home, work, loud), setOf(1L, 2L, 3L), groups)
        assertEquals(8, result.volumeMedia)
        assertEquals(RingerMode.SOUND, result.ringerMode)
        assertEquals(true, result.darkMode)
    }

    @Test
    fun modeInTwoGroups_losesBoth_isExcluded() {
        val work = mode(1, 50, settings = ModeSettings(volumeMedia = 8))
        val loud = mode(2, 90, settings = ModeSettings(ringerMode = RingerMode.SOUND))
        val loser = mode(3, 10, settings = ModeSettings(darkMode = true))
        val groups = mapOf(1L to listOf(100L), 2L to listOf(200L), 3L to listOf(100L, 200L))
        val result = ModeResolver.resolve(listOf(work, loud, loser), setOf(1L, 2L, 3L), groups)
        assertNull(result.darkMode)
    }

    @Test
    fun equalPriority_ungroupedModeWinsTieOverGroupedMode() {
        val grouped = mode(1, 50, settings = ModeSettings(darkMode = false))
        val ungrouped = mode(2, 50, settings = ModeSettings(darkMode = true))
        val groups = mapOf(1L to listOf(100L))
        val result = ModeResolver.resolve(listOf(grouped, ungrouped), setOf(1L, 2L), groups)
        assertEquals(true, result.darkMode)
    }

    @Test
    fun defaultMode_alwaysBase_withNoActiveModes() {
        val default = mode(1, 0, isDefault = true, settings = ModeSettings(darkMode = false, brightness = 100))
        val result = ModeResolver.resolve(listOf(default), emptySet(), emptyMap())
        assertEquals(false, result.darkMode)
        assertEquals(100, result.brightness)
    }

    @Test
    fun defaultMode_overwrittenByActiveGroupedMode() {
        val default = mode(1, 0, isDefault = true, settings = ModeSettings(darkMode = false, brightness = 100))
        val work = mode(2, 50, settings = ModeSettings(darkMode = true))
        val groups = mapOf(2L to listOf(100L))
        val result = ModeResolver.resolve(listOf(default, work), setOf(2L), groups)
        assertEquals(true, result.darkMode)
        assertEquals(100, result.brightness)
    }

    @Test
    fun defaultMode_idInActiveModeIds_stillExcludedFromWorkingSet() {
        val default = mode(1, 0, isDefault = true, settings = ModeSettings(brightness = 80))
        val result = ModeResolver.resolve(listOf(default), setOf(1L), emptyMap())
        assertEquals(80, result.brightness)
        assertNull(result.darkMode)
    }

    @Test
    fun defaultMode_missingFromList_returnsEmptyBase() {
        val work = mode(1, 50, settings = ModeSettings(volumeMedia = 8))
        val result = ModeResolver.resolve(listOf(work), setOf(1L), emptyMap())
        assertEquals(8, result.volumeMedia)
    }

    @Test
    fun keyboardVibration_higherPriorityModeInSameGroupWins() {
        val low = mode(1, 10, settings = ModeSettings(keyboardVibration = false))
        val high = mode(2, 50, settings = ModeSettings(keyboardVibration = true))
        val groups = mapOf(1L to listOf(100L), 2L to listOf(100L))
        val result = ModeResolver.resolve(listOf(low, high), setOf(1L, 2L), groups)
        assertEquals(true, result.keyboardVibration)
    }
}
