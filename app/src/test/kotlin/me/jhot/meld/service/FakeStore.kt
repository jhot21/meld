package me.jhot.meld.service

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import me.jhot.meld.data.db.dao.ActiveModeDao
import me.jhot.meld.data.db.dao.ExclusivityGroupDao
import me.jhot.meld.data.db.dao.ModeDao
import me.jhot.meld.data.model.ActiveMode
import me.jhot.meld.data.model.ExclusivityGroup
import me.jhot.meld.data.model.Mode
import me.jhot.meld.data.model.ModeExclusivityGroupCrossRef
import me.jhot.meld.data.model.ModeSettings

/** In-memory stand-ins for the three DAOs, so tests can exercise real mutation paths. */
internal class FakeStore {
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
