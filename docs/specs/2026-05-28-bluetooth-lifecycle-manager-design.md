# BluetoothLifecycleManager — Design Spec

**Date:** 2026-05-28  
**Branch:** feat/bluetooth-lifecycle-manager  
**Status:** Approved, pending implementation

---

## Background

Meld applies Bluetooth settings changes via Shizuku (`cmd bluetooth_manager enable/disable`). The
existing approach in `SettingsApplier` launches an `applicationScope.launch` coroutine each time a
mode is applied. A prior bug fix added `btJob?.cancel()` to prevent stale commands from piling up,
but the fundamental issue remains: the code can still call `setBluetooth` while the BT adapter is
in a transitional state (`STATE_TURNING_ON` / `STATE_TURNING_OFF`), which can destabilise the BT
stack.

Two related symptoms were observed in production:
- A Bluetooth loop where the BT stack crashed repeatedly as Meld fired multiple rapid enable
  commands during crash recovery.
- BT occasionally not being toggled at scheduled mode transition times (Shizuku unavailable during
  a Meld crash/restart window).

The fix is a dedicated `BluetoothLifecycleManager` that tracks BT adapter state and only acts when
three conditions are simultaneously true: BT is in a stable state, the desired state differs from
current, and Shizuku is available.

---

## Goals

- Never call `setBluetooth` during `STATE_TURNING_ON` or `STATE_TURNING_OFF`.
- Automatically retry when BT stabilises after a crash (reaches `STATE_OFF` with `desired=true`).
- Automatically retry when Shizuku becomes available after being temporarily unavailable.
- Discard intermediate desired-state updates: only the latest desired state matters when conditions
  allow action.
- When no active mode controls Bluetooth (`settings.bluetooth == null`), the manager goes idle and
  makes no BT calls.

---

## Architecture

### Files changed

| File | Change |
|---|---|
| `service/BluetoothLifecycleManager.kt` | **New.** Core lifecycle manager. |
| `service/BluetoothToggler.kt` | **New.** Single-method interface extracted from `ShizukuGranter` for testability. |
| `service/ShizukuGranter.kt` | **Modified.** Implement `BluetoothToggler`. |
| `service/SettingsApplier.kt` | **Modified.** Remove `btJob`; accept `BluetoothLifecycleManager` as constructor param; call `manager.setDesired(enable)`. |
| `MeldApplication.kt` | **Modified.** Construct `BluetoothLifecycleManager` and wire into `SettingsApplier`. |
| `AndroidManifest.xml` | **Modified if needed.** Add `BLUETOOTH_CONNECT` if `getState()` requires it at runtime on Android 12+. Verify during implementation — the existing `SettingsApplier` already calls `adapter?.state` without this permission with no observed SecurityException. |

### Responsibility boundaries

| Component | Responsibility |
|---|---|
| `BluetoothLifecycleManager` | Own desired BT state; observe adapter state and Shizuku availability; call `BluetoothToggler.setBluetooth` when all conditions align. |
| `BluetoothToggler` | Interface: `suspend fun setBluetooth(enable: Boolean): Boolean`. |
| `ShizukuGranter` | Implement `BluetoothToggler`. No behavioural change. |
| `SettingsApplier` | Translate `ModeSettings.bluetooth` → `manager.setDesired(enable)`. No BT-specific coroutine logic. |
| `ModeRepository` | **Unchanged.** |

`BluetoothLifecycleManager` has no knowledge of modes, Tasker, or the rest of Meld. It is a
pure reactive component: given a desired state and two observable preconditions, act when all three
align.

---

## BluetoothLifecycleManager Internals

### Constructor

```kotlin
class BluetoothLifecycleManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val toggler: BluetoothToggler,
)
```

### Internal flows

**`btStateFlow(): Flow<Int>`**  
A `callbackFlow` that:
1. Emits the current adapter state immediately on collection (initial value).
2. Registers a dynamic `BroadcastReceiver` for `BluetoothAdapter.ACTION_STATE_CHANGED`.
3. Emits `BluetoothAdapter.EXTRA_STATE` on each broadcast.
4. Unregisters the receiver in `awaitClose`.

Registered with `ContextCompat.RECEIVER_NOT_EXPORTED` — system broadcasts do not require
`RECEIVER_EXPORTED`, and this receiver has no reason to be accessible to other apps.

**`shizukuAvailableFlow(): Flow<Boolean>`**  
A `callbackFlow` that:
1. Emits `ShizukuGranter.hasPermission()` immediately as the initial value.
2. Emits `true` via `Shizuku.addBinderReceivedListener`.
3. Emits `false` via `Shizuku.addBinderDeadListener`.
4. Removes both listeners in `awaitClose`.

**`_desired: MutableStateFlow<Boolean?>`**  
Starts as `null`. Updated by `setDesired()`. `MutableStateFlow` conflates intermediate values — if
`setDesired` is called multiple times while BT is transitional, only the latest value is seen by
the collector when BT stabilises.

### Core loop (started in `init`)

```kotlin
combine(btStateFlow(), shizukuAvailableFlow(), _desired) { btState, shizuku, desired ->
    Triple(btState, shizuku, desired)
}.collect { (btState, shizuku, desired) ->
    if (desired == null) return@collect                              // no mode controls BT
    if (!shizuku) return@collect                                     // Shizuku unavailable
    if (btState != STATE_ON && btState != STATE_OFF) return@collect  // transitional — wait
    val isOn = btState == STATE_ON
    if (desired == isOn) return@collect                              // already at target
    try {
        val ok = toggler.setBluetooth(desired)
        if (!ok) Log.w(TAG, "setBluetooth($desired) failed")
    } catch (e: Exception) {
        Log.e(TAG, "setBluetooth($desired) threw", e)
    }
}
```

