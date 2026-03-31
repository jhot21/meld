package me.jhot.meld.data.db.dao

import androidx.room.*
import me.jhot.meld.data.model.ActiveMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Dao
interface ActiveModeDao {
    @Query("SELECT * FROM active_modes")
    fun getAll(): Flow<List<ActiveMode>>

    @Query("SELECT modeId FROM active_modes")
    fun getActiveModeIdList(): Flow<List<Long>>

    fun getActiveModeIds(): Flow<Set<Long>> = getActiveModeIdList().map { it.toSet() }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(activeMode: ActiveMode)

    @Query("DELETE FROM active_modes WHERE modeId = :modeId")
    suspend fun deleteByModeId(modeId: Long)

    @Query("DELETE FROM active_modes")
    suspend fun clearAll()
}
