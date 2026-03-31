# Meld — Project Synopsis

## What is Meld?

Meld is an Android app that acts as a contextual settings engine for Tasker. Users define "modes" (e.g. home, work, car, night) each with a set of desired phone settings. Tasker profiles detect context changes and tell Meld to activate or deactivate modes. Meld merges all active modes by priority and applies the resolved settings directly to the device.

The project is a evolution of [tasker-phone-modes](https://github.com/jhotmann/tasker-phone-modes), a Tasker JavaScript framework, reborn as a proper Android app with a polished UI.

---

## Architecture

**Meld is the brain. Tasker is just the trigger.**

- Users configure everything inside Meld
- Tasker profiles call Meld intents to activate/deactivate modes
- Meld resolves the merged config and applies settings directly to the device
- No Tasker task XML to import or maintain

---

## Mode System

### Mode Types
- **Primary** — mutually exclusive contexts (home, work, car). Only the highest priority active primary mode is used.
- **Secondary** — overlapping states (night, meeting, headphones). Multiple can be active simultaneously.

### Priority
- Integer 0–100. Higher priority wins when settings conflict.
- Secondary mode settings always override primary mode settings at the same priority level.

### Settings per Mode
Each setting can be explicitly **on**, explicitly **off**, or **unset** (inherit from lower priority / leave unchanged). This three-state model is core to the merging system.

**Supported settings:**

| Setting | Notes |
|---|---|
| Notification volume | |
| Media volume | |
| Media volume override | Prevents context changes from overriding manual volume adjustments |
| Ringer mode | Silent, vibrate, sound |
| Do Not Disturb | Off, priority-only, alarms-only, total silence |
| Display brightness | 0–255 or auto |
| Display timeout | Minutes |
| Screen rotation | On/off |
| Dark mode | |
| Night light | |
| Extra dim | Android 12+ |
| Immersive mode | Off, hide status bar, hide nav bar, hide both |
| Grayscale mode | |
| Haptic feedback | |
| Battery saver | |
| Location mode | Off, high accuracy, battery saver, device only |
| Bluetooth | On/off |

**Intentionally excluded:** WiFi toggle, mobile data toggle (added complexity, rarely needed in practice)

### Enter / Exit Actions
Each mode can define tasks to run and Tasker profiles to enable/disable when the mode is activated or deactivated.

---

## Permissions

Three-step onboarding flow:

1. **`WRITE_SETTINGS`** — standard in-app permission dialog
2. **`ACCESS_NOTIFICATION_POLICY`** — standard in-app permission dialog (for DND)
3. **`WRITE_SECURE_SETTINGS`** — one-time adb command, guided in-app with copy button

---

## Integration Points

### Tasker Plugin
Meld exposes itself as a Tasker plugin. Tasker profiles call `AddToContext` / `RemoveFromContext` actions with a mode name. No XML import required beyond installing the app.

### Exposed Intents (for any app)
- `meld.intent.SET_MODE` — activate or deactivate a named mode
- `meld.intent.QUERY_MODES` — returns all configured modes and their current active state via response broadcast

### ntfy.sh Listener
Optional feature in global settings. When enabled:
- Set a topic filter
- Meld registers a BroadcastReceiver for ntfy's local broadcast intents
- If an incoming message's topic matches the filter and the title matches a mode name, it activates or deactivates that mode
- Truthy values: `enable`, `true`, `on`, `1`
- Falsy values: `disable`, `false`, `off`, `0`
- Ambiguous values are ignored

---

## Data Model

### Database (Room)
Option B — JSON column for settings, structured columns for metadata:

```
modes table:
id | name | type | priority | settings_json | created_at | updated_at
```

Adding new supported settings requires no schema migration — new keys simply appear in the JSON blob. Structured metadata columns (name, type, priority) are what the app actually queries on.

### Export / Import / Backup Format

```json
{
  "meld_version": "1.0",
  "exported_at": "2026-03-29T12:00:00Z",
  "modes": [
    {
      "name": "home",
      "type": 1,
      "priority": 10,
      "settings": {
        "volume_media": 7,
        "dark_mode": true,
        "ringer_mode": "vibrate"
      },
      "enter": {
        "tasksToRun": ["TaskName"],
        "profilesToEnable": ["ProfileName"]
      },
      "exit": {
        "tasksToRun": [],
        "profilesToDisable": ["ProfileName"]
      }
    }
  ]
}
```

- Export format doubles as the config file format
- Version field enables lightweight Kotlin-side migration on import without schema changes
- Easy bug reproduction: "export your config and paste it"

---

## App UI

Use Jetpack Compose for all user-facing screens

### Screens
1. **Mode list** — all modes with name, type badge, priority, and current active state
2. **Mode editor** — full settings form organized into sections:
   - Identity (name, type, priority)
   - Audio (volumes, ringer mode)
   - Display (brightness, timeout, rotation, dark mode, night light, extra dim, immersive, grayscale)
   - System (DND, haptic feedback, battery saver, location, bluetooth)
   - On Enter / On Exit (task runners, profile toggles)
3. **Global settings** — default context, ntfy.sh config, permissions status, import/export/backup

### Key UX Challenge: Three-State Controls
Every setting needs to represent three states: explicitly on, explicitly off, or unset (don't touch). This is core to how mode merging works and needs a consistent, intuitive control pattern throughout the editor. Suggested pattern: tapping cycles `— (unset) → ON → OFF`.

For simple unset/on/off settings, use material design's segmented buttons for these cases.

For cases like volumes and brightness, show a toggle button with a slider underneath. When the toggle is off that setting is considered unset. When the toggle is on, then use the value from the slider.

---

## Tech Stack
- **Language:** Kotlin
- **Database:** Room with JSON type converter
- **Plugin API:** TaskerPlugin library (joaomgcd)
- **Min SDK:** TBD (Android 12+ for extra dim; most features work on earlier versions)

---

## Out of Scope (for now)
- Widget / quick tile for manually toggling modes (nice future addition)
- Tasker variable read/write as a settings type
- Cloud sync of configs
