package me.jhot.meld.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import me.jhot.meld.data.db.dao.ActiveModeDao
import me.jhot.meld.data.db.dao.ExclusivityGroupDao
import me.jhot.meld.data.db.dao.ModeDao
import me.jhot.meld.data.model.ActiveMode
import me.jhot.meld.data.model.ExclusivityGroup
import me.jhot.meld.data.model.Mode
import me.jhot.meld.data.model.ModeExclusivityGroupCrossRef
import me.jhot.meld.data.model.ModeSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ModeRepositoryChangeNotificationTest {

    private val scope = TestScope(UnconfinedTestDispatcher())
    private val store = FakeStore()
    private val sent = mutableListOf<List<String>>()
    private val applier: SettingsApplier = mockk(relaxed = true)

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun repo(settingsApplier: SettingsApplier = applier, notifier: ModesChangedNotifier = ModesChangedNotifier { sent += it }) =
        ModeRepository(store.modeDao, store.activeDao, store.groupDao, settingsApplier, scope, notifier)

    @Test
    fun firstResolveNotifiesOnce_evenWhenEmpty() = runTest {
        val repo = repo()
        repo.resolveAndApply()
        repo.resolveAndApply()
        assertEquals(listOf(emptyList<String>()), sent)
    }

    @Test
    fun activateNotifiesWithName() = runTest {
        store.addMode("Focus", 10)
        repo().addToContext("Focus")
        assertEquals(listOf(listOf("Focus")), sent)
    }

    @Test
    fun reactivateAlreadyActiveDoesNotNotify() = runTest {
        store.addMode("Focus", 10)
        val repo = repo()
        repo.addToContext("Focus")
        repo.addToContext("Focus")
        assertEquals(1, sent.size)
    }

    @Test
    fun deactivateNotifies() = runTest {
        store.addMode("Focus", 10)
        val repo = repo()
        repo.addToContext("Focus")
        repo.removeFromContext("Focus")
        assertEquals(listOf(listOf("Focus"), emptyList()), sent)
    }

    @Test
    fun groupLoserActivationDoesNotNotify() = runTest {
        val low = store.addMode("Low", 10)
        val high = store.addMode("High", 20)
        val g = store.addGroup("g")
        store.join(low, g)
        store.join(high, g)
        val repo = repo()
        repo.addToContext("High")
        repo.addToContext("Low")
        assertEquals(listOf(listOf("High")), sent)
    }

    @Test
    fun priorityEditChangingWinnerNotifies() = runTest {
        val low = store.addMode("Low", 10)
        val high = store.addMode("High", 20)
        val g = store.addGroup("g")
        store.join(low, g)
        store.join(high, g)
        val repo = repo()
        repo.addToContext("Low")
        repo.addToContext("High")
        repo.updateMode(low.copy(priority = 30))
        assertEquals(listOf(listOf("Low"), listOf("High"), listOf("Low")), sent)
    }

    @Test
    fun groupMembershipChangeNotifies() = runTest {
        val a = store.addMode("A", 10)
        val b = store.addMode("B", 20)
        val repo = repo()
        repo.addToContext("A")
        repo.addToContext("B")
        val g = repo.getOrCreateGroupByName("g")
        repo.updateModeGroups(a.id, listOf(g))
        repo.updateModeGroups(b.id, listOf(g))
        assertEquals(listOf(listOf("A"), listOf("A", "B"), listOf("B")), sent)
    }

    @Test
    fun deleteActiveModeNotifies() = runTest {
        store.addMode("A", 10)
        val b = store.addMode("B", 20)
        val repo = repo()
        repo.addToContext("A")
        repo.addToContext("B")
        repo.deleteMode(b)
        assertEquals(listOf("A"), sent.last())
    }

    @Test
    fun importThatChangesEffectiveSetNotifies() = runTest {
        store.addMode("A", 10)
        val repo = repo()
        repo.addToContext("A")
        // REPLACE gives the imported "A" a new id, so the active row goes stale.
        repo.importModes(listOf(Mode(name = "A", priority = 10)))
        assertEquals(listOf(listOf("A"), emptyList()), sent)
    }

    @Test
    fun noopUpdateDoesNotNotify() = runTest {
        val a = store.addMode("A", 10)
        val repo = repo()
        repo.addToContext("A")
        repo.updateMode(a)
        assertEquals(1, sent.size)
    }

    @Test
    fun renameActiveModeNotifies() = runTest {
        val a = store.addMode("A", 10)
        val repo = repo()
        repo.addToContext("A")
        repo.updateMode(a.copy(name = "A2"))
        assertEquals(listOf(listOf("A"), listOf("A2")), sent)
    }

    @Test
    fun renameInactiveModeDoesNotNotify() = runTest {
        store.addMode("A", 10)
        val b = store.addMode("B", 20)
        val repo = repo()
        repo.addToContext("A")
        repo.updateMode(b.copy(name = "B2"))
        assertEquals(listOf(listOf("A")), sent)
    }

    @Test
    fun deleteGroupWithTwoActiveModesNotifies() = runTest {
        val low = store.addMode("Low", 10)
        val high = store.addMode("High", 20)
        val g = store.addGroup("g")
        store.join(low, g)
        store.join(high, g)
        val repo = repo()
        repo.addToContext("Low")
        repo.addToContext("High")
        repo.deleteGroup(g)
        assertEquals(listOf("High", "Low"), sent.last())
    }

    @Test
    fun deleteGroupReappliesSettings() = runTest {
        val low = store.addMode("Low", 10)
        val g = store.addGroup("g")
        store.join(low, g)
        val repo = repo()
        repo.addToContext("Low")
        verify(exactly = 1) { applier.apply(any()) }
        repo.deleteGroup(g)
        verify(exactly = 2) { applier.apply(any()) }
    }

    @Test
    fun notifiesAfterSettingsApplied() = runTest {
        val order = mutableListOf<String>()
        val recordingApplier = mockk<SettingsApplier>()
        every { recordingApplier.apply(any()) } answers { order += "apply" }
        store.addMode("A", 10)
        repo(recordingApplier, ModesChangedNotifier { order += "notify" }).addToContext("A")
        assertEquals(listOf("apply", "notify"), order)
    }

    @Test
    fun noNotificationWhenApplyThrows_andNextResolveStillNotifies() = runTest {
        val failing = mockk<SettingsApplier>()
        var fail = true
        every { failing.apply(any()) } answers { if (fail) throw IllegalStateException("boom") }
        store.addMode("A", 10)
        val repo = repo(failing)
        assertTrue(runCatching { repo.addToContext("A") }.isFailure)
        assertEquals(emptyList<List<String>>(), sent)
        fail = false
        repo.resolveAndApply()
        assertEquals(listOf(listOf("A")), sent)
    }

    @Test
    fun effectiveModeNamesAndAllModeNames_excludeDefault_andAreAlphabetical() = runTest {
        store.modes.value = listOf(
            Mode(id = 1, name = "Base", isDefault = true, priority = 0),
            Mode(id = 2, name = "Zed", priority = 5),
            Mode(id = 3, name = "Alpha", priority = 9),
        )
        store.active.value = listOf(1L, 2L, 3L)
        val repo = repo()
        assertEquals(listOf("Alpha", "Zed"), repo.allModeNames())
        assertEquals(listOf("Alpha", "Zed"), repo.effectiveModeNames())
    }
}
