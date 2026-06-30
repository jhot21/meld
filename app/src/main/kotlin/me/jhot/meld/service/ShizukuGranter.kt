package me.jhot.meld.service

import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
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
    private val mainHandler = Handler(Looper.getMainLooper())

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
     * Binds the Shizuku UserService, calls AudioSystem.setStreamVolumeIndex via reflection
     * to set STREAM_MUSIC volume below AudioService's Java-layer safe volume check, then unbinds.
     * Must be called from a coroutine (suspends until the service responds).
     */
    suspend fun setMediaVolumeDirect(volume: Int): Boolean =
        withShizukuService { it.setMediaVolumeDirect(volume) }

    /**
     * Binds the Shizuku UserService, calls putSettings for all entries in [changes],
     * then unbinds. One bind/unbind cycle regardless of list size.
     * Must be called from a coroutine.
     */
    suspend fun putSettings(changes: List<SettingChange>): Boolean =
        withShizukuService { it.putSettings(changes) }

    /**
     * Acquires the lock, binds the Shizuku UserService, runs [block], then unbinds.
     *
     * Both bindUserService and unbindUserService are dispatched to the main thread via
     * Handler.post. Shizuku iterates its internal connection HashMap on the main thread when
     * delivering connected callbacks (ShizukuServiceConnection.lambda$connected$0). Calling
     * either bind or unbind from Dispatchers.Default while that iteration is in progress
     * modifies the map mid-iteration and causes ConcurrentModificationException. Handler.post
     * ensures both operations run as fresh main-looper messages, after the current iteration
     * completes.
     */
    private suspend fun withShizukuService(block: (IShizukuService) -> Unit): Boolean =
        lock.withLock {
            var connection: ServiceConnection? = null
            var unbindPosted = false
            val result = runCatching {
                withTimeout(SHIZUKU_TIMEOUT_MS) {
                    suspendCancellableCoroutine { cont ->
                        // Runs [block] at most once per connection. Shizuku delivers
                        // onServiceConnected to EVERY connection still registered for these
                        // serviceArgs whenever the service (re)connects. If a previous call's
                        // connection was not removed in time, a later bind re-delivers
                        // onServiceConnected to it — which would re-run that call's stale block
                        // (e.g. an old keyboard_vibration value) against the new service
                        // instance. The guard makes such a re-delivery a no-op and unbinds the
                        // stale connection so it stops being re-delivered.
                        val handled = java.util.concurrent.atomic.AtomicBoolean(false)
                        val conn = object : ServiceConnection {
                            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                                if (!handled.compareAndSet(false, true)) {
                                    mainHandler.post {
                                        connection?.let {
                                            try { Shizuku.unbindUserService(serviceArgs, it, true) } catch (_: Exception) {}
                                        }
                                    }
                                    return
                                }
                                val success = try {
                                    block(IShizukuService.Stub.asInterface(binder))
                                    true
                                } catch (e: Exception) {
                                    false
                                }
                                if (cont.isActive) cont.resume(success)
                            }

                            override fun onServiceDisconnected(name: ComponentName) {
                                if (cont.isActive) cont.resume(false)
                            }
                        }
                        connection = conn
                        cont.invokeOnCancellation {
                            unbindPosted = true
                            mainHandler.post {
                                try { Shizuku.unbindUserService(serviceArgs, conn, true) } catch (_: Exception) {}
                            }
                        }
                        // Post bind to main thread so it never races with Shizuku's HashMap
                        // iteration. The connected callback is delivered on the main thread;
                        // calling bindUserService from a background thread while that iteration
                        // is in progress causes ConcurrentModificationException.
                        mainHandler.post {
                            try {
                                Shizuku.bindUserService(serviceArgs, conn)
                            } catch (e: Exception) {
                                if (cont.isActive) cont.resume(false)
                            }
                        }
                    }
                }
            }
            if (!unbindPosted) {
                connection?.let { conn ->
                    mainHandler.post {
                        try { Shizuku.unbindUserService(serviceArgs, conn, true) } catch (_: Exception) {}
                    }
                }
            }
            result.getOrDefault(false)
        }
}
