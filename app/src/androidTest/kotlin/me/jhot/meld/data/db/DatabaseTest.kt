package me.jhot.meld.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.jhot.meld.data.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseTest {

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
}