The five guards are ordered cheapest-first. No else branches, no queuing, no retry loops — the
`MutableStateFlow` conflation and the reactive combine handle all retry and deduplication
implicitly.

### Public surface

```kotlin
fun setDesired(enabled: Boolean?)
```

One method. The manager is otherwise self-contained.

---

## Desired-State Semantics

When `settings.bluetooth == null` (no active mode controls Bluetooth), `SettingsApplier` calls
`setDesired(null)`. The manager goes idle: it holds `null` and makes no further BT calls. Android
owns the BT state from that point. The manager does **not** hold the last explicitly-set value —
clearing to `null` is the correct behaviour when no mode has an opinion.

---

## Rapid `setDesired` Calls During Transition

If `setDesired` is called multiple times while BT is in a transitional state, the
`MutableStateFlow` ensures only the final value is acted upon:

```
BT: STATE_TURNING_OFF

setDesired(true)  → _desired=true  → combine fires, not stable → skip
setDesired(false) → _desired=false → combine fires, not stable → skip
setDesired(true)  → _desired=true  → combine fires, not stable → skip

BT: STATE_OFF → combine fires: desired=true, stable → setBluetooth(true) ✓
```

Direction reversals mid-transition are also handled:

```
desired=true + STATE_OFF → setBluetooth(true) → BT starts TURNING_ON
setDesired(false) while TURNING_ON → combine fires, not stable → skip
BT: STATE_ON → combine fires: desired=false, stable → setBluetooth(false) ✓
```

---

## Error Handling

| Scenario | Behaviour |
|---|---|
| `setBluetooth` returns `false` (Shizuku timeout) | Log warning. Desired state is still held; next combine re-evaluation retries. |
| `setBluetooth` throws | Caught in collect body, logged as error. Same retry behaviour. |
| `btStateFlow` receiver unregister throws | Caught in `awaitClose`. Flow cancels cleanly. |
| `shizukuAvailableFlow` listener registration fails | Flow emits `false`, manager goes dormant until a binder-received event arrives. |
| Unhandled exception in collect | Propagates to `applicationScope` (SupervisorJob — does not tear down other coroutines). |

---

## SettingsApplier Changes

`btJob` field and the `applicationScope.launch` BT block are removed entirely. The
`applySecureSettings` BT handling becomes:

```kotlin
btLifecycleManager.setDesired(settings.bluetooth)
```

`setDesired` is called on **every** `apply()` invocation, passing `null` when `settings.bluetooth`
is unset. This keeps the manager in sync with the current mode resolution — including when a mode
that previously controlled BT is removed from context, which must clear the desired state back to
`null` (idle).

`btLifecycleManager` is a constructor parameter alongside the existing `permissionChecker` and
`overrideSessionStore`.

The `if (ShizukuGranter.hasPermission())` guard that currently gates the BT launch is removed —
Shizuku availability is now handled reactively inside the manager.

---

## MeldApplication Wiring

```kotlin
val bluetoothLifecycleManager: BluetoothLifecycleManager by lazy {
    BluetoothLifecycleManager(this, applicationScope, ShizukuGranter)
}

val settingsApplier: SettingsApplier by lazy {
    SettingsApplier(this, permissionChecker, overrideSessionStore, applicationScope,
                    bluetoothLifecycleManager)
}
```

---

## Testing

### `BluetoothLifecycleManager` unit tests

Inject:
- A fake `BluetoothToggler` that records calls.
- `TestScope` with `UnconfinedTestDispatcher`.
- `MutableStateFlow<Int>` for BT state (replaces `btStateFlow`).
- `MutableStateFlow<Boolean>` for Shizuku availability (replaces `shizukuAvailableFlow`).

Key cases:

| Scenario | Expected |
|---|---|
| BT transitional, desired set, Shizuku available | No call |
| BT reaches `STATE_OFF`, desired=`true`, Shizuku available | `setBluetooth(true)` called once |
| Multiple rapid `setDesired` calls while transitional | Only final value acted on at stabilisation |
| Shizuku becomes available after desired set, BT already stable | `setBluetooth` fires immediately |
| `desired=null` | No calls regardless of BT/Shizuku state |
| `desired` matches current BT state | No calls |
| `setBluetooth` returns `false` | Logged; desired state retained for next retry |

### `SettingsApplier` unit tests

Inject a mock `BluetoothLifecycleManager`. Verify `setDesired` is called with:
- `true` when `settings.bluetooth = true`
- `false` when `settings.bluetooth = false`
- `null` when `settings.bluetooth = null`

---

## Android 16 Compatibility

- `ACTION_STATE_CHANGED` is on the implicit broadcast exemption list — receivable by dynamically
  registered receivers in the background. Meld's foreground service ensures an active process.
- `STATE_ON/OFF/TURNING_ON/TURNING_OFF` constants are unchanged through Android 16.
- Shizuku binder listener APIs have no known incompatibilities with Android 16. GitHub release
  required (v13.6.0+), not Play Store.
- `BLUETOOTH_CONNECT` permission: verify at runtime whether `getState()` throws `SecurityException`
  without it. Add to manifest if needed.
