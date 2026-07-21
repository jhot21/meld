package me.jhot.meld.service

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import me.jhot.meld.data.db.dao.ExclusivityGroupDao
import me.jhot.meld.data.db.dao.ModeDao
import me.jhot.meld.data.model.Mode
import me.jhot.meld.data.model.ModeSettings
import kotlinx.coroutines.flow.first
import java.time.LocalDate

data class ModeExport(
    val name: String,
    val isDefault: Boolean,
    val priority: Int,
    val settings: ModeSettings,
    val groupNames: List<String> = emptyList(),
)

private data class SystemExport(
    val exportVersion: Int,
    val exportedAt: Long,
    val modes: List<ModeExport>,
)

private data class LegacyModeExportV1(
    val name: String,
    val type: String,
    val priority: Int,
    val settings: ModeSettings,
)

private data class LegacySystemExportV1(
    val exportVersion: Int,
    val exportedAt: Long,
    val modes: List<LegacyModeExportV1>,
)

sealed interface ImportResult {
    data class ConflictsDetected(
        val modes: List<ModeExport>,
        val conflictingNames: List<String>,
        val legacyPrimaryNames: List<String> = emptyList(),
    ) : ImportResult
    data class Ready(
        val modes: List<ModeExport>,
        val legacyPrimaryNames: List<String> = emptyList(),
    ) : ImportResult
    data class NeedsExclusivityGroupAssignment(
        val modes: List<ModeExport>,
        val legacyPrimaryNames: List<String>,
        val preselectedGroupNames: Map<String, List<String>>,
    ) : ImportResult
    data object MalformedJson : ImportResult
    data object UnsupportedVersion : ImportResult
}

class ImportExportService(
    private val modeDao: ModeDao,
    private val exclusivityGroupDao: ExclusivityGroupDao,
    private val context: Context,
) {
    private val gson = Gson()

    private fun Mode.toModeExport(groupNames: List<String>) = ModeExport(
        name = name,
        isDefault = isDefault,
        priority = priority,
        settings = settings,
        groupNames = groupNames,
    )

    suspend fun export(): String {
        val modes = modeDao.getAll().first()
        val crossRefs = exclusivityGroupDao.getAllCrossRefs().first()
        val groups = exclusivityGroupDao.getAllGroups().first().associateBy { it.id }
        val exports = modes.map { mode ->
            val groupNames = crossRefs.filter { it.modeId == mode.id }.mapNotNull { groups[it.groupId]?.name }
            mode.toModeExport(groupNames)
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

        val versionProbe = try {
            gson.fromJson(json, SystemExport::class.java)
        } catch (e: JsonSyntaxException) {
            return ImportResult.MalformedJson
        } ?: return ImportResult.MalformedJson

        if (versionProbe.exportVersion > CURRENT_EXPORT_VERSION) {
            return ImportResult.UnsupportedVersion
        }

        val (modes, legacyPrimaryNames) = if (versionProbe.exportVersion == 1) {
            val legacy = try {
                gson.fromJson(json, LegacySystemExportV1::class.java)
            } catch (e: JsonSyntaxException) {
                return ImportResult.MalformedJson
            } ?: return ImportResult.MalformedJson
            val legacyModes = legacy.modes ?: return ImportResult.MalformedJson
            val converted = legacyModes.map { m ->
                ModeExport(name = m.name, isDefault = m.type == "DEFAULT", priority = m.priority, settings = m.settings)
            }
            converted to legacyModes.filter { it.type == "PRIMARY" }.map { it.name }
        } else {
            (versionProbe.modes ?: return ImportResult.MalformedJson) to emptyList()
        }

        val existingNames = modeDao.getAll().first().map { it.name }.toSet()
        val conflictingNames = modes.map { it.name }.filter { it in existingNames }

        return if (conflictingNames.isEmpty()) {
            ImportResult.Ready(modes, legacyPrimaryNames)
        } else {
            ImportResult.ConflictsDetected(modes, conflictingNames, legacyPrimaryNames)
        }
    }

    fun exportFileName(): String = "meld-export-${LocalDate.now()}.json"

    suspend fun exportSingleMode(mode: Mode): String {
        val groupNames = exclusivityGroupDao.getGroupsForModeOnce(mode.id).map { it.name }
        val systemExport = SystemExport(
            exportVersion = CURRENT_EXPORT_VERSION,
            exportedAt = System.currentTimeMillis(),
            modes = listOf(mode.toModeExport(groupNames)),
        )
        return gson.toJson(systemExport)
    }

    fun singleModeExportFileName(modeName: String): String {
        val safeName = modeName.replace(Regex("[^a-zA-Z0-9]"), "_")
        return "meld-export-${safeName}-${LocalDate.now()}.json"
    }

    companion object {
        const val CURRENT_EXPORT_VERSION = 2
    }
}
