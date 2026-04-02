package me.jhot.meld.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import me.jhot.meld.data.db.dao.ModeDao
import me.jhot.meld.data.model.Mode
import me.jhot.meld.data.model.ModeSettingsConverter

@Database(entities = [Mode::class], version = 2, exportSchema = true)
@TypeConverters(ModeSettingsConverter::class)
abstract class MeldDatabase : RoomDatabase() {
    abstract fun modeDao(): ModeDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Recreate modes table without the isDefault column,
                // promoting any isDefault=1 row to type='DEFAULT', priority=0.
                db.execSQL("""
                    CREATE TABLE modes_new (
                        id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL,
                        type TEXT NOT NULL,
                        priority INTEGER NOT NULL,
                        settings TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO modes_new (id, name, type, priority, settings, createdAt, updatedAt)
                    SELECT id, name,
                        CASE WHEN isDefault = 1 THEN 'DEFAULT' ELSE type END,
                        CASE WHEN isDefault = 1 THEN 0 ELSE priority END,
                        settings, createdAt, updatedAt
                    FROM modes
                """.trimIndent())
                db.execSQL("DROP TABLE modes")
                db.execSQL("ALTER TABLE modes_new RENAME TO modes")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_modes_name ON modes (name)")
                // Guarantee a DEFAULT mode exists even if no isDefault=1 row existed
                val now = System.currentTimeMillis()
                db.execSQL("""
                    INSERT INTO modes (name, type, priority, settings, createdAt, updatedAt)
                    SELECT 'Default', 'DEFAULT', 0, '{}', $now, $now
                    WHERE NOT EXISTS (SELECT 1 FROM modes WHERE type = 'DEFAULT')
                """.trimIndent())
            }
        }
    }
}
