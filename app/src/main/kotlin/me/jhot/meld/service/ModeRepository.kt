package me.jhot.meld.service

import me.jhot.meld.data.db.dao.ActiveModeDao
import me.jhot.meld.data.db.dao.ModeDao
import me.jhot.meld.data.model.ActiveMode
import me.jhot.meld.data.model.Mode
import me.jhot.meld.domain.ModeResolver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

class ModeRepository(
    private val modeDao: ModeDao,
    private val activeModeDao: ActiveModeDao,
    private val settingsApplier: SettingsApplier,
) {
    /** Emits combined list of all modes with their current active state for UI observation. */
    val modesWithActiveState: Flow<List<Pair<Mode, Boolean>>> =
        combine(modeDao.getAll(), activeModeDao.getActiveModeIds()) { modes, activeIds ->
            modes.map { mode -> mode to (mode.id in activeIds) }
        }

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

    suspend fun resolveAndApply() {
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
        modeDao.delete(mode)
        resolveAndApply()
    }

    suspend fun setDefault(modeId: Long) = modeDao.setDefault(modeId)

    fun getAllModes(): Flow<List<Mode>> = modeDao.getAll()
}
