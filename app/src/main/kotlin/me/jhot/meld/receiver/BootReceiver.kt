package me.jhot.meld.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.room.Room
import me.jhot.meld.data.db.ActiveDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Must use device-protected storage context — regular storage is not
                // available at LOCKED_BOOT_COMPLETED time (before user unlock)
                val deviceContext = context.createDeviceProtectedStorageContext()
                val db = Room.databaseBuilder(deviceContext, ActiveDatabase::class.java, "active.db")
                    .build()
                try {
                    db.activeModeDao().clearAll()
                } catch (e: Exception) {
                    Log.e("BootReceiver", "Failed to clear active modes on boot", e)
                } finally {
                    db.close()
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
