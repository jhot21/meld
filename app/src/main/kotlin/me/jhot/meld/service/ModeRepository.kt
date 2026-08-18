package me.jhot.meld.service

import me.jhot.meld.data.db.dao.ActiveModeDao
import me.jhot.meld.data.db.dao.ExclusivityGroupDao
import me.jhot.meld.data.db.dao.ModeDao
import me.jhot.meld.data.model.ActiveMode
import me.jhot.meld.data.model.ExclusivityGroup
import me.jhot.meld.data.model.Mode
import me.jhot.meld.domain.ModeResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ModeRepository(
    private val modeDao: ModeDao,
    private val activeModeDao: ActiveModeDao,
    private val exclusivityGroupDao: ExclusivityGroupDao,
    private val settingsApplier: SettingsApplier,
    scope: CoroutineScope,
) {

    private val applyLock = Mutex()

    val modesWithActiveState: StateFlow<List<Pair<Mode, Boolean>>> =
        combine(modeDao.getAll(), activeModeDao.getActiveModeIds()) { modes, activeIds ->
            modes.map { mode -> mode to (mode.id in activeIds) }
        }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    fun getModeByIdNow(id: Long): Mode? =
        modesWithActiveState.value.firstOrNull { (mode, _) -> mode.id == id }?.first

    suspend fun addToContext(modeName: String) {
        val mode = modeDao.getByName(modeName) ?: return
        activeModeDao.insert(ActiveMode(modeId = mode.id))
        resolveAndApply()
    }

    suspend fun removeFromContext(modeName: String) {
        val mode = modeDao.getByName(modeName) ?: return
        activeModeDao.deleteByModeId(mode.id)
        resolveAndApply()
    }

    suspend fun setModeActive(modeId: Long, active: Boolean) {
        if (active) {
            activeModeDao.insert(ActiveMode(modeId = modeId))
        } else {
            activeModeDao.deleteByModeId(modeId)
        }
        resolveAndApply()
    }

    suspend fun resolveAndApply() = applyLock.withLock {
        val (allModes, activeIds, crossRefs) = combine(
            modeDao.getAll(),
            activeModeDao.getActiveModeIds(),
            exclusivityGroupDao.getAllCrossRefs(),
        ) { modes, ids, refs -> Triple(modes, ids, refs) }.first()
        val modeGroupIds = crossRefs.groupBy({ it.modeId }, { it.groupId })
        val resolved = ModeResolver.resolve(allModes, activeIds, modeGroupIds)
        settingsApplier.apply(resolved)
    }

    suspend fun insertMode(mode: Mode): Long = modeDao.insert(mode)

    suspend fun updateMode(mode: Mode) {
        modeDao.update(mode)
        resolveAndApply()
    }

    suspend fun updateModeGroups(modeId: Long, groupIds: List<Long>) {
        exclusivityGroupDao.setModeGroups(modeId, groupIds)
        resolveAndApply()
    }

    suspend fun deleteMode(mode: Mode) {
        activeModeDao.deleteByModeId(mode.id)
        exclusivityGroupDao.setModeGroups(mode.id, emptyList())
        modeDao.deleteSafe(mode)
        resolveAndApply()
    }

    fun getAllModes(): Flow<List<Mode>> = modeDao.getAll()

    fun getAllGroups(): Flow<List<ExclusivityGroup>> = exclusivityGroupDao.getAllGroups()

    fun getGroupIdsPerMode(): Flow<Map<Long, List<Long>>> =
        exclusivityGroupDao.getAllCrossRefs().map { refs -> refs.groupBy({ it.modeId }, { it.groupId }) }

    suspend fun getGroupsForMode(modeId: Long): List<ExclusivityGroup> =
        exclusivityGroupDao.getGroupsForModeOnce(modeId)

    suspend fun getOrCreateGroupByName(name: String): Long =
        exclusivityGroupDao.getOrCreateGroupByName(name)

    suspend fun renameGroup(groupId: Long, name: String) = exclusivityGroupDao.renameGroup(groupId, name)

    suspend fun deleteGroup(groupId: Long) = exclusivityGroupDao.deleteGroup(groupId)

    suspend fun importModes(modes: List<Mode>, modeGroupNames: Map<String, List<String>> = emptyMap()) {
        modeDao.replaceByName(modes)
        for (mode in modes) {
            val groupNames = modeGroupNames[mode.name] ?: continue
            val insertedMode = modeDao.getByName(mode.name) ?: continue
            val groupIds = groupNames.map { exclusivityGroupDao.getOrCreateGroupByName(it) }
            exclusivityGroupDao.setModeGroups(insertedMode.id, groupIds)
        }
        resolveAndApply()
    }
}
