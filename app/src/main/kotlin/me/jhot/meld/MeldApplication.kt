package me.jhot.meld

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import me.jhot.meld.data.db.ActiveDatabase
import me.jhot.meld.data.db.MeldDatabase
import me.jhot.meld.receiver.NtfyReceiver
import me.jhot.meld.service.MeldForegroundService
import me.jhot.meld.service.ModeRepository
import me.jhot.meld.service.OverrideSessionStore
import me.jhot.meld.service.PermissionChecker
import me.jhot.meld.service.SettingsApplier
import me.jhot.meld.tasker.TaskerBridge

class MeldApplication : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: MeldDatabase by lazy {
        Room.databaseBuilder(this, MeldDatabase::class.java, "meld.db")
            .addMigrations(MeldDatabase.MIGRATION_1_2)
            .build()
    }

    /** Device-protected storage — accessible before user unlock (for BootReceiver). */
    val activeDatabase: ActiveDatabase by lazy {
        val deviceContext = createDeviceProtectedStorageContext()
        Room.databaseBuilder(deviceContext, ActiveDatabase::class.java, "active.db").build()
    }

    val overrideSessionStore: OverrideSessionStore by lazy {
        OverrideSessionStore(getSharedPreferences("meld_prefs", Context.MODE_PRIVATE))
    }

    val permissionChecker: PermissionChecker by lazy { PermissionChecker(this) }

    val settingsApplier: SettingsApplier by lazy {
        SettingsApplier(this, permissionChecker, overrideSessionStore, applicationScope)
    }

    val modeRepository: ModeRepository by lazy {
        ModeRepository(
            modeDao = database.modeDao(),
            activeModeDao = activeDatabase.activeModeDao(),
            settingsApplier = settingsApplier,
        )
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForegroundService(Intent(this, MeldForegroundService::class.java))
        ContextCompat.registerReceiver(
            this,
            NtfyReceiver(),
            IntentFilter(NtfyReceiver.ACTION_NTFY_MESSAGE),
            ContextCompat.RECEIVER_EXPORTED,
        )
        TaskerBridge(this, applicationScope).start()
    }

    private fun createNotificationChannel() {
        val nm = getSystemService(NotificationManager::class.java)!! // Non-null: minSdk 31 guarantees NotificationManager is always available.
        // Delete any stale channel so that a fresh one is created with the correct importance.
        // (Android ignores importance updates on existing channels, so a stale channel created
        // with IMPORTANCE_MIN would persist across updates and suppress the notification.)
        nm.deleteNotificationChannel(MeldForegroundService.CHANNEL_ID)
        val channel = NotificationChannel(
            MeldForegroundService.CHANNEL_ID,
            "Background service",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Keeps Meld running in the background to receive ntfy automation messages. Safe to disable — this notification has no other purpose."
        }
        nm.createNotificationChannel(channel)
    }
}
