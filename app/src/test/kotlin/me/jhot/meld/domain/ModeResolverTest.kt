package me.jhot.meld.domain

import me.jhot.meld.data.model.*
import org.junit.Assert.*
import org.junit.Test

class ModeResolverTest {

    private fun mode(
        id: Long,
        type: ModeType,
        priority: Int,
        isDefault: Boolean = false,
        settings: ModeSettings = ModeSettings(),
    ) = Mode(id = id, name = "mode$id", type = type, priority = priority, isDefault = isDefault, settings = settings)

    @Test
    fun noModesAtAll_returnsEmptySettings() {
        val result = ModeResolver.resolve(emptyList(), emptySet())
        assertEquals(ModeSettings(), result)
    }

    @Test
    fun singlePrimaryMode_returnsItsSettings() {
        val home = mode(1, ModeType.PRIMARY, 10, settings = ModeSettings(volumeMedia = 7, darkMode = true))
        val result = ModeResolver.resolve(listOf(home), setOf(1L))
        assertEquals(7, result.volumeMedia)
        assertEquals(true, result.darkMode)
    }

    @Test
    fun multiplePrimaries_highestPriorityWins() {
        val home = mode(1, ModeType.PRIMARY, 10, settings = ModeSettings(volumeMedia = 5, darkMode = true))
        val work = mode(2, ModeType.PRIMARY, 50, settings = ModeSettings(volumeMedia = 8))
        val result = ModeResolver.resolve(listOf(home, work), setOf(1L, 2L))
        assertEquals(8, result.volumeMedia)
        assertNull(result.darkMode)
    }

    @Test
    fun secondaryOnlyActive_whenNoPrimary_usesAllSecondaries() {
        val night = mode(1, ModeType.SECONDARY, 60, settings = ModeSettings(darkMode = true))
        val casual = mode(2, ModeType.SECONDARY, 5, settings = ModeSettings(brightness = 100))
        val result = ModeResolver.resolve(listOf(night, casual), setOf(1L, 2L))
        assertEquals(true, result.darkMode)
        assertEquals(100, result.brightness)
    }

    @Test
    fun secondaryBelowWinningPrimaryPriority_excluded() {
        val work = mode(1, ModeType.PRIMARY, 50, settings = ModeSettings(volumeMedia = 8))
        val casual = mode(2, ModeType.SECONDARY, 5, settings = ModeSettings(brightness = 50))
        val result = ModeResolver.resolve(listOf(work, casual), setOf(1L, 2L))
        assertEquals(8, result.volumeMedia)
        assertNull(result.brightness)
    }

    @Test
    fun secondaryAtEqualPriorityBeatsWinningPrimary() {
        val work = mode(1, ModeType.PRIMARY, 50, settings = ModeSettings(darkMode = false))
        val meeting = mode(2, ModeType.SECONDARY, 50, settings = ModeSettings(darkMode = true))
        val result = ModeResolver.resolve(listOf(work, meeting), setOf(1L, 2L))
        assertEquals(true, result.darkMode)
    }

    @Test
    fun secondaryAboveWinningPrimary_included_andWinsOnConflict() {
        val work = mode(1, ModeType.PRIMARY, 50, settings = ModeSettings(volumeMedia = 8, darkMode = false))
        val night = mode(2, ModeType.SECONDARY, 60, settings = ModeSettings(darkMode = true))
        val result = ModeResolver.resolve(listOf(work, night), setOf(1L, 2L))
        assertEquals(8, result.volumeMedia)
        assertEquals(true, result.darkMode)
    }

    @Test
    fun defaultMode_alwaysBase_withNoActiveModes() {
        val default = mode(1, ModeType.DEFAULT, 0, settings = ModeSettings(darkMode = false, brightness = 100))
        val result = ModeResolver.resolve(listOf(default), emptySet())
        assertEquals(false, result.darkMode)
        assertEquals(100, result.brightness)
    }

    @Test
    fun defaultMode_overwrittenByActivePrimary() {
        val default = mode(1, ModeType.DEFAULT, 0, settings = ModeSettings(darkMode = false, brightness = 100))
        val work = mode(2, ModeType.PRIMARY, 50, settings = ModeSettings(darkMode = true))
        val result = ModeResolver.resolve(listOf(default, work), setOf(2L))
        assertEquals(true, result.darkMode)
        assertEquals(100, result.brightness)  // not overridden by work — still comes from default
    }

    @Test
    fun defaultMode_overwrittenByActiveSecondary_whenNoPrimary() {
        val default = mode(1, ModeType.DEFAULT, 0, settings = ModeSettings(brightness = 50))
        val night = mode(2, ModeType.SECONDARY, 60, settings = ModeSettings(darkMode = true))
        val result = ModeResolver.resolve(listOf(default, night), setOf(2L))
        assertEquals(true, result.darkMode)
        assertEquals(50, result.brightness)  // still from default
    }

    @Test
    fun defaultMode_idInActiveModeIds_stillExcludedFromWorkingSet() {
        // Even if the DEFAULT mode's id is passed in activeModeIds, it must not participate
        // in working-set construction (no primary selection, no secondary threshold).
        val default = mode(1, ModeType.DEFAULT, 0, settings = ModeSettings(brightness = 80))
        val result = ModeResolver.resolve(listOf(default), setOf(1L))  // DEFAULT id in active set
        assertEquals(80, result.brightness)   // still the DEFAULT baseline
        assertNull(result.darkMode)           // no other mode contributed
    }

    @Test
    fun defaultMode_missingFromList_returnsEmptyBase() {
        // Guard: if somehow no DEFAULT mode is in allModes, resolver still works
        val work = mode(1, ModeType.PRIMARY, 50, settings = ModeSettings(volumeMedia = 8))
        val result = ModeResolver.resolve(listOf(work), setOf(1L))
        assertEquals(8, result.volumeMedia)
    }

    @Test
    fun keyboardVibration_higherPriorityModeWins() {
        val low = mode(1, ModeType.PRIMARY, 10, settings = ModeSettings(keyboardVibration = false))
        val high = mode(2, ModeType.PRIMARY, 50, settings = ModeSettings(keyboardVibration = true))
        val result = ModeResolver.resolve(listOf(low, high), setOf(1L, 2L))
        assertEquals(true, result.keyboardVibration)
    }

    @Test
    fun keyboardVibration_unsetDoesNotOverrideExplicitValue() {
        val primary = mode(1, ModeType.PRIMARY, 50, settings = ModeSettings(keyboardVibration = false))
        val secondary = mode(2, ModeType.SECONDARY, 50, settings = ModeSettings())
        val result = ModeResolver.resolve(listOf(primary, secondary), setOf(1L, 2L))
        // secondary has keyboardVibration=null → null ?: false = false (primary's value passes through)
        assertEquals(false, result.keyboardVibration)
    }
}
