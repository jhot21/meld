package me.jhot.meld.service

import me.jhot.meld.data.model.Mode
import me.jhot.meld.data.model.ModeType
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationBodyTest {

    private fun mode(id: Long, name: String, type: ModeType, priority: Int) =
        Mode(id = id, name = name, type = type, priority = priority)

    @Test
    fun noModesActive_returnsOnlyDefaultMessage() {
        val modes = listOf(
            mode(1, "Home", ModeType.PRIMARY, 10) to false,
            mode(2, "Work", ModeType.PRIMARY, 20) to false,
        )
        assertEquals("Only default mode active", buildNotificationBody(modes))
    }

    @Test
    fun emptyList_returnsOnlyDefaultMessage() {
        assertEquals("Only default mode active", buildNotificationBody(emptyList()))
    }

    @Test
    fun singleModeActive_usesSingularLabel() {
        val modes = listOf(
            mode(1, "Home", ModeType.PRIMARY, 10) to true,
        )
        assertEquals("1 mode active: Home", buildNotificationBody(modes))
    }

    @Test
    fun twoModesActive_usesPluralLabel() {
        val modes = listOf(
            mode(1, "Home", ModeType.PRIMARY, 10) to true,
            mode(2, "Work", ModeType.PRIMARY, 20) to true,
        )
        assertEquals("2 modes active: Work, Home", buildNotificationBody(modes))
    }

    @Test
    fun activeModesOrderedByPriorityDescending() {
        val modes = listOf(
            mode(1, "Low", ModeType.PRIMARY, 5) to true,
            mode(2, "High", ModeType.PRIMARY, 50) to true,
            mode(3, "Mid", ModeType.SECONDARY, 25) to true,
        )
        assertEquals("3 modes active: High, Mid, Low", buildNotificationBody(modes))
    }

    @Test
    fun defaultModeExcluded_evenIfMarkedActive() {
        val modes = listOf(
            mode(1, "Default", ModeType.DEFAULT, 0) to true,
            mode(2, "Work", ModeType.PRIMARY, 20) to true,
        )
        assertEquals("1 mode active: Work", buildNotificationBody(modes))
    }

    @Test
    fun inactiveModeNotIncluded() {
        val modes = listOf(
            mode(1, "Home", ModeType.PRIMARY, 10) to false,
            mode(2, "Work", ModeType.PRIMARY, 20) to true,
        )
        assertEquals("1 mode active: Work", buildNotificationBody(modes))
    }
}
