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

    // ---- WRITE_SECURE_SETTINGS settings -------------------------------------

    private fun applySecureSettings(settings: ModeSettings) {
        val resolver = context.contentResolver

        settings.darkMode?.let {
            resolver.putSecureIntIfChanged("ui_night_mode", if (it) 2 else 1)
        }

        settings.nightLight?.let {
            resolver.putSecureIntIfChanged("night_display_activated", if (it) 1 else 0)
        }

        settings.extraDim?.let { enable ->
            // reduce_bright_colors_activated is restricted to system apps on Android 12+;
            // direct read/write via Settings.Secure will fail — fall back to Shizuku.
            val value = if (enable) 1 else 0
            try {
                Settings.Secure.putInt(resolver, "reduce_bright_colors_activated", value)
            } catch (e: SecurityException) {
                if (ShizukuGranter.hasPermission()) {
                    applicationScope.launch {
                        val ok = ShizukuGranter.putSetting(SettingNamespace.SECURE, "reduce_bright_colors_activated", value)
                        if (!ok) Log.w(TAG, "Shizuku fallback for reduce_bright_colors_activated failed")
                    }
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
            if (Settings.System.getInt(resolver, KEY_KEYBOARD_VIBRATION, -1) != value) {
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

        settings.bluetooth?.let { enable ->
            val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE)
                    as? android.bluetooth.BluetoothManager)?.adapter
            if (adapter == null || adapter.isEnabled != enable) {
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
        // getString returns null for unset keys; null != any non-null value, so a
        // missing key always triggers the write — which is the correct behaviour.
        if (Settings.Global.getString(this, key) != value) {
            Settings.Global.putString(this, key, value)
        }
    }
}
