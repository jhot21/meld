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
    fun emptyActiveModes_returnsEmptySettings() {
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
    fun noPrimaryActive_defaultPrimaryUsed() {
        val home = mode(1, ModeType.PRIMARY, 10, isDefault = true, settings = ModeSettings(volumeMedia = 7))
        val work = mode(2, ModeType.PRIMARY, 50, settings = ModeSettings(volumeMedia = 8))
        val result = ModeResolver.resolve(listOf(home, work), emptySet())
        assertEquals(7, result.volumeMedia)
    }

    @Test
    fun activePrimaryTakesPrecedenceOverDefault() {
        val home = mode(1, ModeType.PRIMARY, 10, isDefault = true, settings = ModeSettings(volumeMedia = 7))
        val work = mode(2, ModeType.PRIMARY, 50, settings = ModeSettings(volumeMedia = 8))
        val result = ModeResolver.resolve(listOf(home, work), setOf(2L))
        assertEquals(8, result.volumeMedia)
    }

    @Test
    fun defaultPrimarySecondaryPriorityFilter_usesDefaultPriority() {
        val home = mode(1, ModeType.PRIMARY, 10, isDefault = true, settings = ModeSettings(volumeMedia = 7))
        val night = mode(2, ModeType.SECONDARY, 60, settings = ModeSettings(darkMode = true))
        val casual = mode(3, ModeType.SECONDARY, 5, settings = ModeSettings(brightness = 50))
        val result = ModeResolver.resolve(listOf(home, night, casual), setOf(2L, 3L))
        assertEquals(7, result.volumeMedia)
        assertEquals(true, result.darkMode)
        assertNull(result.brightness)
    }
}
