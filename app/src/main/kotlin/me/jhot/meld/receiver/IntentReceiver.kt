package me.jhot.meld.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import me.jhot.meld.MeldApplication

class IntentReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SET_MODE) return

        val modeName = intent.getStringExtra(EXTRA_MODE) ?: return
        val active = intent.getBooleanExtra(EXTRA_ACTIVE, true)

        val pendingResult = goAsync()
        val app = context.applicationContext as MeldApplication

        app.applicationScope.launch(Dispatchers.IO) {
            try {
                withTimeout(15_000L) {
                    if (active) {
                        app.modeRepository.addToContext(modeName)
                    } else {
                        app.modeRepository.removeFromContext(modeName)
                    }
                }
            } catch (e: TimeoutCancellationException) {
                Log.w("IntentReceiver", "Mode context update timed out")
            } catch (e: Exception) {
                Log.e("IntentReceiver", "Failed to update mode context", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_SET_MODE = "meld.intent.SET_MODE"
        const val EXTRA_MODE = "mode"
        const val EXTRA_ACTIVE = "active"
    }
}
