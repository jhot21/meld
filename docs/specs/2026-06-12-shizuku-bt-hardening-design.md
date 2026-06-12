# Shizuku BT Debounce, AudioHardening Fix, and Batch putSettings

**Date:** 2026-06-12
**Status:** Approved

## Background

Logcat analysis revealed three interrelated problems:

1. `BluetoothLifecycleManager` calls `toggler.setBluetooth()` immediately on every `STATE_ON` emission with no cooldown. When the BT stack crashes and restarts (cycling through STATE_OFF → STATE_ON every ~3 seconds), Meld immediately tries to disable BT again, crashing the stack again. The `com.google.android.bluetooth` process died `fg SVC` (service crash) 8+ times in ~90 seconds.

2. Android 16's `AudioHardening` silently drops `AudioManager.setStreamVolume` calls from background apps (`level: full`). Meld's media volume changes were being ignored entirely for non-max volumes.

3. Each Shizuku-backed settings write goes through its own full `bind → exec subprocess → unbind` cycle via `withShizukuService`. A mode apply that touches multiple Shizuku-restricted settings (e.g., `keyboard_vibration_enabled`, `reduce_bright_colors_activated`) causes repeated Shizuku UserService bind/unbind cycles, contributing to the Shizuku manager provider being restarted and eventually crashing.

## Change 1: BT Debounce in `BluetoothLifecycleManager`

**File:** `app/src/main/kotlin/me/jhot/meld/service/BluetoothLifecycleManager.kt`

Add a `lastToggleMs: Long` field initialized to `0L` and a companion constant `TOGGLE_COOLDOWN_MS = 10_000L`.

In the `collect` block, after all existing guards (null desired, no Shizuku, transitional BT state, already at desired state) and before calling `toggler.setBluetooth()`, add:

```
if (SystemClock.elapsedRealtime() - lastToggleMs < TOGGLE_COOLDOWN_MS) {
    Log.d(TAG, "setBluetooth($desired) skipped — within cooldown")
    return@collect
}
```

On reaching the `toggler.setBluetooth()` call (regardless of its return value), record `lastToggleMs = SystemClock.elapsedRealtime()`.

**Cooldown:** 10 seconds. Acceptable lag per user confirmation; gives the BT stack time to fully initialize before any subsequent toggle attempt.

## Change 2: AudioHardening Fix in `SettingsApplier`

**File:** `app/src/main/kotlin/me/jhot/meld/service/SettingsApplier.kt`

Replace the current `setMediaVolume()` implementation. The Shizuku path becomes first-class for all volumes, not just max:

```
private fun setMediaVolume(volume: Int) {
    val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
    if (ShizukuGranter.hasPermission()) {
        if (am.getStreamVolume(AudioManager.STREAM_MUSIC) == volume) return
        applicationScope.launch {
            val ok = ShizukuGranter.setMediaVolumeDirect(volume)
            if (!ok) Log.w(TAG, "Shizuku setMediaVolumeDirect($volume) failed")
        }
        return
    }
    if (am.getStreamVolume(AudioManager.STREAM_MUSIC) != volume) {
        am.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
    }
}
```

The `getStreamVolume` equality check is preserved in both branches to avoid unnecessary calls.

## Change 3: Batch `putSettings`

### 3a. New `SettingChange` Parcelable

**New file:** `app/src/main/kotlin/me/jhot/meld/service/SettingChange.kt`

```kotlin
@Parcelize
data class SettingChange(val namespace: String, val key: String, val value: Int) : Parcelable
```

**New file:** `app/src/main/aidl/me/jhot/meld/SettingChange.aidl`

```aidl
package me.jhot.meld;
parcelable SettingChange;
```

### 3b. AIDL Interface

**File:** `app/src/main/aidl/me/jhot/meld/IShizukuService.aidl`

- Remove `putSetting(String namespace, String key, int value)`
- Add `putSettings(in List<SettingChange> settings)`

### 3c. `ShizukuService`

**File:** `app/src/main/kotlin/me/jhot/meld/service/ShizukuService.kt`

- Remove `putSetting()` method
- Add `putSettings(settings: List<SettingChange>)` that iterates the list and calls `exec("settings", "put", entry.namespace, entry.key, entry.value.toString())` for each entry. Same subprocess per key as before — batched inside a single service session.

### 3d. `ShizukuGranter`

**File:** `app/src/main/kotlin/me/jhot/meld/service/ShizukuGranter.kt`

- Remove `suspend fun putSetting(namespace: SettingNamespace, key: String, value: Int): Boolean`
- Add `suspend fun putSettings(changes: List<SettingChange>): Boolean` wrapping one `withShizukuService { it.putSettings(changes) }` call

### 3e. `SettingsApplier` Restructure

**File:** `app/src/main/kotlin/me/jhot/meld/service/SettingsApplier.kt`

Change `applySecureSettings` signature to return `List<SettingChange>` (only this method has Shizuku fallbacks — `applyWriteSettings` uses `WRITE_SETTINGS`-gated APIs and needs no change). `applySecureSettings` builds a `MutableList<SettingChange>` and appends to it wherever a Shizuku fallback was previously launched inline (on `SecurityException` / `Exception` from the direct API). The inline `applicationScope.launch { ShizukuGranter.putSetting(...) }` calls are removed.

`apply()` takes the returned list and, if non-empty, fires a single:

```kotlin
applicationScope.launch {
    val ok = ShizukuGranter.putSettings(allChanges)
    if (!ok) Log.w(TAG, "Shizuku putSettings failed for ${allChanges.size} change(s)")
}
```

One `withShizukuService` bind/unbind cycle per mode apply regardless of how many settings need Shizuku.

## Files Changed

| File | Change |
|------|--------|
| `service/BluetoothLifecycleManager.kt` | Add debounce cooldown |
| `service/SettingsApplier.kt` | Shizuku-first volume + batch collection |
| `service/SettingChange.kt` | New parcelable |
| `aidl/me/jhot/meld/SettingChange.aidl` | New AIDL declaration |
| `aidl/me/jhot/meld/IShizukuService.aidl` | Replace putSetting with putSettings |
| `service/ShizukuService.kt` | Replace putSetting with putSettings |
| `service/ShizukuGranter.kt` | Replace putSetting with putSettings |

## Out of Scope

- Replacing subprocess spawning (`exec("settings", "put", ...)`) with direct ContentProvider calls — `ShizukuService` has no `Context`
- Configurable cooldown duration
- Notification volume or other stream types through the Shizuku path
