package me.jhot.meld.service

import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import me.jhot.meld.data.model.Mode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ModeStateQueryTest {

    private val scope = TestScope(UnconfinedTestDispatcher())
    private val store = FakeStore()

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun query(enabled: Boolean = true) = ModeStateQuery(
        ModeRepository(store.modeDao, store.activeDao, store.groupDao, mockk(relaxed = true), scope),
        isSharingEnabled = { enabled },
    )

    @Test
    fun modesPath_returnsNonDefaultNamesAlphabetical() = runTest {
        store.modes.value = listOf(
            Mode(id = 1, name = "Base", isDefault = true, priority = 0),
            Mode(id = 2, name = "Zed", priority = 1),
            Mode(id = 3, name = "Alpha", priority = 2),
        )
        assertEquals(listOf("Alpha", "Zed"), query().names("modes"))
    }

    @Test
    fun activePath_returnsEffectiveOnly() = runTest {
        val low = store.addMode("Low", 10)
        val high = store.addMode("High", 20)
        store.addMode("Other", 5)
        val g = store.addGroup("g")
        store.join(low, g)
        store.join(high, g)
        store.active.value = listOf(low.id, high.id)
        assertEquals(listOf("High"), query().names("active"))
        assertEquals(listOf("High", "Low", "Other"), query().names("modes"))
    }

    @Test
    fun emptyActive_returnsEmptyList_notNull() = runTest {
        store.addMode("A", 1)
        assertEquals(emptyList<String>(), query().names("active"))
    }

    @Test
    fun unknownPath_returnsNull() = runTest {
        assertNull(query().names("settings"))
    }

    @Test
    fun namesWithCommasQuotesUnicodeAndSpaces_passThroughUnchanged() = runTest {
        val odd = listOf(" lead", "a,b", "say \"hi\"", "café ☕", "trail ")
        odd.forEachIndexed { i, n -> store.addMode(n, i) }
        assertEquals(odd.sorted(), query().names("modes"))
    }

    @Test
    fun sharingDisabled_throwsSecurityException() = runTest {
        val result = runCatching { query(enabled = false).names("modes") }
        assertEquals(SecurityException::class, result.exceptionOrNull()!!::class)
    }
}
