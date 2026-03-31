package me.jhot.meld.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import me.jhot.meld.data.db.dao.ActiveModeDao
import me.jhot.meld.data.model.ActiveMode

@Database(entities = [ActiveMode::class], version = 1, exportSchema = false)
abstract class ActiveDatabase : RoomDatabase() {
    abstract fun activeModeDao(): ActiveModeDao
}
