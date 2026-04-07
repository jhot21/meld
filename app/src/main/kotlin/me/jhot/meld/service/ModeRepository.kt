package me.jhot.meld.service

import me.jhot.meld.data.db.dao.ActiveModeDao
import me.jhot.meld.data.db.dao.ModeDao
import me.jhot.meld.data.model.ActiveMode
import me.jhot.meld.data.model.Mode
import me.jhot.meld.domain.ModeResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ModeRepository(
    private val modeDao: ModeDao,
    private val activeModeDao: ActiveModeDao,
    private val settingsApplier: SettingsApplier,
    scope: CoroutineScope,
) {

    private val applyLock = Mutex()

    /** Emits combined list of all modes with their current active state for UI observation. */
    val modesWithActiveState: StateFlow<List<Pair<Mode, Boolean>>> =
        combine(modeDao.getAll(), activeModeDao.getActiveModeIds()) { modes, activeIds ->
            modes.map { mode -> mode to (mode.id in activeIds) }
        }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    /**
     * Returns the mode with [id] from the current in-memory snapshot, or null if not yet loaded
     * or not found. Synchronous — no coroutine needed.
     */
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
        val (allModes, activeIds) = combine(
            modeDao.getAll(),
            activeModeDao.getActiveModeIds()
        ) { modes, ids -> modes to ids }.first()
        val resolved = ModeResolver.resolve(allModes, activeIds)
        settingsApplier.apply(resolved)
    }

    // ---- Mode CRUD ----------------------------------------------------------

    suspend fun insertMode(mode: Mode): Long = modeDao.insert(mode)

    suspend fun updateMode(mode: Mode) {
        modeDao.update(mode)
        resolveAndApply()
    }

    suspend fun deleteMode(mode: Mode) {
        activeModeDao.deleteByModeId(mode.id)
        modeDao.deleteSafe(mode)
        resolveAndApply()
    }

    fun getAllModes(): Flow<List<Mode>> = modeDao.getAll()

    suspend fun importModes(modes: List<Mode>) {
        modeDao.replaceByName(modes)
        resolveAndApply()
    }
}
