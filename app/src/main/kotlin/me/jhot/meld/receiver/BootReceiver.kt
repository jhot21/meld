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
import me.jhot.meld.service.MeldForegroundService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED) return

        context.startForegroundService(Intent(context, MeldForegroundService::class.java))

        val pendingResult = goAsync()
        val app = context.applicationContext as MeldApplication

        app.applicationScope.launch(Dispatchers.IO) {
            try {
                withTimeout(15_000L) {
                    app.activeDatabase.activeModeDao().clearAll()
                }
            } catch (e: TimeoutCancellationException) {
                Log.w("BootReceiver", "Boot cleanup timed out")
            } catch (e: Exception) {
                Log.e("BootReceiver", "Failed to clear active modes on boot", e)
            } finally {
                pendingResult?.finish()
            }
        }
    }
}
