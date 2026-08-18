package me.jhot.meld.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import me.jhot.meld.data.db.dao.ExclusivityGroupDao
import me.jhot.meld.data.db.dao.ModeDao
import me.jhot.meld.data.model.ExclusivityGroup
import me.jhot.meld.data.model.Mode
import me.jhot.meld.data.model.ModeExclusivityGroupCrossRef
import me.jhot.meld.data.model.ModeSettingsConverter

@Database(
    entities = [Mode::class, ExclusivityGroup::class, ModeExclusivityGroupCrossRef::class],
    version = 3,
    exportSchema = true,
)
@TypeConverters(ModeSettingsConverter::class)
abstract class MeldDatabase : RoomDatabase() {
    abstract fun modeDao(): ModeDao
    abstract fun exclusivityGroupDao(): ExclusivityGroupDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
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
                val now = System.currentTimeMillis()
                db.execSQL("""
                    INSERT INTO modes (name, type, priority, settings, createdAt, updatedAt)
                    SELECT 'Default', 'DEFAULT', 0, '{}', $now, $now
                    WHERE NOT EXISTS (SELECT 1 FROM modes WHERE type = 'DEFAULT')
                """.trimIndent())
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE exclusivity_groups (
                        id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_exclusivity_groups_name ON exclusivity_groups (name)")
                db.execSQL("""
                    CREATE TABLE mode_exclusivity_group_cross_ref (
                        modeId INTEGER NOT NULL,
                        groupId INTEGER NOT NULL,
                        PRIMARY KEY(modeId, groupId)
                    )
                """.trimIndent())

                val primaryCount = db.query("SELECT COUNT(*) FROM modes WHERE type = 'PRIMARY'").use { cursor ->
                    cursor.moveToFirst()
                    cursor.getInt(0)
                }
                if (primaryCount > 0) {
                    db.execSQL("INSERT INTO exclusivity_groups (name) VALUES ('Exclusive')")
                    db.execSQL("""
                        INSERT INTO mode_exclusivity_group_cross_ref (modeId, groupId)
                        SELECT id, (SELECT id FROM exclusivity_groups WHERE name = 'Exclusive')
                        FROM modes WHERE type = 'PRIMARY'
                    """.trimIndent())
                }

                db.execSQL("""
                    CREATE TABLE modes_new (
                        id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL,
                        isDefault INTEGER NOT NULL,
                        priority INTEGER NOT NULL,
                        settings TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO modes_new (id, name, isDefault, priority, settings, createdAt, updatedAt)
                    SELECT id, name, CASE WHEN type = 'DEFAULT' THEN 1 ELSE 0 END, priority, settings, createdAt, updatedAt
                    FROM modes
                """.trimIndent())
                db.execSQL("DROP TABLE modes")
                db.execSQL("ALTER TABLE modes_new RENAME TO modes")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_modes_name ON modes (name)")
            }
        }
    }
}
