# Tasker State & Event Plugins Design

**Date:** 2026-04-01
**Status:** Approved

## Overview

Expose Meld mode state to Tasker via three new plugin entry points:

1. **Condition plugin** — "Mode State" — a profile stays active while the selected mode is active (or inactive, configurable).
2. **Event plugin** — "Mode Activated" — fires once when a mode enters the active set.
3. **Event plugin** — "Mode Deactivated" — fires once when a mode leaves the active set.

All three support selecting a specific mode or "Any Mode" (blank selection). Events output `%meld_mode_name` and `%meld_mode_type` so a generic task can identify the triggering mode.

---

## Architecture

```
ActiveModeDao (Flow<Set<Long>>)
        ↓
  TaskerBridge          ← observes active mode changes (Application scope coroutine)
     ↓         ↓
  fires events   sends requestQuery
     ↓                  ↓
ModeActivatedRunner  ModeStateRunner   ← Tasker queries for condition evaluation
ModeDeactivatedRunner
```

`ModeRepository` is not modified. `TaskerBridge` is the only new observer of `ActiveModeDao`.

---

## New Components

All new files live in `app/src/main/kotlin/me/jhot/meld/tasker/`.

| File | Type | Purpose |
|---|---|---|
| `ModeStateActivity` | Config Activity | Condition plugin UI: mode picker + Active/Inactive toggle |
| `ModeStateRunner` | Condition Runner | Evaluates whether selected mode matches configured state direction |
| `ModeActivatedActivity` | Config Activity | Event plugin UI: mode picker (+ Any Mode) |
| `ModeActivatedRunner` | Event Runner | Fires when a mode is added to the active set |
| `ModeDeactivatedActivity` | Config Activity | Event plugin UI: mode picker (+ Any Mode) |
| `ModeDeactivatedRunner` | Event Runner | Fires when a mode is removed from the active set |
| `TaskerBridge` | Observer | Collects ActiveModeDao flow, diffs sets, drives runners and requestQuery |
| `ModeEventOutput` | Data class | `@TaskerOutputVariable` fields for name and type |
| `ModeStateInput` | Data class | Input bundle: `modeName: String`, `stateDirection: StateDirection` |

`StateDirection` is a simple enum: `ACTIVE`, `INACTIVE`.

"Any Mode" is represented as an empty string in the `modeName` field of the input bundle.

The existing `ModeNameInput` remains unchanged; the new `ModeStateInput` adds the `stateDirection` field.

---

## Config UI (shared pattern)

All three config activities share the same UI structure:

- **Mode picker** — dropdown populated from `ModeDao.getAll()`, with "Any Mode" prepended as the first item.
- **Condition only** — **State Direction toggle** — "Active" / "Inactive" (default: Active).

This mirrors the existing `AddToContextActivity` / `RemoveFromContextActivity` pattern.

---

## Data Flow

### Condition — queried by Tasker

```
Tasker → queries ModeStateRunner
  → reads ActiveModeDao.getActiveModeIds() (suspend, one-shot)
  → if specific mode: resolves modeName → modeId via ModeDao.getByName()
      checks modeId ∈ activeIds
  → if "Any Mode": checks activeIds.isNotEmpty()
  → applies stateDirection:
      ACTIVE  → return result as-is
      INACTIVE → return result.inverted
  → returns TaskerPluginResultConditionSatisfied or Unsatisfied
```

### Events — pushed by Meld via TaskerBridge

```
TaskerBridge starts in MeldApplication.onCreate() (applicationScope)
  → collects ActiveModeDao.getActiveModeIds() as Flow
  → first emission: store as previousIds, skip diff (no spurious deactivations on start)
  → subsequent emissions:
      added   = currentIds - previousIds → look up Mode by ID
      removed = previousIds - currentIds → look up Mode by ID
      for each added mode:
        fire ModeActivated event broadcast with ModeEventOutput(meld_mode_name, meld_mode_type)
        Tasker routes the broadcast to each profile using ModeActivatedRunner;
        the runner receives the event output + the profile's configured ModeNameInput
        and returns whether the mode name matches (or input is empty = Any Mode)
      for each removed mode:
        same pattern via ModeDeactivatedRunner
      send requestQuery broadcast → Tasker immediately re-evaluates all ModeState conditions
      store currentIds as previousIds
```

---

## Output Variables

Set on both event plugins and available for use in the triggered task:

| Variable | Type | Example |
|---|---|---|
| `%meld_mode_name` | String | `"Work"` |
| `%meld_mode_type` | String | `"PRIMARY"` or `"SECONDARY"` |

---

## Manifest Changes

Three new `<activity>` entries in `AndroidManifest.xml`:

```xml
<!-- Condition: Mode State -->
<activity
    android:name=".tasker.ModeStateActivity"
    android:exported="true"
    android:label="@string/tasker_condition_mode_state">
    <intent-filter>
        <action android:name="com.twofortyfouram.locale.intent.action.EDIT_CONDITION" />
        <category android:name="com.twofortyfouram.locale.intent.category.PLUGIN_CONDITION" />
    </intent-filter>
</activity>

<!-- Event: Mode Activated -->
<!-- NOTE: Verify correct intent action/category for joaomgcd event plugins during implementation.
     The EDIT_SETTING / PLUGIN_SETTING pair is used for action plugins; event plugins may differ. -->
<activity
    android:name=".tasker.ModeActivatedActivity"
    android:exported="true"
    android:label="@string/tasker_event_mode_activated">
    <intent-filter>
        <action android:name="com.twofortyfouram.locale.intent.action.EDIT_SETTING" />
        <category android:name="com.twofortyfouram.locale.intent.category.PLUGIN_SETTING" />
    </intent-filter>
</activity>

<!-- Event: Mode Deactivated -->
<activity
    android:name=".tasker.ModeDeactivatedActivity"
    android:exported="true"
    android:label="@string/tasker_event_mode_deactivated">
    <intent-filter>
        <action android:name="com.twofortyfouram.locale.intent.action.EDIT_SETTING" />
        <category android:name="com.twofortyfouram.locale.intent.category.PLUGIN_SETTING" />
    </intent-filter>
</activity>
```

---

## String Resources

New entries needed in `res/values/strings.xml`:

```xml
<string name="tasker_condition_mode_state">Mode State</string>
<string name="tasker_event_mode_activated">Mode Activated</string>
<string name="tasker_event_mode_deactivated">Mode Deactivated</string>
```

---

## TaskerBridge Lifecycle

`TaskerBridge` is instantiated in `MeldApplication` and launched in `applicationScope`. It does not need to be a bound service — it runs as a coroutine for the lifetime of the process. If the process is killed and restarted, it skips the first emission to avoid spurious events.

---

## Out of Scope

- Tasker variables for mode priority or settings values.
- Notification to Tasker of settings changes (only mode active state is exposed).
- Support for automation tools other than Tasker (Macrodroid, etc.) — those already work via the existing `meld.intent.SET_MODE` broadcast API.
