# Remaining TODOs

Tracked after the 15-prompt implementation. Paths are from the repo root.

## Playback

- [x] Per-request HTTP headers (User-Agent / Referer / Origin) are attached to the OkHttp data source in `PlayerManager.play` / `zap`.  
  `app/src/main/java/com/nova/iptv/data/player/PlayerManager.kt`
- [x] AFR selects a preferred `Display.Mode`, sets `videoChangeFrameRateStrategy`, and applies `preferredDisplayModeId` to the Activity window.  
  `PlayerManager.kt`, `MainActivity.kt`
- [x] Audio / subtitle track pickers are bound to `Tracks` + `TrackSelectionOverride`.  
  `app/src/main/java/com/nova/iptv/ui/player/PlayerScreen.kt`
- [x] Zap uses a 120 ms animated fade-through-black transition.
- [x] Unsupported timeshift actions show a timed in-player Snackbar message.  
  `PlayerScreen.kt`

## Playlists / Xtream

- [x] `PlaylistImporter.refresh` passes the existing playlist ID into each import path.  
  `app/src/main/java/com/nova/iptv/data/playlist/PlaylistImporter.kt`
- [x] Detail fetches and caches Xtream movie/series metadata and series episodes on open.  
  `app/src/main/java/com/nova/iptv/ui/vod/VodScreens.kt`
- [x] TMDB fallback fills blank movie/series artwork when a key is configured and caches the result.  
  `AppSettings.tmdbKey`
- [x] Xtream `get_simple_data_table` / `get_short_epg` ingestion is wired with bounded concurrency and Room persistence.  
  `app/src/main/java/com/nova/iptv/data/remote/XtreamApi.kt`

## EPG / Guide

- [x] Guide cells use a custom measured timeline layout with exact window clipping and pixel placement.  
  `app/src/main/java/com/nova/iptv/ui/guide/GuideScreen.kt`
- [x] Guide mounts and lifecycle-manages a mini preview when `settings.preview` is enabled.  
  `GuideScreen.kt`
- [x] `XmltvChannelDao.listAll()` powers XMLTV auto-match without search heuristics.  
  `app/src/main/java/com/nova/iptv/data/local/dao/Daos.kt`

## Recordings

- [x] `RecordingService` follows HLS master/media playlists and appends initialization maps and deduplicated segments.  
  `app/src/main/java/com/nova/iptv/data/recordings/RecordingService.kt`
- [x] Programme Info → Remind is hooked to `ReminderWorker.schedule`.  
  `GuideScreen.kt`, `ReminderWorker.kt`
- [x] Completed recording playback deletes the file and database row when “Delete watched” is enabled.

## Multi-view / Search

- [x] Long-press channel picker displays favorites and replaces the selected tile.  
  `app/src/main/java/com/nova/iptv/ui/multiview/MultiViewScreen.kt`
- [x] Search explicitly requests the TV software keyboard with text/search IME options.

## Settings / backup

- [x] Restore refreshes playlist rows, reapplies favorites, passwords, and backed-up settings.
  `app/src/main/java/com/nova/iptv/data/backup/BackupManager.kt`
- [x] Clear image + EPG cache is bound to a Settings action.
- [x] Parental settings includes a locked-groups toggle list.

## Visual / store

- [x] Bundle the Outfit variable font under `res/font` and map regular/medium theme weights to it.  
  `app/src/main/java/com/nova/iptv/ui/theme/Theme.kt`
- [x] Raster banner, icon, and poster from `store/` are copied into `res/drawable` (Play Console upload remains a release operation).  
  `app/src/main/res/drawable/tv_banner.xml`
- [x] Settings row and diagnostics labels are sourced from `strings.xml`.  
  `app/src/main/java/com/nova/iptv/ui/settings/SettingsScreen.kt`

## Build / QA

- [x] `gradle/wrapper/gradle-wrapper.jar` is present.  
  `gradle/wrapper/`
- [x] Add a `:baselineprofile` module that records Home + Player and replaces the static profile.
- [x] Instrumentation tests use a runner backed by `HiltTestApplication`.  
  `app/src/androidTest/`
- [x] A Macrobenchmark measures frame timing while scrolling a benchmark-only 10k-channel demo catalog.
