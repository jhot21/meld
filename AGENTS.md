# AGENTS.md — Meld

## Project overview

Meld is an Android mode manager for automation tools. Users define named collections of device settings ("modes") and activate/deactivate them. Meld tracks which modes are active, resolves conflicts by priority, and applies the correct combined settings.

- **minSdk:** 31 (Android 12)
- **targetSdk:** 35
- **Language:** Kotlin
- **UI:** Jetpack Compose + Material 3
- **Database:** Room (two databases: `meld.db` for modes, `active.db` in device-protected storage for active state)
- **Build:** Gradle Kotlin DSL, version catalog at `gradle/libs.versions.toml`
- **Tooling:** [mise](https://mise.jdx.dev/) for env management, [Task](https://taskfile.dev/) for task automation

## Building

```sh
./gradlew assembleDebug     # debug APK
./gradlew assembleRelease   # release APK (needs signing key)
```

## Running tests

```sh
./gradlew :app:test                    # unit tests
./gradlew :app:connectedAndroidTest    # instrumented tests (needs device/emulator)
```

All unit tests are in `app/src/test/` (run on host JVM). Instrumented tests are in `app/src/androidTest/` (run on device).

## Architecture

```
app/src/main/kotlin/me/jhot/meld/
├── data/
│   ├── db/          # Room databases, DAOs
│   │   ├── dao/     # ModeDao, ActiveModeDao
│   │   ├── MeldDatabase.kt
│   │   └── ActiveDatabase.kt
│   └── model/       # Entities: Mode, ActiveMode, ModeSettings, ModeType
├── domain/          # Pure business logic
│   └── ModeResolver.kt
├── service/         # Foreground service, settings applier, permissions, Shizuku
│   ├── MeldForegroundService.kt
│   ├── ModeRepository.kt
│   ├── SettingsApplier.kt
│   ├── PermissionChecker.kt
│   └── ...
├── receiver/        # Broadcast receivers (ntfy, intents, boot)
├── tasker/          # Tasker plugin bridge (actions, events, conditions)
├── ui/              # Compose screens and ViewModels
│   ├── modeList/
│   ├── modeEditor/
│   ├── globalSettings/
│   ├── components/
│   ├── navigation/
│   └── theme/
└── MeldApplication.kt  # DI container (manual, no DI framework)
```

### Key patterns

- **Manual DI:** `MeldApplication` creates all dependencies. ViewModels use `ViewModelProvider.Factory` with `APPLICATION_KEY` to access the Application.
- **Coroutines:** `MeldApplication.applicationScope` (`SupervisorJob + Dispatchers.Default`) for app-level work. ViewModels use `viewModelScope`. Services create their own `CoroutineScope`.
- **State driving:** The foreground service observes `ModeRepository.modesWithActiveState` as a Flow and applies settings via `SettingsApplier`.
- **Mode types:** `DEFAULT` (baseline settings), `PRIMARY` (mutually exclusive — highest priority wins), `SECONDARY` (always additive, conflicts resolved by priority).

### Testing conventions

- Unit tests use **JUnit 4**, **MockK** for mocking, and **kotlinx-coroutines-test** for coroutines.
- Test classes mirror the source package structure in `app/src/test/`.
- Instrumented tests use Room's in-memory database helper (`room-testing`) and MockK Android.
- Unit test config forces `isReturnDefaultValues = true` because Shizuku's static initializer touches `android.os.Binder`.
