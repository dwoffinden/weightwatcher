# WeightWatcher

An Android home screen widget that shows how long ago you last logged a type of health data (weight, heart rate, blood pressure, etc.) in Health Connect.

## Features

- **1x1 Home Screen Widget**: Shows hours since the last record of the widget's data type, with a green check (≤ 20 hours) or red cross (> 20 hours).
- **Multiple Data Types**: Each widget tracks one data type, picked when adding the widget (from the launcher or via the app's pin button) and changeable later by reconfiguring it. Read permission is requested per data type.
- **Incremental Sync**: Polls the Health Connect [Changelog API](https://developer.android.com/reference/kotlin/androidx/health/connect/client/HealthConnectClient#getChanges(kotlin.String)) every 30 minutes via `WorkManager`, with a separate changes token per data type.
- **Tap to Refresh**: Tapping the widget triggers an immediate sync (or opens the permission setup screen if permissions are missing).

## Project Structure

```text
.
├── flake.nix                                    # Nix dev shell & CI environment (Gradle 9, JDK 25, Android SDK 37)
└── app/
    ├── build.gradle.kts                         # App build config and dependencies
    ├── proguard-rules.pro                       # R8 rules for Protobuf Lite and Glance callbacks
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── java/dev/daw/weightwatcher/
        │   │   ├── MainActivity.kt              # Compose UI for picking a data type, requesting permissions, and pinning/configuring widgets
        │   │   ├── dataTypes.kt                 # Supported data types: record classes, names, icons and permissions
        │   │   ├── WeightGlanceWidget.kt        # Jetpack Glance widget UI and per-widget data type state
        │   │   ├── WeightGlanceWidgetReceiver.kt
        │   │   ├── WidgetPinReceiver.kt         # Stores the data type of a widget pinned from the app
        │   │   ├── SyncManager.kt               # Health Connect queries and Proto DataStore persistence
        │   │   ├── WeightWorker.kt              # WorkManager CoroutineWorker for periodic/one-off syncs
        │   │   ├── WeightWatcherApplication.kt  # Hilt application; migrates widgets from older versions
        │   │   ├── PermissionsRationaleActivity.kt
        │   │   ├── AppModule.kt                 # Hilt bindings for DataStore and WorkManager
        │   │   ├── HealthConnectModule.kt       # Hilt bindings for the Health Connect client
        │   │   ├── ProtoSerializer.kt           # Generic DataStore serializer for Protobuf Lite messages
        │   │   ├── Theme.kt                     # Material 3 dynamic color theme
        │   │   └── timestamps.kt                # java.time.Instant <-> protobuf Timestamp conversions
        │   └── proto/
        │       └── sync_settings.proto          # Per-data-type changes token, last record time and sync status
        └── test/java/dev/daw/weightwatcher/
            ├── MainActivityTest.kt              # Robolectric Compose UI tests
            ├── DataTypesTest.kt                 # Data type property tests
            ├── SyncManagerTest.kt               # Sync and change-processing tests
            ├── SyncManagerRecordTimeTest.kt     # Record time extraction for each data type
            ├── WeightGlanceWidgetTest.kt        # Widget staleness and data type resolution
            ├── WeightGlanceWidgetStateTest.kt   # Robolectric tests for per-widget Glance state
            ├── WeightGlanceWidgetReceiverTest.kt
            ├── TimestampsTest.kt                # Instant/Timestamp conversion unit tests
            ├── testRecords.kt                   # Sample Health Connect records for tests
            └── testWidgets.kt                   # Robolectric widget and WorkManager helpers
```

## Building and Development

Builds and dependencies are managed with **Gradle**. Development and CI use **Nix** (`flake.nix`) to provide a pinned toolchain (Gradle 9, JDK 25, Android SDK 37, and emulator) and to generate `gradle.properties` with Nix store paths. No Gradle wrapper (`gradlew`) is checked into the repository so the Gradle version stays in sync with `nixpkgs`.

While building outside Nix isn't officially supported, any recent Gradle 9 installation with JDK 25 and Android SDK 37 (`ANDROID_HOME`) should be able to build the project directly.

### Common Gradle Commands

```bash
# Run unit tests
gradle test

# Build debug or release APKs
gradle assembleDebug
gradle assembleRelease

# Install the debug build onto a connected device or running emulator
gradle installDebug

# To install only to the main profile (skipping work profiles on devices with multiple users):
adb install --user 0 app/build/outputs/apk/debug/app-debug.apk
```

Build outputs are written to:
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- Release APK: `app/build/outputs/apk/release/app-release.apk`

### Using the Nix Dev Shell

1. Enter the development shell with `nix develop` (or run `direnv allow` if using [direnv](https://direnv.net/) / [nix-direnv](https://github.com/nix-community/nix-direnv)).
   - The shell hook sets `ANDROID_HOME`, `ANDROID_SDK_ROOT`, and `JAVA_HOME`, and symlinks `gradle.properties` to the Nix store.
2. Run any of the `gradle` commands above (or single-shot via `nix develop --command gradle test`).
3. To launch the Nix-provisioned Android emulator, run `emulator-run` inside `nix develop`. This creates a `weightwatcher` AVD if needed and boots it.

### Building Without Nix

When running Gradle outside `nix develop` (where `gradle.properties` is not generated), provide your Android SDK path, a JDK 25 installation, and sufficient daemon heap for R8/Lint:

```bash
ANDROID_HOME=~/Android/Sdk \
JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 \
GRADLE_OPTS="-Dorg.gradle.jvmargs=-Xmx4096m" \
  gradle test assembleDebug assembleRelease
```

## License

This project is licensed under the Mozilla Public License 2.0. See [`LICENSE`](LICENSE) for details.
