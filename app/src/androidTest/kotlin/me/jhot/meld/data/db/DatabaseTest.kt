package me.jhot.meld.data.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import me.jhot.meld.data.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.fail
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseTest {

    private val TEST_DB = "migration-test.db"

    @get:Rule
    val migrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MeldDatabase::class.java,
    )

    private lateinit var meldDb: MeldDatabase
    private lateinit var activeDb: ActiveDatabase

    @Before
    fun setup() {
        meldDb = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MeldDatabase::class.java
        ).allowMainThreadQueries().build()

        activeDb = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ActiveDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun teardown() {
        meldDb.close()
        activeDb.close()
    }

    @Test
    fun insertAndRetrieveMode() = runTest {
        val mode = Mode(name = "home", type = ModeType.PRIMARY, priority = 10)
        val id = meldDb.modeDao().insert(mode)
        val retrieved = meldDb.modeDao().getAll().first()
        assertEquals(1, retrieved.size)
        assertEquals("home", retrieved[0].name)
        assertEquals(id, retrieved[0].id)
    }

    @Test
    fun activeModesClearedCorrectly() = runTest {
        val activeDao = activeDb.activeModeDao()
        activeDao.insert(ActiveMode(modeId = 1L))
        activeDao.insert(ActiveMode(modeId = 2L))
        assertEquals(2, activeDao.getAll().first().size)
        activeDao.clearAll()
        assertEquals(0, activeDao.getAll().first().size)
    }

    @Test
    fun getActiveModeIds() = runTest {
        val activeDao = activeDb.activeModeDao()
        activeDao.insert(ActiveMode(modeId = 5L))
        activeDao.insert(ActiveMode(modeId = 8L))
        val ids = activeDao.getActiveModeIds().first()
        assertEquals(setOf(5L, 8L), ids)
    }

    @Test
    fun migration1to2_promotesIsDefaultPrimaryToDefaultType() {
        val db = migrationTestHelper.createDatabase(TEST_DB, 1)
        db.execSQL(
            "INSERT INTO modes (name, type, priority, isDefault, settings, createdAt, updatedAt) " +
            "VALUES ('home', 'PRIMARY', 50, 1, '{}', 0, 0)"
        )
        db.close()

        val migratedDb = migrationTestHelper.runMigrationsAndValidate(TEST_DB, 2, true, MeldDatabase.MIGRATION_1_2)
        val cursor = migratedDb.query("SELECT type, priority FROM modes WHERE name = 'home'")
        cursor.moveToFirst()
        assertEquals("DEFAULT", cursor.getString(0))
        assertEquals(0, cursor.getInt(1))
        cursor.close()

        // Verify no duplicate DEFAULT rows were created by the seed INSERT
        val countCursor = migratedDb.query("SELECT COUNT(*) FROM modes WHERE type = 'DEFAULT'")
        countCursor.moveToFirst()
        assertEquals(1, countCursor.getInt(0))
        countCursor.close()
        migratedDb.close()
    }

    @Test
    fun migration1to2_createsDefaultModeWhenNoneExisted() {
        val db = migrationTestHelper.createDatabase(TEST_DB, 1)
        db.execSQL(
            "INSERT INTO modes (name, type, priority, isDefault, settings, createdAt, updatedAt) " +
            "VALUES ('work', 'PRIMARY', 50, 0, '{}', 0, 0)"
        )
        db.close()

        val migratedDb = migrationTestHelper.runMigrationsAndValidate(TEST_DB, 2, true, MeldDatabase.MIGRATION_1_2)
        val cursor = migratedDb.query("SELECT COUNT(*) FROM modes WHERE type = 'DEFAULT'")
        cursor.moveToFirst()
        assertEquals(1, cursor.getInt(0))
        cursor.close()
        migratedDb.close()
    }

    @Test
    fun deleteSafe_throwsForDefaultMode() = runTest {
        val dao = meldDb.modeDao()
        val id = dao.insert(Mode(name = "Default", type = ModeType.DEFAULT, priority = 0))
        val defaultMode = dao.getAll().first().find { it.id == id }!!
        try {
            dao.deleteSafe(defaultMode)
            fail("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertEquals("Cannot delete the DEFAULT mode", e.message)
        }
    }

    @Test
    fun replaceByName_insertsNewModes() = runTest {
        val dao = meldDb.modeDao()
        val modes = listOf(
            Mode(name = "Work", type = ModeType.PRIMARY, priority = 50),
            Mode(name = "Home", type = ModeType.SECONDARY, priority = 30),
        )
        dao.replaceByName(modes)
        val all = dao.getAll().first()
        assertEquals(2, all.size)
        val work = all.find { it.name == "Work" }!!
        assertEquals(ModeType.PRIMARY, work.type)
        assertEquals(50, work.priority)
        val home = all.find { it.name == "Home" }!!
        assertEquals(ModeType.SECONDARY, home.type)
        assertEquals(30, home.priority)
    }

    @Test
    fun replaceByName_overwritesExistingModeWithSameName() = runTest {
        val dao = meldDb.modeDao()
        dao.insert(Mode(name = "Work", type = ModeType.PRIMARY, priority = 50, settings = ModeSettings(brightness = 100)))

        val imported = listOf(Mode(name = "Work", type = ModeType.PRIMARY, priority = 70, settings = ModeSettings(brightness = 200)))
        dao.replaceByName(imported)

        val all = dao.getAll().first()
        assertEquals(1, all.size)
        assertEquals(70, all[0].priority)
        assertEquals(200, all[0].settings.brightness)
    }

    @Test
    fun replaceByName_leavesOtherModesUntouched() = runTest {
        val dao = meldDb.modeDao()
        dao.insert(Mode(name = "Existing", type = ModeType.SECONDARY, priority = 10))

        val imported = listOf(Mode(name = "Work", type = ModeType.PRIMARY, priority = 50))
        dao.replaceByName(imported)

        val all = dao.getAll().first()
        assertEquals(2, all.size)
        assertTrue(all.any { it.name == "Existing" })
        assertTrue(all.any { it.name == "Work" })
    }
}
