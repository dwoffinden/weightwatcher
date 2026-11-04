# WeightWatcher

An Android home screen widget that shows how many hours ago your weight was last recorded in Health Connect.

## Features

- **1x1 Home Screen Widget**: Shows hours since the last weight entry, with a green check (≤ 20 hours) or red cross (> 20 hours).
- **Incremental Sync**: Polls the Health Connect [Changelog API](https://developer.android.com/reference/kotlin/androidx/health/connect/client/HealthConnectClient#getChanges(kotlin.String)) every 30 minutes via `WorkManager`.
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
        │   │   ├── MainActivity.kt              # Compose UI for requesting permissions and pinning the widget
        │   │   ├── WeightGlanceWidget.kt        # Jetpack Glance widget UI and state management
        │   │   ├── WeightGlanceWidgetReceiver.kt
        │   │   ├── SyncManager.kt               # Health Connect queries and Proto DataStore persistence
        │   │   ├── WeightWorker.kt              # WorkManager CoroutineWorker for periodic/one-off syncs
        │   │   ├── ProtoSerializer.kt           # Generic DataStore serializer for Protobuf Lite messages
        │   │   ├── Theme.kt                     # Material 3 dynamic color theme
        │   │   └── timestamps.kt                # java.time.Instant <-> protobuf Timestamp conversions
        │   └── proto/
        │       ├── sync_settings.proto          # Stored changes token and last weight timestamp
        │       └── widget_state.proto           # Per-widget state (timestamp and sync/auth status)
        └── test/java/dev/daw/weightwatcher/
            ├── MainActivityTest.kt              # Robolectric Compose UI test
            ├── TimestampsTest.kt                # Instant/Timestamp conversion unit tests
            └── WeightWorkerTest.kt              # SyncManager change-processing unit tests
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
