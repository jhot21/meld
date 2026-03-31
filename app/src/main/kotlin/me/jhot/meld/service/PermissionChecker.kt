package me.jhot.meld.service

import android.app.NotificationManager
import android.content.Context
import android.provider.Settings

class PermissionChecker(private val context: Context) {

    /** WRITE_SETTINGS — required for volumes, brightness, ringer mode, rotation, display timeout */
    fun canWriteSettings(): Boolean = Settings.System.canWrite(context)

    /** ACCESS_NOTIFICATION_POLICY — required for Do Not Disturb */
    fun canWriteNotificationPolicy(): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return false
        return nm.isNotificationPolicyAccessGranted
    }

    @Volatile
    private var secureSettingsGranted: Boolean? = null

    /**
     * WRITE_SECURE_SETTINGS — granted via ADB.
     * Result is cached for the process lifetime — this permission won't change during a session.
     * Call [resetSecureSettingsCache] if you need to force a re-check (e.g., after ADB grant).
     */
    fun canWriteSecureSettings(): Boolean {
        secureSettingsGranted?.let { return it }
        val granted = try {
            Settings.Secure.putInt(context.contentResolver, "_meld_permission_probe", 0)
            Settings.Secure.putString(context.contentResolver, "_meld_permission_probe", null)
            true
        } catch (e: SecurityException) {
            false
        }
        secureSettingsGranted = granted
        return granted
    }

    fun resetSecureSettingsCache() {
        secureSettingsGranted = null
    }
}
