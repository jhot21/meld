package me.jhot.meld.service

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

    override fun setMediaVolumeDirect(volume: Int) {
        // AudioService.java enforces safe media volume in-memory and rejects setStreamVolume
        // calls that exceed the safe threshold. AudioPolicyService (native) has no such check.
        // Calling AudioSystem.setStreamVolumeIndex via reflection bypasses the Java layer
        // entirely and sets the volume directly in the audio policy engine.
        // ADB UID (what Shizuku runs as) is exempt from hidden API restrictions, so
        // setAccessible(true) succeeds and the method is callable without StrictMode blocks.
        try {
            val method = Class.forName("android.media.AudioSystem")
                .getDeclaredMethod(
                    "setStreamVolumeIndex",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                )
                .also { it.isAccessible = true }
            val streamMusic = 3
            // Call for every common output device type. AudioPolicyManager silently ignores
            // device types that are not currently active; calling for all ensures whichever
            // device safe volume is protecting gets the full index.
            for (device in OUTPUT_DEVICES) {
                try { method.invoke(null, streamMusic, volume, device) } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            android.util.Log.w("ShizukuService", "setMediaVolumeDirect reflection failed: ${e.message}")
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
    private fun exec(vararg cmd: String) {
        ProcessBuilder(*cmd)
            .redirectErrorStream(true)
            .start()
            .run {
                inputStream.readBytes()  // drain merged stdout+stderr; blocks until process exits
                waitFor()
            }
    }
}
