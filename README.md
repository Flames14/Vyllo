# Vyllo

[![Android CI](https://github.com/Flames14/Vyllo/actions/workflows/android-ci.yml/badge.svg?branch=main)](https://github.com/Flames14/Vyllo/actions/workflows/android-ci.yml)
[![Platform: Android](https://img.shields.io/badge/platform-Android-3DDC84?logo=android&logoColor=white)](https://www.android.com/)
[![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue.svg)](LICENSE)

Vyllo is an unofficial Android music player that plays audio and video from YouTube and YouTube Music. It combines background playback, an editable queue, downloads, playlists, lyrics, and library tools in a Jetpack Compose interface.

> Vyllo does not host or distribute music. Content availability and playback depend on third-party services. Users are responsible for complying with applicable terms and copyright laws. Vyllo is not affiliated with or endorsed by Google or YouTube.

## Download

Published APKs are available from [GitHub Releases](https://github.com/Flames14/Vyllo/releases). A release is produced by the CI workflow when a `v*` version tag is pushed. The APK built locally from this checkout is not published automatically by a normal branch push.

To install a published APK, download `app-release.apk`, allow installation from the selected source if Android prompts, and open Vyllo. The minimum supported Android version is Android 7.0 (API 24).

## Features

- **Playback:** stream audio or video, continue playback in the background, and control playback from the notification or floating mini-player.
- **Queue:** edit Up Next by moving tracks, removing tracks, and clearing upcoming tracks; queue changes are synchronized with playback.
- **Library:** create local playlists, browse downloads and recently played tracks, remove history entries, and clear listening history.
- **Favorites:** like or dislike tracks with ratings persisted locally across restarts.
- **Offline listening:** download supported tracks for playback without a network connection.
- **Discovery:** search and browse recommendations, with autoplay and sticky-queue preferences.
- **Lyrics and audio:** synced lyrics, equalizer controls, and volume enhancement controls.
- **More tools:** alarms, sleep timer, song recognition, backup and restore, and YouTube playlist import/sync options.
- **Localization and accessibility:** English and Spanish resources, TalkBack descriptions, and retryable loading/error states.

Features depend on the availability and behavior of the upstream services and Android permissions. See [PERMISSIONS.md](PERMISSIONS.md) for the permissions Vyllo requests and their purposes.

## Build from source

### Requirements

- JDK 17
- Android SDK with platform 36 and the required build tools installed
- Gradle 8.11.1 (the included Gradle wrapper downloads and verifies this version)

The project currently uses Kotlin 1.9.24 and Android Gradle Plugin 8.9.1.

### Build and test

Clone the repository and build the debug app:

```bash
git clone https://github.com/Flames14/Vyllo.git
cd Vyllo
./gradlew :app:assembleDebug
```

On Windows, run `gradlew.bat` instead of `./gradlew`.

Run the local verification checks:

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:compileDebugAndroidTestKotlin
```

To run instrumented tests, start an Android emulator or connect a device and run:

```bash
./gradlew :app:connectedDebugAndroidTest
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

### Build a signed release APK

Release builds use R8 minification and resource shrinking. A production release build requires the release signing certificate configured for Vyllo; Gradle verifies the APK signature and zip alignment as part of the build.

For a local build, copy `keystore.local.properties.example` to the git-ignored `keystore.local.properties` and fill in the signing values. Alternatively, provide the corresponding `VYLLO_STOREFILE`, `VYLLO_STOREPASSWORD`, `VYLLO_KEYALIAS`, and `VYLLO_KEYPASSWORD` environment variables. Never commit signing keys, passwords, or local properties.

Then run:

```bash
./gradlew :app:assembleRelease
```

The release APK is written to `app/build/outputs/apk/release/app-release.apk`. The workflow requires signing credentials for published releases. `-PallowDebugRelease` is available only for explicitly requested local/debug-signed packaging; that APK is not suitable for distribution or updating an existing production-signed installation.

## Technology

- Kotlin and Jetpack Compose with Material 3
- AndroidX Media3 / ExoPlayer for playback and media sessions
- Room for local database storage and migrations; DataStore for preferences
- Hilt for dependency injection
- NewPipe Extractor for YouTube media extraction
- OkHttp for networking and Coil for image loading
- WorkManager and foreground services for background work and playback

## Contributing

Bug reports and focused pull requests are welcome. For substantial changes, open an issue first to discuss the intended behavior and implementation. Please run the unit tests and lint checks before submitting a pull request.

## License

Vyllo is distributed under the GNU General Public License v3.0. See [LICENSE](LICENSE).
