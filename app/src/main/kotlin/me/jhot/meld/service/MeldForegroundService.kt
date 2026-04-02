package me.jhot.meld.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import me.jhot.meld.R

class MeldForegroundService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Meld")
            .setContentText("Running in the background")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        // Specifying foregroundServiceType is required when targetSdk >= 34 (Android 14+);
        // omitting it suppresses the notification without crashing.
        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "meld_background_v2"
        /** Legacy channel ID created with IMPORTANCE_MIN; kept here only for cleanup. */
        const val CHANNEL_ID_V1 = "meld_background"
        const val NOTIFICATION_ID = 1
    }
}
