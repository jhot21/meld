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
    suspend fun setDefault(id: Long) {
        getById(id) ?: return
        clearDefault()
        setDefaultById(id)
    }

    @Query("UPDATE modes SET isDefault = 0 WHERE isDefault = 1")
    suspend fun clearDefault()

    @Query("UPDATE modes SET isDefault = 1 WHERE id = :id")
    suspend fun setDefaultById(id: Long)
}
