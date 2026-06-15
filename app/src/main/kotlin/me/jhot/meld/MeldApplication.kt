package me.jhot.meld

import android.app.Application
import android.app.ForegroundServiceStartNotAllowedException
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import me.jhot.meld.data.db.ActiveDatabase
import me.jhot.meld.data.db.MeldDatabase
import me.jhot.meld.receiver.NtfyReceiver
import me.jhot.meld.service.ImportExportService
import me.jhot.meld.service.MeldForegroundService
import me.jhot.meld.service.ModeRepository
import me.jhot.meld.service.OverrideSessionStore
import me.jhot.meld.service.PermissionChecker
import me.jhot.meld.service.SettingsApplier
import me.jhot.meld.service.ShizukuGranter
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
            scope = applicationScope,
        )
    }

    val importExportService: ImportExportService by lazy {
        ImportExportService(database.modeDao(), this)
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        // startForegroundService() is safe for most process-creation contexts:
        //   • User opens the app (MainActivity) — always foreground, always exempt.
        //   • After a reboot — LOCKED_BOOT_COMPLETED is an exempt broadcast; the OS grants
        //     the FGS start exemption to the whole process before Application.onCreate() runs.
        //   • NtfyReceiver can't wake a dead process (dynamically registered).
        //
        // Non-exempt background triggers — Tasker FIRE_SETTING / QUERY_CONDITION broadcasts
        // and IntentReceiver's meld.intent.SET_MODE — do NOT grant an FGS start exemption.
        // Without the catch, the exception propagates through Application.onCreate() and
        // crashes the entire process before NtfyReceiver registers or TaskerBridge starts.
        // Catching here lets those initializations complete; the FGS will be started by
        // the next exempt trigger (reboot via BootReceiver, or user opening the app).
        try {
            startForegroundService(Intent(this, MeldForegroundService::class.java))
        } catch (e: ForegroundServiceStartNotAllowedException) {
            Log.w(TAG, "FGS start blocked by background restriction; will retry on next allowed trigger", e)
        }
        ContextCompat.registerReceiver(
            this,
            NtfyReceiver(),
            IntentFilter(NtfyReceiver.ACTION_NTFY_MESSAGE),
            ContextCompat.RECEIVER_EXPORTED,
        )
        TaskerBridge(this, applicationScope).start()
    }

    companion object {
        private const val TAG = "MeldApplication"
    }

    private fun createNotificationChannel() {
        val nm = getSystemService(NotificationManager::class.java)!! // Non-null: minSdk 31 guarantees NotificationManager is always available.
        // One-time cleanup: delete the v1 channel which was created with IMPORTANCE_MIN.
        // Android ignores importance updates on existing channels, so the old channel would
        // suppress the notification. Deleting it here is safe — deleteNotificationChannel is a
        // no-op once it's gone, so this runs harmlessly on every future launch.
        nm.deleteNotificationChannel(MeldForegroundService.CHANNEL_ID_V1)
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
