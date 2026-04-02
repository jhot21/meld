package me.jhot.meld.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.jhot.meld.MeldApplication

/**
 * Parses the ntfy `tags` extra and returns:
 *   true  → add the mode to context
 *   false → remove the mode from context
 *   null  → ignore the message
 *
 * Extracted as an internal top-level function so it can be unit tested without Android framework.
 */
internal fun parseNtfyIntent(tagsString: String?): Boolean? {
    val tags = tagsString
        ?.split(",")
        ?.map { it.trim().lowercase() }
        ?.toSet()
        ?: emptySet()
    if ("meld" !in tags) return null
    val hasTruthy = tags.any { it in NtfyReceiver.TRUTHY_TAGS }
    val hasFalsy = tags.any { it in NtfyReceiver.FALSY_TAGS }
    return when {
        hasTruthy && hasFalsy -> null
        hasTruthy -> true
        hasFalsy -> false
        else -> null
    }
}

class NtfyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_NTFY_MESSAGE) return

        val message = intent.getStringExtra(EXTRA_MESSAGE)?.trim() ?: return
        val active = parseNtfyIntent(intent.getStringExtra(EXTRA_TAGS)) ?: return

        val pendingResult = goAsync()
        val app = context.applicationContext as MeldApplication

        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (active) {
                    app.modeRepository.addToContext(message)
                } else {
                    app.modeRepository.removeFromContext(message)
                }
            } catch (e: Exception) {
                Log.e("NtfyReceiver", "Failed to update mode context", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_NTFY_MESSAGE = "io.heckel.ntfy.MESSAGE_RECEIVED"
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_TAGS = "tags"
        val TRUTHY_TAGS = setOf("add", "activate", "active", "1", "true", "on", "enable", "enabled")
        val FALSY_TAGS = setOf("remove", "deactivate", "inactive", "0", "false", "off", "disable", "disabled")
    }
}
