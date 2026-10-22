# Database and migrations

Vyllo stores downloads, playlists, history, alarms, ratings, and the independent
local library in the Room database `downloads_db`. The current schema is
**version 8**. Exported schema snapshots live in
`app/schemas/com.vyllo.music.data.download.DownloadDatabase/`.

## Current tables

| Table | Purpose |
|---|---|
| `downloads` | Download metadata and worker status |
| `playlists` | User playlist identity and custom cover/crop settings |
| `playlist_songs` | Playlist membership and stable custom ordering |
| `playback_history` | Recent plays and listening-stat counters |
| `alarms` | Scheduled alarm configuration |
| `favorites` | Signed like/dislike rating state |
| `local_library_songs` | Explicitly saved tracks and editable title/artist/album metadata |

Local-library membership is separate from `favorites`: users can save a track
without liking it, and album collections are derived from nonblank album tags
on saved tracks.

## Adding a schema change

1. Update the Room entity/DAO and add a forward-only `Migration` in
   `app/src/main/java/com/vyllo/music/data/download/RoomMigrations.kt`.
2. Increment `@Database(version = ...)` and register the migration in
   `DownloadDatabase.getInstance()`.
3. Add the new entity's migration behavior to
   `DownloadDatabaseMigrationTest`; preserve existing rows and define defaults
   for newly introduced non-null fields.
4. Run KSP/compilation to export the new schema JSON, inspect it, and commit it
   alongside the migration. Do not hand-edit schema snapshots to make a test
   pass.
5. Verify the unit/lint/debug build gate and compile Android instrumentation
   tests. Run the connected migration tests when an emulator or device is
   available.

Avoid destructive migrations or `fallbackToDestructiveMigration()`. A schema
snapshot, migration, and migration test are one change and should stay in
lockstep. Keep data access in repositories; view models should use use cases or
repository contracts rather than DAOs.

## Migration history

- **1→2:** playlist tables.
- **2→3:** playback history.
- **3→4:** alarms.
- **4→5:** like/dislike ratings in `favorites`.
- **5→6:** playback count and listened-time statistics.
- **6→7:** playlist cover URL and crop focal point.
- **7→8:** local-library songs and album/artist indexes.

The executable SQL and exact exported table definitions are authoritative; this
summary is a quick orientation, not a replacement for those migration sources.
