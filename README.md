# SpeedSense

SpeedSense is an offline Android app that watches GPS updates in a foreground service, matches the user's position to the nearest road from a bundled Bedford road dataset, and plays a unique vibration pattern when the detected speed limit changes.

## Features

- Continuous high-accuracy location tracking with `FusedLocationProviderClient`
- Foreground service with persistent notification for background monitoring
- Bundled Bedford road dataset loaded from `assets/roads.json`
- Spatially indexed nearest-road matching with point-to-segment distance checks
- Speed-limit-specific vibration patterns using `VibrationEffect.createWaveform`
- Minimal UI to start and stop monitoring and show the latest detected road and speed limit

## Build Instructions

1. Open the project root in Android Studio.
2. Let Android Studio install the Android SDK components it requests.
3. Sync Gradle when prompted.
4. Connect a physical Android device with GPS and vibration hardware enabled.
5. Build the APK with `Build > Build Bundle(s) / APK(s) > Build APK(s)`.
6. Install the generated APK from `app/build/outputs/apk/debug/`.

## Optional Command-Line Build

- Windows: `gradlew.bat assembleDebug`
- macOS/Linux: `./gradlew assembleDebug`

## Testing

1. Start monitoring from the main screen.
2. Use Android Studio's device emulator controls or a physical device route replay to simulate movement along the roads in `roads.json`.
3. Verify that the speed limit label changes only when moving onto a road with a different speed limit.
4. Confirm that the vibration pattern changes per speed limit and does not repeat while staying on the same limit.
