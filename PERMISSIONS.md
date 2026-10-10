# Permissions

Every permission Vyllo declares, why it is needed, where it is used, and how it
is requested. Google Play requires a declaration for each of these; this file is
the source of truth for the Play Console forms and for `PRIVACY_POLICY.md`.

Manifest: `app/src/main/AndroidManifest.xml`.

## Summary

| Permission | Type | Requested | Sensitive / policy-relevant |
| --- | --- | --- | --- |
| `INTERNET` | install-time | — | no |
| `ACCESS_NETWORK_STATE` | install-time | — | no |
| `ACCESS_WIFI_STATE` | install-time | — | no |
| `WAKE_LOCK` | install-time | — | no |
| `VIBRATE` | install-time | — | no |
| `RECEIVE_BOOT_COMPLETED` | install-time | — | no |
| `FOREGROUND_SERVICE` | install-time | — | no |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | install-time | — | no |
| `FOREGROUND_SERVICE_DATA_SYNC` | install-time | — | no |
| `POST_NOTIFICATIONS` | runtime | first launch (alarms) | yes |
| `RECORD_AUDIO` | runtime | voice search only | yes |
| `SCHEDULE_EXACT_ALARM` | special access | on Android 12+ alarm creation | yes |
| `USE_FULL_SCREEN_INTENT` | special access | on Android 14+ alarm creation | yes |
| `SYSTEM_ALERT_WINDOW` | special access | floating player / volume booster | yes |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | special access | opt-in, "keep alarms reliable" | yes |
| `REQUEST_INSTALL_PACKAGES` | special access | in-app update install only | yes |

Runtime permissions are requested through `PermissionHandler`, at the moment the
feature that needs them is invoked — never at first launch. Declining any of
them degrades a single feature; the app does not gate startup on consent.

---

## Install-time permissions

### `INTERNET`
Stream playback, search, lyrics, recommendations, comments, YouTube Music sync,
and in-app update checks. Normal permission — no user prompt.

### `ACCESS_NETWORK_STATE`
Used by `core/network/ConnectivityState.kt` to decide whether to show the
offline banner and the Home error/retry state, and by the download manager to
pause work when the network drops. Normal permission — no user prompt.

### `ACCESS_WIFI_STATE`
Read by `WakeLockManager` and `DownloadWorker` to avoid holding Wi-Fi locks
through network changes while downloads or alarm playback are in flight. Normal
permission — no user prompt.

### `WAKE_LOCK`
Keeps the CPU awake for: alarm playback (`AlarmWakeLockManager`,
`AlarmSchedulerHelper`, `AlarmTriggerService`), download completion
(`DownloadWorker`), and playback continuity across queue operations
(`MusicService`, `PlaybackQueueOrchestrator`). Without it alarms fire while the
device is dozing and playback stops when the screen turns off. Normal permission
— no user prompt.

### `VIBRATE`
Alarm haptics (`AlarmPlaybackController`, `AlarmTriggerService`,
`AlarmTriggerActivity`). Normal permission — no user prompt.

### `RECEIVE_BOOT_COMPLETED`
`AlarmBootReceiver` re-schedules saved alarms after a reboot, and re-schedules
them again on `TIME_SET` / `TIMEZONE_CHANGED` so RTC alarms survive clock and
time-zone changes. The receiver is `exported="true"` and explicitly guarded by
this permission, so only the system can deliver these broadcasts. Normal
permission — no user prompt.

### `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`
Required for `MusicService`, `FloatingWindowService`, and `AlarmTriggerService`,
all declared `foregroundServiceType="mediaPlayback"`. This is what lets music
keep playing with the screen off and lets the alarm sound when the app is not in
the foreground. Normal permission — no user prompt.

### `FOREGROUND_SERVICE_DATA_SYNC`
WorkManager's `SystemForegroundService` is merged with
`foregroundServiceType="dataSync"` so a long download can surface a progress
notification instead of being killed mid-transfer. Normal permission — no user
prompt.

---

## Runtime permissions

### `POST_NOTIFICATIONS` *(Android 13+)*
Rationale: **media playback controls** and **alarm alerts**.

Requested only when the user creates their first alarm (not on first launch) via
`PermissionHandler`. If declined, the in-process alarm still rings while Vyllo
is in the foreground or the screen is on; only the background/system-tray path
is lost. Playback controls continue to work from the lock screen and Bluetooth
targets via the `MediaSession`, which does not depend on this permission.

