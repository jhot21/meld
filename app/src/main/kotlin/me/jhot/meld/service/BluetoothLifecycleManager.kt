package me.jhot.meld.service

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

private const val TAG = "BluetoothLifecycleMgr"
private const val TOGGLE_COOLDOWN_MS = 10_000L

open class BluetoothLifecycleManager(
    private val scope: CoroutineScope,
    private val toggler: BluetoothToggler,
    private val btStateSource: Flow<Int>,
    private val shizukuSource: Flow<Boolean>,
    private val clock: () -> Long = { SystemClock.elapsedRealtime() },
) {

    private val _desired = MutableStateFlow<Boolean?>(null)
    private var lastToggleMs: Long = -TOGGLE_COOLDOWN_MS
    private var lastToggleDir: Boolean? = null

    init {
        scope.launch {
            combine(btStateSource, shizukuSource, _desired) { btState, shizuku, desired ->
                Triple(btState, shizuku, desired)
            }.collect { (btState, shizuku, desired) ->
                if (desired == null) return@collect
                if (!shizuku) return@collect
                if (btState != BluetoothAdapter.STATE_ON && btState != BluetoothAdapter.STATE_OFF) return@collect
                val isOn = btState == BluetoothAdapter.STATE_ON
                if (desired == isOn) return@collect

                val withinCooldown = clock() - lastToggleMs < TOGGLE_COOLDOWN_MS
                val sameDirection = desired == lastToggleDir
                if (withinCooldown && sameDirection) {
                    Log.w(TAG, "setBluetooth($desired) skipped — within ${TOGGLE_COOLDOWN_MS / 1000}s cooldown")
                    return@collect
                }

                lastToggleMs = clock()
                lastToggleDir = desired
                try {
                    val ok = toggler.setBluetooth(desired)
                    if (ok) {
                        _desired.value = null  // one-shot: applied once, step back and let user control BT
                    } else {
                        Log.w(TAG, "setBluetooth($desired) failed")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "setBluetooth($desired) threw", e)
                }
            }
        }
    }

    open fun setDesired(enabled: Boolean?) {
        _desired.value = enabled
    }
}

fun btAdapterStateFlow(context: Context): Flow<Int> = callbackFlow {
    val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    trySend(adapter?.state ?: BluetoothAdapter.STATE_OFF)

    val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val state = intent.getIntExtra(
                BluetoothAdapter.EXTRA_STATE,
                BluetoothAdapter.STATE_OFF,
            )
            trySend(state)
        }
    }
    ContextCompat.registerReceiver(
        context,
        receiver,
        IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
        ContextCompat.RECEIVER_NOT_EXPORTED,
    )
    awaitClose {
        try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
    }
}

fun shizukuAvailableFlow(): Flow<Boolean> = callbackFlow {
    trySend(ShizukuGranter.hasPermission())

    val receivedListener = Shizuku.OnBinderReceivedListener { trySend(true) }
    val deadListener = Shizuku.OnBinderDeadListener { trySend(false) }

    Shizuku.addBinderReceivedListenerSticky(receivedListener)
    Shizuku.addBinderDeadListener(deadListener)

    awaitClose {
        Shizuku.removeBinderReceivedListener(receivedListener)
        Shizuku.removeBinderDeadListener(deadListener)
    }
}
