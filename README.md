# WeightWatcher Android

A simple Android home screen widget app that tracks when your last weight log in Health Connect was.

## Features

- **1x1 Home Screen Widget**: Displays the number of hours since your last recorded weight.
- **Dynamic Indicators**: Shows a big green ✅ if you've logged your weight within the last 20 hours, and a red ❌ if it's been longer.
- **Background Sync**: Uses Android Jetpack `WorkManager` & the Health Connect Changelog API to seamlessly update. It polls the changelog instead of blindly fetching everything.
- **Instant Refresh**: Tap anywhere on the widget to trigger an immediate background refresh.

## Project Structure

- `flake.nix` & `direnv`: Nix environment ensuring hermetic, reproducible Android dev tools (JDK 21, Android SDK 35).
- `app/src/main/java/.../MainActivity.kt`: A modern Jetpack Compose Activity handling the `READ_WEIGHT` permission request cleanly.
- `app/src/main/java/.../WeightWidgetProvider.kt`: Implements `AppWidgetProvider` for rendering the native XML `weight_widget_layout.xml` on the launcher.
- `app/src/main/java/.../WeightWorker.kt`: A Coroutine Worker executing periodic updates using `HealthConnectClient`. It fetches a `ChangesToken`, safely iterates Health Connect `UpsertionChange`, and updates the local shared preferences.
- `app/src/test/`: Contains automated unit tests.

## Local Development (Nix)

This project strictly utilizes **Nix Flakes** for isolation.

1. Ensure Nix and `direnv` are installed. We recommend using [nix-direnv](https://github.com/nix-community/nix-direnv) for better performance. Run `direnv allow` to automatically provision Java & Gradle.
2. The `flake.nix` configures `ANDROID_SDK_ROOT` to standard patched binaries for NixOS.
3. To start the Nix-provisioned emulator:
   - Run `nix develop` to enter the shell.
   - Run `emulator-run`. This will create a "weightwatcher" AVD if it doesn't exist and start it.
4. To build the APK:
   - Run `gradle assembleDebug` (or `nix develop --command gradle assembleDebug`).
   - The APK will be located at `app/build/outputs/apk/debug/app-debug.apk`.
5. To install on a connected device/emulator:
   - Run `gradle installDebug` (or `nix develop --command gradle installDebug`).

## License

This project is licensed under the Mozilla Public License 2.0. See the `LICENSE` file for details.
