package me.jhot.meld.service

import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import me.jhot.meld.IShizukuService
import rikka.shizuku.Shizuku
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
    suspend fun grantSecureSettings(packageName: String): Boolean =
        withShizukuService { it.grantSecureSettings(packageName) }

    /**
     * Binds the Shizuku UserService, runs `cmd bluetooth_manager enable/disable`, then unbinds.
     * Must be called from a coroutine (suspends until the service responds).
     */
    suspend fun setBluetooth(enable: Boolean): Boolean =
        withShizukuService { it.setBluetooth(enable) }

    /**
     * Binds the Shizuku UserService, runs `settings put <namespace> <key> <value>`, then unbinds.
     * Must be called from a coroutine (suspends until the service responds).
     */
    suspend fun putSetting(namespace: SettingNamespace, key: String, value: Int): Boolean =
        withShizukuService { it.putSetting(namespace.value, key, value) }

    /**
     * Acquires the lock, binds the Shizuku UserService, runs [block], then unbinds.
     *
     * Everything runs on Dispatchers.Main. This is required because Shizuku iterates its
     * internal connection HashMap on the main thread when delivering onServiceConnected.
     * Dispatchers.Main (unlike .immediate) always posts to the handler queue, so the code
     * after suspendCancellableCoroutine — including unbindUserService — runs in a fresh
     * handler message, after Shizuku's current iteration has completed. Calling unbindUserService
     * from a background thread (Dispatchers.Default) while the main thread is mid-iteration
     * causes ConcurrentModificationException.
     */
    private suspend fun withShizukuService(block: (IShizukuService) -> Unit): Boolean =
        lock.withLock {
            withContext(Dispatchers.Main) {
                var connection: ServiceConnection? = null
                val result = runCatching {
                    withTimeout(SHIZUKU_TIMEOUT_MS) {
                        suspendCancellableCoroutine { cont ->
                            val conn = object : ServiceConnection {
                                override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                                    val success = try {
                                        block(IShizukuService.Stub.asInterface(binder))
                                        true
                                    } catch (e: Exception) {
                                        false
                                    }
                                    // Dispatchers.Main posts the continuation to the handler queue
                                    // rather than resuming inline, so unbindUserService (below) runs
                                    // in the next handler message — after Shizuku finishes iterating.
                                    if (cont.isActive) cont.resume(success)
                                }

                                override fun onServiceDisconnected(name: ComponentName) {
                                    if (cont.isActive) cont.resume(false)
                                }
                            }
                            connection = conn
                            // On timeout, cancellation fires on the main thread (Dispatchers.Main),
                            // so this unbind also runs on the main thread in a safe context.
                            cont.invokeOnCancellation {
                                try { Shizuku.unbindUserService(serviceArgs, conn, true) } catch (_: Exception) {}
                            }
                            try {
                                Shizuku.bindUserService(serviceArgs, conn)
                            } catch (e: Exception) {
                                if (cont.isActive) cont.resume(false)
                            }
                        }
                    }
                }
                // Runs on main thread in the next handler message, after Shizuku's iterator exits.
                connection?.let {
                    try { Shizuku.unbindUserService(serviceArgs, it, true) } catch (_: Exception) {}
                }
                result.getOrDefault(false)
            }
        }
}
