package me.jhot.meld.service

import android.app.NotificationManager
import android.content.Context
import android.content.ContentResolver
import android.media.AudioManager
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.jhot.meld.data.model.*

private const val TAG = "SettingsApplier"
private const val KEY_KEYBOARD_VIBRATION = "keyboard_vibration_enabled"

class SettingsApplier(
    private val context: Context,
    private val permissionChecker: PermissionChecker,
    private val overrideSessionStore: OverrideSessionStore,
    private val applicationScope: CoroutineScope,
) {

    fun apply(settings: ModeSettings) {
        applyMediaVolumeWithOverride(settings)
        if (permissionChecker.canWriteSettings()) {
            applyWriteSettings(settings)
        }
        if (permissionChecker.canWriteNotificationPolicy()) {
            applyDnd(settings)
        }
        if (permissionChecker.canWriteSecureSettings()) {
            applySecureSettings(settings)
        }
    }

    // ---- Media volume override logic ----------------------------------------

    private fun applyMediaVolumeWithOverride(settings: ModeSettings) {
        if (!permissionChecker.canWriteSettings()) return

        val overrideActive = settings.volumeMediaOverride == true

        when {
            !overrideActive && overrideSessionStore.isActive() -> {
                // Override just ended — clear session and apply normally
                overrideSessionStore.clear()
                settings.volumeMedia?.let { setMediaVolume(it) }
            }
            overrideActive && !overrideSessionStore.isActive() -> {
                // Override just started — apply once and lock
                settings.volumeMedia?.let { setMediaVolume(it) }
                overrideSessionStore.setActive()
            }
            overrideActive && overrideSessionStore.isActive() -> {
                // Override session in progress — do nothing
                // Note: all Meld-driven and manual volume changes are ignored
                // while an override session is active. See user documentation.
            }
            else -> {
                // Normal (no override) — apply if set
                settings.volumeMedia?.let { setMediaVolume(it) }
            }
        }
    }

    private fun setMediaVolume(volume: Int) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        am.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
    }

    // ---- WRITE_SETTINGS settings --------------------------------------------

    private fun applyWriteSettings(settings: ModeSettings) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val resolver = context.contentResolver

        settings.volumeNotification?.let {
            if (am.getStreamVolume(AudioManager.STREAM_NOTIFICATION) != it) {
                am.setStreamVolume(AudioManager.STREAM_NOTIFICATION, it, 0)
            }
        }

        settings.ringerMode?.let {
            val mode = when (it) {
                RingerMode.SILENT -> AudioManager.RINGER_MODE_SILENT
                RingerMode.VIBRATE -> AudioManager.RINGER_MODE_VIBRATE
                RingerMode.SOUND -> AudioManager.RINGER_MODE_NORMAL
            }
            if (am.ringerMode != mode) am.ringerMode = mode
        }

        settings.brightnessAuto?.let { auto ->
            val value = if (auto) Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
                        else Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            resolver.putSystemIntIfChanged(Settings.System.SCREEN_BRIGHTNESS_MODE, value)
        }

        if (settings.brightnessAuto != true) {
            settings.brightness?.let {
                resolver.putSystemIntIfChanged(Settings.System.SCREEN_BRIGHTNESS, it.coerceIn(0, 255))
            }
        }

        settings.displayTimeout?.let {
            resolver.putSystemIntIfChanged(Settings.System.SCREEN_OFF_TIMEOUT, it * 60 * 1000)
        }

        settings.screenRotation?.let {
            resolver.putSystemIntIfChanged(Settings.System.ACCELEROMETER_ROTATION, if (it) 1 else 0)
        }
    }

    // ---- ACCESS_NOTIFICATION_POLICY -----------------------------------------

    private fun applyDnd(settings: ModeSettings) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        settings.dnd?.let {
            val filter = when (it) {
                DndMode.OFF -> NotificationManager.INTERRUPTION_FILTER_ALL
                DndMode.PRIORITY_ONLY -> NotificationManager.INTERRUPTION_FILTER_PRIORITY
                DndMode.ALARMS_ONLY -> NotificationManager.INTERRUPTION_FILTER_ALARMS
                DndMode.TOTAL_SILENCE -> NotificationManager.INTERRUPTION_FILTER_NONE
            }
            nm.setInterruptionFilter(filter)
        }
    }

    // ---- WRITE_SECURE_SETTINGS settings -------------------------------------

    private fun applySecureSettings(settings: ModeSettings) {
        val resolver = context.contentResolver

        settings.darkMode?.let {
            // 1 = light, 2 = night/dark
            Settings.Secure.putInt(resolver, "ui_night_mode", if (it) 2 else 1)
        }

        settings.nightLight?.let {
            Settings.Secure.putInt(resolver, "night_display_activated", if (it) 1 else 0)
        }

        settings.extraDim?.let {
            // Android 12+ (API 31+) — reduce_bright_colors_activated
            Settings.Secure.putInt(resolver, "reduce_bright_colors_activated", if (it) 1 else 0)
        }

        settings.immersiveMode?.let {
            val value = when (it) {
                ImmersiveMode.OFF -> ""
                ImmersiveMode.STATUS_BAR -> "immersive.status=*"
                ImmersiveMode.NAV_BAR -> "immersive.navigation=*"
                ImmersiveMode.BOTH -> "immersive.full=*"
            }
            Settings.Global.putString(resolver, "policy_control", value)
        }

        settings.grayscale?.let {
            Settings.Secure.putInt(resolver, "accessibility_display_daltonizer_enabled", if (it) 1 else 0)
            if (it) Settings.Secure.putInt(resolver, "accessibility_display_daltonizer", 0)
        }

        settings.hapticFeedback?.let {
            Settings.System.putInt(resolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, if (it) 1 else 0)
        }

        settings.keyboardVibration?.let { enabled ->
            val value = if (enabled) 1 else 0
            try {
                Settings.System.putInt(resolver, KEY_KEYBOARD_VIBRATION, value)
            } catch (e: IllegalArgumentException) {
                if (ShizukuGranter.hasPermission()) {
                    applicationScope.launch {
                        val ok = ShizukuGranter.putSetting(SettingNamespace.SYSTEM, KEY_KEYBOARD_VIBRATION, value)
                        if (!ok) Log.w(TAG, "Shizuku fallback for $KEY_KEYBOARD_VIBRATION failed")
                    }
                }
            }
        }

        settings.batterySaver?.let {
            Settings.Global.putInt(resolver, "low_power", if (it) 1 else 0)
        }

        settings.locationMode?.let {
            val value = when (it) {
                LocationMode.OFF -> 0
                LocationMode.BATTERY_SAVER -> 2
                LocationMode.DEVICE_ONLY -> 1
                LocationMode.HIGH_ACCURACY -> 3
            }
            Settings.Secure.putInt(resolver, "location_mode", value)
        }

        settings.bluetooth?.let { enable ->
            if (ShizukuGranter.hasPermission()) {
                applicationScope.launch {
                    val ok = ShizukuGranter.setBluetooth(enable)
                    if (!ok) Log.w(TAG, "Shizuku setBluetooth($enable) failed")
                }
            } else {
                Log.w(TAG, "Bluetooth toggle skipped: Shizuku permission not available")
            }
        }
    }

    // ---- ContentResolver helpers -------------------------------------------

    private fun ContentResolver.putSystemIntIfChanged(key: String, value: Int) {
        if (Settings.System.getInt(this, key, -1) != value) {
            Settings.System.putInt(this, key, value)
        }
    }

    private fun ContentResolver.putSecureIntIfChanged(key: String, value: Int) {
        if (Settings.Secure.getInt(this, key, -1) != value) {
            Settings.Secure.putInt(this, key, value)
        }
    }

    private fun ContentResolver.putGlobalIntIfChanged(key: String, value: Int) {
        if (Settings.Global.getInt(this, key, -1) != value) {
            Settings.Global.putInt(this, key, value)
        }
    }

    private fun ContentResolver.putGlobalStringIfChanged(key: String, value: String) {
        if (Settings.Global.getString(this, key) != value) {
            Settings.Global.putString(this, key, value)
        }
    }
}
