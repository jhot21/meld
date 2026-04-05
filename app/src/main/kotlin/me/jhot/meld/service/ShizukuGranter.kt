package me.jhot.meld.service

import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import me.jhot.meld.IShizukuService
import rikka.shizuku.Shizuku
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

private const val SHIZUKU_TIMEOUT_MS = 5_000L

object ShizukuGranter {

    // Serializes all bind/unbind cycles so concurrent Shizuku calls don't race each other.
    private val lock = Mutex()

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
    suspend fun grantSecureSettings(packageName: String): Boolean = lock.withLock {
        runCatching {
            withTimeout(SHIZUKU_TIMEOUT_MS) {
                suspendCancellableCoroutine { cont ->
                    val connection = object : ServiceConnection {
                        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                            val success = try {
                                IShizukuService.Stub.asInterface(binder).grantSecureSettings(packageName)
                                true
                            } catch (e: Exception) {
                                false
                            }
                            try { Shizuku.unbindUserService(serviceArgs, this, true) } catch (_: Exception) {}
                            if (cont.isActive) cont.resume(success)
                        }

                        override fun onServiceDisconnected(name: ComponentName) {
                            if (cont.isActive) cont.resume(false)
                        }
                    }
                    try {
                        Shizuku.bindUserService(serviceArgs, connection)
                    } catch (e: Exception) {
                        if (cont.isActive) cont.resume(false)
                    }
                }
            }
        }.getOrDefault(false)
    }

    /**
     * Binds the Shizuku UserService, runs `cmd bluetooth_manager enable/disable`, then unbinds.
     * Must be called from a coroutine (suspends until the service responds).
     */
    suspend fun setBluetooth(enable: Boolean): Boolean = lock.withLock {
        runCatching {
            withTimeout(SHIZUKU_TIMEOUT_MS) {
                suspendCancellableCoroutine { cont ->
                    val connection = object : ServiceConnection {
                        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                            val success = try {
                                IShizukuService.Stub.asInterface(binder).setBluetooth(enable)
                                true
                            } catch (e: Exception) {
                                false
                            }
                            try { Shizuku.unbindUserService(serviceArgs, this, true) } catch (_: Exception) {}
                            if (cont.isActive) cont.resume(success)
                        }

                        override fun onServiceDisconnected(name: ComponentName) {
                            if (cont.isActive) cont.resume(false)
                        }
                    }
                    try {
                        Shizuku.bindUserService(serviceArgs, connection)
                    } catch (e: Exception) {
                        if (cont.isActive) cont.resume(false)
                    }
                }
            }
        }.getOrDefault(false)
    }

    /**
     * Binds the Shizuku UserService, runs `settings put <namespace> <key> <value>`, then unbinds.
     * Must be called from a coroutine (suspends until the service responds).
     */
    suspend fun putSetting(namespace: SettingNamespace, key: String, value: Int): Boolean = lock.withLock {
        runCatching {
            withTimeout(SHIZUKU_TIMEOUT_MS) {
                suspendCancellableCoroutine { cont ->
                    val connection = object : ServiceConnection {
                        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                            val success = try {
                                IShizukuService.Stub.asInterface(binder).putSetting(namespace.value, key, value)
                                true
                            } catch (e: Exception) {
                                false
                            }
                            try { Shizuku.unbindUserService(serviceArgs, this, true) } catch (_: Exception) {}
                            if (cont.isActive) cont.resume(success)
                        }

                        override fun onServiceDisconnected(name: ComponentName) {
                            if (cont.isActive) cont.resume(false)
                        }
                    }
                    try {
                        Shizuku.bindUserService(serviceArgs, connection)
                    } catch (e: Exception) {
                        if (cont.isActive) cont.resume(false)
                    }
                }
            }
        }.getOrDefault(false)
    }
}
