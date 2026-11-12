package me.jhot.meld.service

import android.content.Intent
import android.util.Log

/**
 * Sends [ACTION] as an implicit broadcast guarded by [PERMISSION], so only runtime-registered
 * receivers in apps the user has granted that permission to hear it. Clients treat it as a nudge
 * and re-query the provider; the extra is a convenience.
 */
class BroadcastModesChangedNotifier(
    private val isSharingEnabled: () -> Boolean,
    private val send: (Intent, String) -> Unit,
) : ModesChangedNotifier {

    override fun onChanged(activeModeNames: List<String>) {
        if (!isSharingEnabled()) return
        val intent = Intent(ACTION)
        intent.putExtra(EXTRA_MODES, activeModeNames.toTypedArray())
        Log.d(TAG, "send $ACTION modes=$activeModeNames")
        send(intent, PERMISSION)
    }

    companion object {
        const val ACTION = "meld.intent.MODES_CHANGED"
        const val EXTRA_MODES = "modes"
        const val PERMISSION = "me.jhot.meld.permission.READ_MODES"
        private const val TAG = "MeldNotifier"
    }
}
