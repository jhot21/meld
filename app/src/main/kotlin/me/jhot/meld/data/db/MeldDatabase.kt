package me.jhot.meld.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import me.jhot.meld.data.db.dao.ModeDao
import me.jhot.meld.data.model.Mode
import me.jhot.meld.data.model.ModeSettingsConverter

@Database(entities = [Mode::class], version = 1, exportSchema = false)
@TypeConverters(ModeSettingsConverter::class)
abstract class MeldDatabase : RoomDatabase() {
    abstract fun modeDao(): ModeDao
}
