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

/** In-memory stand-ins for the three DAOs, so tests can exercise real mutation paths. */
private class FakeStore {
    val modes = MutableStateFlow<List<Mode>>(emptyList())
    val active = MutableStateFlow<List<Long>>(emptyList())
    val groups = MutableStateFlow<List<ExclusivityGroup>>(emptyList())
    val refs = MutableStateFlow<List<ModeExclusivityGroupCrossRef>>(emptyList())
    private var nextId = 1L

    val modeDao = object : ModeDao {
        override fun getAll(): Flow<List<Mode>> = modes
        override suspend fun getById(id: Long) = modes.value.firstOrNull { it.id == id }
        override suspend fun getByName(name: String) = modes.value.firstOrNull { it.name == name }
        override suspend fun insert(mode: Mode): Long {
            // Mirrors OnConflictStrategy.REPLACE on the unique name index: a clash gets a new id.
            val id = if (mode.id != 0L) mode.id else nextId++
            modes.value = modes.value.filter { it.name != mode.name && it.id != id } + mode.copy(id = id)
            return id
        }
        override suspend fun update(mode: Mode) {
            modes.value = modes.value.map { if (it.id == mode.id) mode else it }
        }
        override suspend fun delete(mode: Mode) {
            modes.value = modes.value.filter { it.id != mode.id }
        }
    }

    val activeDao = object : ActiveModeDao {
        override fun getAll(): Flow<List<ActiveMode>> = active.map { ids -> ids.map { ActiveMode(it) } }
        override fun getActiveModeIdList(): Flow<List<Long>> = active
        override suspend fun insert(activeMode: ActiveMode) {
            if (activeMode.modeId !in active.value) active.value = active.value + activeMode.modeId
        }
        override suspend fun deleteByModeId(modeId: Long) {
            active.value = active.value.filter { it != modeId }
        }
        override suspend fun clearAll() {
            active.value = emptyList()
        }
    }

    val groupDao = object : ExclusivityGroupDao {
        override fun getAllGroups(): Flow<List<ExclusivityGroup>> = groups
        override suspend fun getGroupsForModeOnce(modeId: Long): List<ExclusivityGroup> {
            val ids = refs.value.filter { it.modeId == modeId }.map { it.groupId }
            return groups.value.filter { it.id in ids }
        }
        override fun getAllCrossRefs(): Flow<List<ModeExclusivityGroupCrossRef>> = refs
        override suspend fun getGroupByName(name: String) =
            groups.value.firstOrNull { it.name.equals(name, ignoreCase = true) }
        override suspend fun insertGroup(group: ExclusivityGroup): Long {
            val id = if (group.id != 0L) group.id else nextId++
            groups.value = groups.value + group.copy(id = id)
            return id
        }
        override suspend fun renameGroup(groupId: Long, name: String) {
            groups.value = groups.value.map { if (it.id == groupId) it.copy(name = name) else it }
        }
        override suspend fun deleteGroupById(groupId: Long) {
            groups.value = groups.value.filter { it.id != groupId }
        }
        override suspend fun deleteCrossRefsForGroup(groupId: Long) {
            refs.value = refs.value.filter { it.groupId != groupId }
        }
        override suspend fun deleteCrossRefsForMode(modeId: Long) {
            refs.value = refs.value.filter { it.modeId != modeId }
        }
        override suspend fun insertCrossRef(crossRef: ModeExclusivityGroupCrossRef) {
            refs.value = refs.value + crossRef
        }
        override suspend fun deleteGroupsWithNoMembers() {
            val used = refs.value.map { it.groupId }.toSet()
            groups.value = groups.value.filter { it.id in used }
        }
    }

    fun addMode(name: String, priority: Int): Mode {
        val id = nextId++
        val mode = Mode(id = id, name = name, priority = priority, settings = ModeSettings())
        modes.value = modes.value + mode
        return mode
    }

    fun addGroup(name: String): Long {
        val id = nextId++
        groups.value = groups.value + ExclusivityGroup(id = id, name = name)
        return id
    }

    fun join(mode: Mode, groupId: Long) {
        refs.value = refs.value + ModeExclusivityGroupCrossRef(mode.id, groupId)
    }
}

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
