package me.jhot.meld.service

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import me.jhot.meld.data.db.dao.ModeDao
import me.jhot.meld.data.model.ModeSettings
import me.jhot.meld.data.model.ModeType
import kotlinx.coroutines.flow.first
import java.time.LocalDate

data class ModeExport(
    val name: String,
    val type: ModeType,
    val priority: Int,
    val settings: ModeSettings,
)

private data class SystemExport(
    val exportVersion: Int,
    val exportedAt: Long,
    val modes: List<ModeExport>,
)

sealed interface ImportResult {
    data class ConflictsDetected(val modes: List<ModeExport>, val conflictingNames: List<String>) : ImportResult
    data class Ready(val modes: List<ModeExport>) : ImportResult
    data object MalformedJson : ImportResult
    data object UnsupportedVersion : ImportResult
}

class ImportExportService(
    private val modeDao: ModeDao,
    private val context: Context,
) {
    private val gson = Gson()

    suspend fun export(): String {
        val modes = modeDao.getAll().first()
        val exports = modes.map { mode ->
            ModeExport(
                name = mode.name,
                type = mode.type,
                priority = mode.priority,
                settings = mode.settings,
            )
        }
        val systemExport = SystemExport(
            exportVersion = CURRENT_EXPORT_VERSION,
            exportedAt = System.currentTimeMillis(),
            modes = exports,
        )
        return gson.toJson(systemExport)
    }

    suspend fun parseImport(uri: Uri): ImportResult {
        val json = try {
            context.contentResolver.openInputStream(uri)?.use {
                it.readBytes().toString(Charsets.UTF_8)
            } ?: return ImportResult.MalformedJson
        } catch (e: Exception) {
            return ImportResult.MalformedJson
        }

        val systemExport = try {
            gson.fromJson(json, SystemExport::class.java)
        } catch (e: JsonSyntaxException) {
            return ImportResult.MalformedJson
        } ?: return ImportResult.MalformedJson

        if (systemExport.exportVersion > CURRENT_EXPORT_VERSION) {
            return ImportResult.UnsupportedVersion
        }

        val existingNames = modeDao.getAll().first().map { it.name }.toSet()
        val conflictingNames = systemExport.modes.map { it.name }.filter { it in existingNames }

        return if (conflictingNames.isEmpty()) {
            ImportResult.Ready(systemExport.modes)
        } else {
            ImportResult.ConflictsDetected(systemExport.modes, conflictingNames)
        }
    }

    fun exportFileName(): String = "meld-export-${LocalDate.now()}.json"

    companion object {
        const val CURRENT_EXPORT_VERSION = 1
    }
}