Play Console declaration: *Notifications — Alarm and media playback*.

### `RECORD_AUDIO` *(microphone)*
Rationale: **on-device voice search** in `SearchScreen` / `RecognitionScreen`.

Requested only when the user taps the microphone button. Declining leaves text
search fully functional. Captured audio stays on-device (recognition is local);
nothing is uploaded. The permission is never requested in the background.

Play Console declaration: *Microphone — voice search*.

---

## Special access (not granted by a normal prompt)

These open a system settings screen or use a system dialog; the user makes the
choice in OS UI. All are optional and each gates exactly one feature.

### `SCHEDULE_EXACT_ALARM` *(Android 12+)*
Rationale: **alarms must fire at the stated minute.**

Used by `AlarmSchedulerManager` with `AlarmManager.setExactAndAllowWhileIdle`.
Without it alarms are inexact (batched, up to ~15 minutes late). The UI
(`AlarmScreen` / `AlarmViewModel`) checks `canScheduleExactAlarms()` and offers
a deep link to the system screen when it is missing; the notification
`notif_exact_alarm_text` tells the user when alarms fell back to inexact timing
after a reboot and offers to re-enable it.

Play Console declaration: *Alarms — exact alarm scheduling*.

### `USE_FULL_SCREEN_INTENT` *(Android 14+)*
Rationale: **the alarm has to be able to take over the screen.**

`AlarmTriggerService` / `AlarmNotificationFactory` attach a full-screen intent so
the alarm UI (`AlarmTriggerActivity`, `showOnLockScreen`, `turnScreenOn`) comes
up even when the device is locked. Declared in the manifest; on Android 14+ the
system grants it by default for alarm apps and the user can revoke it in
settings. If revoked, the alarm still sounds as a high-priority notification.

Play Console declaration: *Alarms — full-screen intent*.

### `SYSTEM_ALERT_WINDOW` *(overlay)*
Rationale: **floating player and the volume-booster overlay.**

`FloatingPlayerManager` draws the draggable mini-player and
`VolumeBoosterOverlay` sits above other apps so volume can be adjusted while
another app is on screen. Checked with `Settings.canDrawOverlays()` at the
moment the user turns the floating player on (`PermissionHandler`), with a deep
link to the system grant screen. Declining simply keeps the player docked inside
Vyllo.

Play Console declaration: *Overlay — floating player*.

### `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
Rationale: **doze would otherwise silence alarms and stop downloads.**

`BatteryOptimizationHelper` offers this as an explicit, user-initiated choice
("keep alarms reliable") — the app never silently requests it. Play normally
restricts this permission to apps whose core function is impaired by doze;
declining it costs nothing except alarm punctuality on aggressive OEMs.

Play Console declaration: *Battery optimisation exemption — alarms and
downloads*.

### `REQUEST_INSTALL_PACKAGES`
Rationale: **the in-app updater hands the downloaded APK to the package
installer.**

`AppUpdateDialog` / `UpdateDownloader` fetch the release APK, verify it with
`ApkInstallValidator` (signature must match Vyllo's own certificate) before the
install intent is fired. The permission is only exercised after the user taps
"Update". The app never installs arbitrary packages.

Play Console declaration: *Package install — self-update*.

---

## What Vyllo deliberately does not request

- **No location.** No location permission appears in the manifest.
- **No contacts, SMS, call log, storage/media or external package visibility.**
  Downloads live in app-private storage and sharing goes out through a
  `FileProvider` with one exported URI per share, so `QUERY_ALL_PACKAGES` and
  broad storage access are not needed.
- **No advertising ID, no `android.permission.ACTIVITY_RECOGNITION`, no health
  or sensor permissions.**
- **No background location.**

## Data touched by permissions

| Permission | Data | Leaves the device? |
| --- | --- | --- |
| `RECORD_AUDIO` | microphone audio during voice search | no — recognition is on-device |
| `INTERNET` | search queries, stream URLs, artwork, lyrics, update check | yes — see `PRIVACY_POLICY.md` |
| `ACCESS_NETWORK_STATE` | network reachability | no |
| `POST_NOTIFICATIONS` | alarm/playback notification content | no |
