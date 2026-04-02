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

    override fun putSetting(namespace: String, key: String, value: Int) {
        require(namespace in setOf("system", "secure", "global")) {
            "Invalid settings namespace: $namespace"
        }
        Runtime.getRuntime()
            .exec(arrayOf("settings", "put", namespace, key, value.toString()))
            .waitFor()
    }

    override fun destroy() {
        System.exit(0)
    }
}
