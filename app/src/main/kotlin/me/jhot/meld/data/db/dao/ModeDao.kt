package me.jhot.meld.data.db.dao

import androidx.room.*
import me.jhot.meld.data.model.Mode
import kotlinx.coroutines.flow.Flow

@Dao
interface ModeDao {
    @Query("SELECT * FROM modes ORDER BY priority DESC")
    fun getAll(): Flow<List<Mode>>

    @Query("SELECT * FROM modes WHERE id = :id")
    suspend fun getById(id: Long): Mode?

    @Query("SELECT * FROM modes WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): Mode?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(mode: Mode): Long

    @Update
    suspend fun update(mode: Mode)

    @Delete
    suspend fun delete(mode: Mode)

    @Transaction
    suspend fun deleteSafe(mode: Mode) {
        check(!mode.isDefault) { "Cannot delete the DEFAULT mode" }
        delete(mode)
    }

    @Transaction
    suspend fun replaceByName(modes: List<Mode>) {
        for (mode in modes) {
            // insert has OnConflictStrategy.REPLACE — a unique-name collision deletes the
            // existing row and inserts a fresh one with a new auto-generated id.
            insert(mode)
        }
    }
}
