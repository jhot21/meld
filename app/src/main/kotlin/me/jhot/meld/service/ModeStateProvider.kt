package me.jhot.meld.service

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import me.jhot.meld.MeldApplication

/**
 * Read-only mode state for other apps, guarded by `readPermission` in the manifest.
 *
 * `content://<applicationId>.state/modes` lists all non-default mode names;
 * `content://<applicationId>.state/active` lists the names of modes currently in effect.
 * Both return one column, [COLUMN_NAME].
 *
 * If the database cannot be read (for example before the first unlock) this throws instead of
 * returning an empty cursor, so a client keeps its last known state rather than reading "nothing
 * active".
 */
class ModeStateProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val path = uri.pathSegments.singleOrNull() ?: throw IllegalArgumentException("Unknown URI: $uri")
        val repo = (context!!.applicationContext as MeldApplication).modeRepository
        // The permission is the gate in the prompt flow, so the query itself is always allowed.
        val query = ModeStateQuery(repo, isSharingEnabled = { true })
        val names = try {
            runBlocking(Dispatchers.IO) { query.names(path) }
        } catch (e: SecurityException) {
            throw e
        } catch (e: Exception) {
            throw IllegalStateException("Mode state unavailable", e)
        } ?: throw IllegalArgumentException("Unknown URI: $uri")
        return MatrixCursor(arrayOf(COLUMN_NAME), names.size).also { cursor ->
            names.forEach { cursor.addRow(arrayOf<Any>(it)) }
        }
    }

    override fun getType(uri: Uri): String = "vnd.android.cursor.dir/vnd.me.jhot.meld.mode"

    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException("read-only")

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("read-only")

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("read-only")

    companion object {
        const val COLUMN_NAME = "name"
    }
}
