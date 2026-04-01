package me.jhot.meld.service

import me.jhot.meld.IShizukuService

/**
 * Runs inside Shizuku's process with ADB-level permissions.
 * Launched via Shizuku.bindUserService — not started directly by the app.
 */
class ShizukuService : IShizukuService.Stub() {

    override fun grantSecureSettings(packageName: String) {
        Runtime.getRuntime()
            .exec(arrayOf("pm", "grant", packageName, "android.permission.WRITE_SECURE_SETTINGS"))
            .waitFor()
    }

    override fun destroy() {
        System.exit(0)
    }
}
