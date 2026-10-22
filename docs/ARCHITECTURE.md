# Architecture

Vyllo is a single-module Android app. The `app` module contains the Compose UI,
playback service, domain code, persistence adapters, and integrations. The
package structure separates responsibilities without adding module boundaries
that would make the current app harder to change.

## Main boundaries

| Area | Location | Responsibility |
|---|---|---|
| Screens and reusable UI | `app/src/main/java/com/vyllo/music/ui` and `presentation` | Compose screens, player surfaces, dialogs, and design components |
| Navigation | `VylloNavigation.kt` | Routes, shared view models, and cross-screen callbacks |
| Domain | `domain/model`, `domain/repository`, `domain/usecase` | App models, repository contracts, and user-operation orchestration |
| Data and integrations | `data` | Repository implementations, Room DAOs, network clients, preferences, and provider adapters |
| Playback | `service`, `service/audio`, `domain/manager/player` | Media3 player lifecycle, queue coordination, audio processing, and session commands |
| Background work | `service` and WorkManager workers | Downloads and other work that outlives a screen |
| Dependency injection | `di` and feature-specific `*/di` packages | Hilt bindings and application-scoped dependencies |

`MusicItem` is the shared playback/search item. Local-library metadata is kept
separately in `LocalLibrarySong`; saving a song there does not change its like
rating. Playlist songs and their ordering are persisted independently as well.

## Playback flow

1. Compose screens call a view model with a `MusicItem` or a playback action.
2. `PlayerViewModel` coordinates screen state, lyrics, playback preferences, and
   queue-facing operations. It delegates queue mutations to
   `PlaybackQueueManager` and playback starts to use cases.
3. `MusicService` owns the Media3 `ExoPlayer` and `MediaSession`. It resolves
   stream data through the repository, maintains the foreground playback
   lifecycle, and attaches audio effects to the current audio session.
4. Queue state and playhead state are persisted through their dedicated
   coordinators. Player notifications and widgets observe the service session.

Keep the service as the owner of the physical player. UI code should issue
commands through the existing view-model/session paths rather than constructing
another player or reaching into service internals.

## Data flow

- **Remote catalogue/playback:** `IMusicRepository` is implemented by
  `MusicRepositoryImpl`, which composes the YouTube data source and specialized
  repository delegates.
- **Playlists and local library:** repository implementations use Room DAOs;
  view models use domain use cases, not DAOs.
- **Downloads:** `DownloadRepository` schedules `DownloadWorker`; the worker
  resolves and downloads media off the UI thread and records status in Room.
- **Lyrics:** `LyricsEngine` tries enabled sources in the user-configured order.
  Parsed line/word timing is carried into the player presentation models.
- **Preferences:** `PreferenceManager` owns persisted playback and feature
  preferences. Keep new settings typed and give them a safe default.

## UI conventions

- Prefer screen-level state in view models and transient presentation state in
  Compose. Avoid putting high-frequency playback ticks into large screen state
  objects if only a small player surface needs them.
- Reuse `VylloSpacing`, `VylloRadius`, and `VylloSize` design tokens for new UI.
- Keep empty, loading, failure, and populated states explicit. Network-backed
  screens should offer a retry path when appropriate.
- Add user-visible strings to both `values/strings.xml` and
  `values-es/strings.xml`.

## Verification

The standard local gate is:

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:compileDebugAndroidTestKotlin
```

Room migration instrumentation tests are in
`app/src/androidTest/java/com/vyllo/music/data/download/DownloadDatabaseMigrationTest.kt`.
Run `:app:connectedDebugAndroidTest` with an emulator/device to execute them.
