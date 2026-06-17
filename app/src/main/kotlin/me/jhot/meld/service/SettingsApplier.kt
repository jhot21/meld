package me.jhot.meld.service

import android.app.NotificationManager
import android.content.Context
import android.content.ContentResolver
import android.media.AudioManager
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
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
    private val pendingSecureSettings = Channel<List<SettingChange>>(Channel.CONFLATED)
    private val pendingMediaVolume = Channel<Int>(Channel.CONFLATED)

    init {
        applicationScope.launch {
            for (changes in pendingSecureSettings) {
                try {
                    val ok = ShizukuGranter.putSettings(changes)
                    if (!ok) Log.w(TAG, "Shizuku putSettings failed for ${changes.size} change(s)")
                } catch (e: Exception) {
                    Log.e(TAG, "Unexpected error in putSettings consumer", e)
                }
            }
        }
        applicationScope.launch {
            for (volume in pendingMediaVolume) {
                try {
                    val ok = ShizukuGranter.setMediaVolumeDirect(volume)
                    if (!ok) Log.w(TAG, "Shizuku setMediaVolumeDirect($volume) failed")
                } catch (e: Exception) {
                    Log.e(TAG, "Unexpected error in setMediaVolumeDirect consumer", e)
                }
            }
        }
    }

    fun apply(settings: ModeSettings) {
        applyMediaVolumeWithOverride(settings)
        if (permissionChecker.canWriteSettings()) {
            applyWriteSettings(settings)
        }
        if (permissionChecker.canWriteNotificationPolicy()) {
            applyDnd(settings)
        }
        val shizukuChanges = mutableListOf<SettingChange>()
        if (permissionChecker.canWriteSecureSettings()) {
            shizukuChanges += applySecureSettings(settings)
        }
        if (shizukuChanges.isNotEmpty()) {
            pendingSecureSettings.trySend(shizukuChanges)
        }
    }

    // ---- Media volume override logic ----------------------------------------

    private fun applyMediaVolumeWithOverride(settings: ModeSettings) {
        if (!permissionChecker.canWriteSettings()) return

        val overrideActive = settings.volumeMediaOverride == true

        when {
            !overrideActive && overrideSessionStore.isActive() -> {
                overrideSessionStore.clear()
                settings.volumeMedia?.let { setMediaVolume(it) }
            }
            overrideActive && !overrideSessionStore.isActive() -> {
                settings.volumeMedia?.let { setMediaVolume(it) }
                overrideSessionStore.setActive()
            }
            overrideActive && overrideSessionStore.isActive() -> {
            }
            else -> {
                settings.volumeMedia?.let { setMediaVolume(it) }
            }
        }
    }

    private fun setMediaVolume(volume: Int) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        if (ShizukuGranter.hasPermission()) {
            if (am.getStreamVolume(AudioManager.STREAM_MUSIC) == volume) return
            pendingMediaVolume.trySend(volume)
            return
        }
        if (am.getStreamVolume(AudioManager.STREAM_MUSIC) != volume) {
            am.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
        }
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

        settings.hapticFeedback?.let {
            resolver.putSystemIntIfChanged(Settings.System.HAPTIC_FEEDBACK_ENABLED, if (it) 1 else 0)
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
            if (nm.currentInterruptionFilter != filter) nm.setInterruptionFilter(filter)
        }
    }

    // ---- WRITE_SECURE_SETTINGS settings — returns pending Shizuku writes ----

    private fun applySecureSettings(settings: ModeSettings): List<SettingChange> {
        val resolver = context.contentResolver
        val pending = mutableListOf<SettingChange>()

        settings.darkMode?.let {
            resolver.putSecureIntIfChanged("ui_night_mode", if (it) 2 else 1)
        }

        settings.nightLight?.let {
            resolver.putSecureIntIfChanged("night_display_activated", if (it) 1 else 0)
        }

        settings.extraDim?.let { enable ->
            val value = if (enable) 1 else 0
            try {
                Settings.Secure.putInt(resolver, "reduce_bright_colors_activated", value)
            } catch (e: SecurityException) {
                if (ShizukuGranter.hasPermission()) {
                    pending += SettingChange("secure", "reduce_bright_colors_activated", value)
                }
            }
        }

        settings.immersiveMode?.let {
            val value = when (it) {
                ImmersiveMode.OFF -> ""
                ImmersiveMode.STATUS_BAR -> "immersive.status=*"
                ImmersiveMode.NAV_BAR -> "immersive.navigation=*"
                ImmersiveMode.BOTH -> "immersive.full=*"
            }
            resolver.putGlobalStringIfChanged("policy_control", value)
        }

        settings.grayscale?.let {
            val enabledValue = if (it) 1 else 0
            resolver.putSecureIntIfChanged("accessibility_display_daltonizer_enabled", enabledValue)
            if (it && Settings.Secure.getInt(resolver, "accessibility_display_daltonizer", -1) != 0) {
                Settings.Secure.putInt(resolver, "accessibility_display_daltonizer", 0)
            }
        }

        settings.keyboardVibration?.let { enabled ->
            val value = if (enabled) 1 else 0
            try {
                if (Settings.System.getInt(resolver, KEY_KEYBOARD_VIBRATION, -1) != value) {
                    Settings.System.putInt(resolver, KEY_KEYBOARD_VIBRATION, value)
                }
            } catch (e: Exception) {
                if (ShizukuGranter.hasPermission()) {
                    pending += SettingChange("system", KEY_KEYBOARD_VIBRATION, value)
                }
            }
        }

        settings.batterySaver?.let {
            resolver.putGlobalIntIfChanged("low_power", if (it) 1 else 0)
        }

        settings.locationMode?.let {
            val value = when (it) {
                LocationMode.OFF -> 0
                LocationMode.BATTERY_SAVER -> 2
                LocationMode.DEVICE_ONLY -> 1
                LocationMode.HIGH_ACCURACY -> 3
            }
            resolver.putSecureIntIfChanged("location_mode", value)
        }

        return pending
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
