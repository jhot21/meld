package me.jhot.meld.data.db.dao

import androidx.room.*
import me.jhot.meld.data.model.Mode
import me.jhot.meld.data.model.ModeType
import kotlinx.coroutines.flow.Flow

@Dao
interface ModeDao {
    @Query("SELECT * FROM modes ORDER BY priority DESC")
    fun getAll(): Flow<List<Mode>>

    @Query("SELECT * FROM modes WHERE id = :id")
    suspend fun getById(id: Long): Mode?

    @Query("SELECT * FROM modes WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): Mode?

    @Query("SELECT * FROM modes WHERE type = 'DEFAULT' LIMIT 1")
    fun getDefaultMode(): Flow<Mode?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(mode: Mode): Long

    @Update
    suspend fun update(mode: Mode)

    @Delete
    suspend fun delete(mode: Mode)

    @Transaction
    suspend fun deleteSafe(mode: Mode) {
        check(mode.type != ModeType.DEFAULT) { "Cannot delete the DEFAULT mode" }
        delete(mode)
    }
}
