package me.jhot.meld.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import me.jhot.meld.MainActivity
import me.jhot.meld.MeldApplication
import me.jhot.meld.R

class MeldForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val repo get() = (applicationContext as MeldApplication).modeRepository
    private val notificationManager get() = getSystemService(NotificationManager::class.java)!! // Non-null: minSdk 31 guarantees NotificationManager is always available.

    private val tapPendingIntent by lazy {
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        PendingIntent.getActivity(
            this, 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    override fun onCreate() {
        super.onCreate()
        // Specifying foregroundServiceType is required when targetSdk >= 34 (Android 14+);
        // omitting it suppresses the notification without crashing.
        //
        // SPECIAL_USE has no time limit (unlike DATA_SYNC which is capped at 6 h/day on Android 14+).
        // This app is not distributed via Google Play, so no Play policy review is required.
        startForeground(
            NOTIFICATION_ID,
            buildNotification(buildNotificationBody(emptyList())),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        serviceScope.launch {
            repo.modesWithActiveState.collect { modes ->
                notificationManager.notify(NOTIFICATION_ID, buildNotification(buildNotificationBody(modes)))
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(body: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Meld")
            .setContentText(body)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(tapPendingIntent)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "meld_background_v2"
        /** Legacy channel ID created with IMPORTANCE_MIN; kept here only for cleanup. */
        const val CHANNEL_ID_V1 = "meld_background"
        const val NOTIFICATION_ID = 1
    }
}
