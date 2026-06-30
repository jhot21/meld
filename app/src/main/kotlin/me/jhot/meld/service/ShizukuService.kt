package me.jhot.meld.service

import android.annotation.SuppressLint
import me.jhot.meld.IShizukuService

/**
 * Runs inside Shizuku's process with ADB-level permissions.
 * Launched via Shizuku.bindUserService — not started directly by the app.
 */
class ShizukuService : IShizukuService.Stub() {

    override fun grantSecureSettings(packageName: String) {
        exec("pm", "grant", packageName, "android.permission.WRITE_SECURE_SETTINGS")
    }

    override fun putSettings(settings: List<me.jhot.meld.service.SettingChange>) {
        for (change in settings) {
            require(change.namespace in setOf("system", "secure", "global")) {
                "Invalid settings namespace: ${change.namespace}"
            }
            exec("settings", "put", change.namespace, change.key, change.value.toString())
        }
    }

    // Reflection targets a hidden API (setStreamVolumeIndexAS). This runs inside the Shizuku
    // service process as ADB UID (2000), which is exempt from hidden-API restrictions at runtime.
    @SuppressLint("BlockedPrivateApi")
    override fun setMediaVolumeDirect(volume: Int) {
        // Only reaches here when AudioService's safe-media-volume cap prevented am.setStreamVolume
        // from reaching the target (e.g. headphones plugged in). Strategy:
        //  1. Disable the safe-volume gate via Settings.Global so AudioService stops capping.
        //  2. Wait for AudioService's ContentObserver to pick up the change (~100 ms).
        //  3. Set the native volume directly via AudioSystem reflection; now AudioService won't
        //     fight it back because its safe-volume state is disabled.
        //  4. Restore the safe-volume state so the user's normal protection is not permanently lost.
        //
        // ADB UID (what Shizuku runs as) is exempt from hidden API restrictions.
        // Android 16 added a 'muted' boolean between index and device; try 4-param first.
        try {
            // Step 1: read current safe-volume state so we can restore it.
            val prevState = exec("settings", "get", "global", "safe_media_volume_state").trim()
            val restoreValue = if (prevState == "null" || prevState.isEmpty()) "0" else prevState

            // Step 2: disable safe-media-volume (SAFE_MEDIA_VOLUME_DISABLED = 1).
            exec("settings", "put", "global", "safe_media_volume_state", "1")
            try {
                // Step 3: give AudioService's ContentObserver time to fire (~100 ms is enough).
                Thread.sleep(150)

                // Step 4: set native volume directly via reflection.
                val cls = Class.forName("android.media.AudioSystem")
                val streamMusic = 3
                val invoker: (Int) -> Unit = try {
                    val m = cls.getDeclaredMethod(
                        "setStreamVolumeIndexAS",
                        Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType,
                        Boolean::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType,
                    )
                    val fn: (Int) -> Unit = { device -> m.invoke(null, streamMusic, volume, false, device) }
                    fn
                } catch (_: NoSuchMethodException) {
                    val m = cls.getDeclaredMethod(
                        "setStreamVolumeIndexAS",
                        Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType,
                    )
                    val fn: (Int) -> Unit = { device -> m.invoke(null, streamMusic, volume, device) }
                    fn
                }
                for (device in OUTPUT_DEVICES) {
                    try { invoker(device) } catch (_: Exception) {}
                }
            } finally {
                // Step 5: always restore safe-volume state, even if an exception occurred above.
                exec("settings", "put", "global", "safe_media_volume_state", restoreValue)
            }
        } catch (e: Exception) {
            android.util.Log.w("ShizukuService", "setMediaVolumeDirect failed: ${e.message}")
        }
    }

    override fun destroy() {
        System.exit(0)
    }

    companion object {
        // Common output device type constants (AudioSystem / AudioManager).
        // These mirror the DEVICE_OUT_* values in android.media.AudioSystem.
        // We iterate all of them so whichever is currently active (wired headset,
        // BT A2DP, BLE, USB, speaker, etc.) gets the full volume index.
        private val OUTPUT_DEVICES = intArrayOf(
            0x1,        // DEVICE_OUT_EARPIECE
            0x2,        // DEVICE_OUT_SPEAKER
            0x4,        // DEVICE_OUT_WIRED_HEADSET
            0x8,        // DEVICE_OUT_WIRED_HEADPHONE
            0x80,       // DEVICE_OUT_BLUETOOTH_A2DP
            0x100,      // DEVICE_OUT_BLUETOOTH_A2DP_HEADPHONES
            0x200,      // DEVICE_OUT_BLUETOOTH_A2DP_SPEAKER
            0x400000,   // DEVICE_OUT_USB_HEADSET
            0x2000000,  // DEVICE_OUT_BLE_HEADSET
            0x4000000,  // DEVICE_OUT_BLE_BROADCAST
        )
    }

    /**
     * Executes a shell command and waits for it to finish.
     *
     * stdout and stderr are merged and drained on the same thread before waitFor().
     * This prevents pipe-buffer deadlocks — e.g. `cmd bluetooth_manager enable` always
     * prints at least two lines to stdout, and `pm grant` prints output on both success
     * and failure. Calling waitFor() without draining those streams can cause the child
     * process to block once the 64 KB pipe buffer fills, hanging the binder call
     * indefinitely (only rescued by the Shizuku 5 s timeout, which returns false and
     * silently skips the setting).
     */
    private fun exec(vararg cmd: String): String =
        ProcessBuilder(*cmd)
            .redirectErrorStream(true)
            .start()
            .run {
                val output = inputStream.readBytes().toString(Charsets.UTF_8)
                waitFor()
                output
            }
}
