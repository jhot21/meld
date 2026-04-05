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
     * Unbind is intentionally deferred until after the coroutine resumes — never called from
     * inside onServiceConnected — to avoid ConcurrentModificationException in Shizuku's
     * internal connection iterator. invokeOnCancellation handles cleanup on timeout so
     * orphaned ServiceConnection registrations don't accumulate across calls.
     */
    private suspend fun withShizukuService(block: (IShizukuService) -> Unit): Boolean =
        lock.withLock {
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
                                // Do NOT call unbindUserService here: Shizuku is currently
                                // iterating its connection map to deliver this callback, and
                                // modifying the map mid-iteration causes ConcurrentModificationException.
                                // Unbind happens below, after the coroutine resumes.
                                if (cont.isActive) cont.resume(success)
                            }

                            override fun onServiceDisconnected(name: ComponentName) {
                                if (cont.isActive) cont.resume(false)
                            }
                        }
                        connection = conn
                        // Clean up on timeout/cancellation so the ServiceConnection doesn't
                        // accumulate in Shizuku's map and cause CME on the next bind.
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
            // Unbind after the coroutine has fully resumed, outside the callback stack.
            connection?.let {
                try { Shizuku.unbindUserService(serviceArgs, it, true) } catch (_: Exception) {}
            }
            result.getOrDefault(false)
        }
}
