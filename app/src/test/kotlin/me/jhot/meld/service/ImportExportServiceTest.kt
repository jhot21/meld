package me.jhot.meld.service

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import me.jhot.meld.data.db.dao.ModeDao
import me.jhot.meld.data.model.Mode
import me.jhot.meld.data.model.ModeSettings
import me.jhot.meld.data.model.ModeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class ImportExportServiceTest {

    private val gson = Gson()
    private val fakeUri = mockk<Uri>()

    private fun serviceWithModes(modes: List<Mode> = emptyList()): ImportExportService {
        val modeDao = mockk<ModeDao> {
            every { getAll() } returns flowOf(modes)
        }
        return ImportExportService(modeDao, mockk(relaxed = true))
    }

    private fun serviceWithJson(existingModes: List<Mode> = emptyList(), json: String): ImportExportService {
        val inputStream = ByteArrayInputStream(json.toByteArray())
        val contentResolver = mockk<ContentResolver> {
            every { openInputStream(any()) } returns inputStream
        }
        val context = mockk<Context> {
            every { this@mockk.contentResolver } returns contentResolver
        }
        val modeDao = mockk<ModeDao> {
            every { getAll() } returns flowOf(existingModes)
        }
        return ImportExportService(modeDao, context)
    }

    // --- export() ---

    @Test
    fun export_includesAllModes() = runTest {
        val modes = listOf(
            Mode(id = 1, name = "Default", type = ModeType.DEFAULT, priority = 0),
            Mode(id = 2, name = "Work", type = ModeType.PRIMARY, priority = 50),
        )
        val json = serviceWithModes(modes).export()
        val parsed = gson.fromJson(json, Map::class.java)
        val modesList = parsed["modes"] as List<*>
        assertEquals(2, modesList.size)
    }

    @Test
    fun export_omitsIdCreatedAtUpdatedAt() = runTest {
        val modes = listOf(
            Mode(id = 99, name = "Work", type = ModeType.PRIMARY, priority = 50, createdAt = 1000L, updatedAt = 2000L)
        )
        val json = serviceWithModes(modes).export()
        val parsed = gson.fromJson(json, Map::class.java)
        val modesList = parsed["modes"] as List<Map<*, *>>
        val modeMap = modesList[0]
        assertFalse("id must not be exported", modeMap.containsKey("id"))
        assertFalse("createdAt must not be exported", modeMap.containsKey("createdAt"))
        assertFalse("updatedAt must not be exported", modeMap.containsKey("updatedAt"))
    }

    @Test
    fun export_setsCurrentExportVersion() = runTest {
        val json = serviceWithModes().export()
        val parsed = gson.fromJson(json, Map::class.java)
        assertEquals(
            ImportExportService.CURRENT_EXPORT_VERSION.toDouble(),
            parsed["exportVersion"]
        )
    }

    @Test
    fun export_onlyEmitsNonNullSettings() = runTest {
        val modes = listOf(
            Mode(name = "Work", type = ModeType.PRIMARY, priority = 50,
                settings = ModeSettings(brightness = 200))  // all other fields null
        )
        val json = serviceWithModes(modes).export()
        val parsed = gson.fromJson(json, Map::class.java)
        val modesList = parsed["modes"] as List<Map<*, *>>
        val settings = modesList[0]["settings"] as Map<*, *>
        assertTrue("brightness should be present", settings.containsKey("brightness"))
        assertFalse("volumeMedia should be absent (null)", settings.containsKey("volumeMedia"))
    }

    // --- parseImport() ---

    @Test
    fun parseImport_returnsReady_whenNoConflicts() = runTest {
        val json = """{"exportVersion":1,"exportedAt":0,"modes":[{"name":"Work","type":"PRIMARY","priority":50,"settings":{}}]}"""
        val result = serviceWithJson(existingModes = emptyList(), json = json).parseImport(fakeUri)
        assertTrue(result is ImportResult.Ready)
        assertEquals("Work", (result as ImportResult.Ready).modes[0].name)
    }

    @Test
    fun parseImport_returnsConflictsDetected_whenNameCollides() = runTest {
        val existing = listOf(Mode(id = 1, name = "Work", type = ModeType.PRIMARY, priority = 50))
        val json = """{"exportVersion":1,"exportedAt":0,"modes":[{"name":"Work","type":"PRIMARY","priority":70,"settings":{}}]}"""
        val result = serviceWithJson(existingModes = existing, json = json).parseImport(fakeUri)
        assertTrue(result is ImportResult.ConflictsDetected)
        assertEquals(listOf("Work"), (result as ImportResult.ConflictsDetected).conflictingNames)
    }

    @Test
    fun parseImport_conflictsResult_containsAllImportedModes() = runTest {
        val existing = listOf(Mode(id = 1, name = "Work", type = ModeType.PRIMARY, priority = 50))
        val json = """{"exportVersion":1,"exportedAt":0,"modes":[
            {"name":"Work","type":"PRIMARY","priority":70,"settings":{}},
            {"name":"Home","type":"SECONDARY","priority":30,"settings":{}}
        ]}"""
        val result = serviceWithJson(existingModes = existing, json = json).parseImport(fakeUri)
        assertTrue(result is ImportResult.ConflictsDetected)
        assertEquals(2, (result as ImportResult.ConflictsDetected).modes.size)
    }

    @Test
    fun parseImport_returnsMalformedJson_onInvalidJson() = runTest {
        val result = serviceWithJson(json = "not valid json { at all").parseImport(fakeUri)
        assertEquals(ImportResult.MalformedJson, result)
    }

    @Test
    fun parseImport_returnsUnsupportedVersion_whenVersionTooHigh() = runTest {
        val json = """{"exportVersion":999,"exportedAt":0,"modes":[]}"""
        val result = serviceWithJson(json = json).parseImport(fakeUri)
        assertEquals(ImportResult.UnsupportedVersion, result)
    }
}
