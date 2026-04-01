package me.jhot.meld.service

import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import me.jhot.meld.IShizukuService
import rikka.shizuku.Shizuku
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

object ShizukuGranter {

    private val serviceArgs = Shizuku.UserServiceArgs(
        ComponentName("me.jhot.meld", ShizukuService::class.java.name)
    ).daemon(false).processNameSuffix("shizuku_service").version(1)

    fun isAvailable(): Boolean = try {
        Shizuku.pingBinder()
    } catch (e: Exception) {
        false
    }

    fun hasPermission(): Boolean = isAvailable() &&
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED

    fun requestPermission(requestCode: Int) = Shizuku.requestPermission(requestCode)

    /**
     * Binds the Shizuku UserService, runs `pm grant WRITE_SECURE_SETTINGS`, then unbinds.
     * Must be called from a coroutine (suspends until the service responds).
     */
    suspend fun grantSecureSettings(packageName: String): Boolean =
        suspendCoroutine { cont ->
            val connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                    val success = try {
                        IShizukuService.Stub.asInterface(binder).grantSecureSettings(packageName)
                        true
                    } catch (e: Exception) {
                        false
                    }
                    try { Shizuku.unbindUserService(serviceArgs, this, true) } catch (_: Exception) {}
                    cont.resume(success)
                }

                override fun onServiceDisconnected(name: ComponentName) {
                    cont.resume(false)
                }
            }
            try {
                Shizuku.bindUserService(serviceArgs, connection)
            } catch (e: Exception) {
                cont.resume(false)
            }
        }
}
