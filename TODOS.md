# Remaining TODOs

Tracked after the 15-prompt implementation. Paths are from the repo root.

## Playback

- [ ] Per-request HTTP headers (User-Agent / Referer / Origin) are collected but not yet attached to the OkHttp `DataSource` used by the current item. Wire `DefaultHttpDataSource.Factory.setDefaultRequestProperties` inside `PlayerManager.play` / `zap`.  
  `app/src/main/java/com/nova/iptv/data/player/PlayerManager.kt`
- [ ] AFR currently logs the preferred `Display.Mode` and sets `videoChangeFrameRateStrategy`. Actual `setPreferredDisplayModeId` on the Activity window is still a safe no-op on many sticks.  
  `PlayerManager.kt`, `MainActivity.kt`
- [ ] Audio / subtitle track pickers in the player side sheet are stubs (they close the sheet). Bind to `Tracks` + `TrackSelectionOverride`.  
  `app/src/main/java/com/nova/iptv/ui/player/PlayerScreen.kt`
- [ ] 120 ms zap crossfade is not animated (prepare-in-place only).
- [ ] Timeshift toast (“Provider does not support timeshift”) is not shown as a Snackbar yet.  
  `PlayerScreen.kt`

## Playlists / Xtream

- [ ] `PlaylistImporter.refresh` re-imports under a new id then remaps. Pass the existing playlist id into `import*` to avoid the shuffle.  
  `app/src/main/java/com/nova/iptv/data/playlist/PlaylistImporter.kt`
- [ ] Xtream `get_series_info` / `get_vod_info` is declared on `XtreamApi` but Detail does not yet fetch plot/cast on open (Room cache is used if present).  
  `app/src/main/java/com/nova/iptv/ui/vod/VodScreens.kt`
- [ ] TMDB fallback when `posterUrl` is blank and a key is set in settings is not implemented.  
  `AppSettings.tmdbKey`
- [ ] `get_short_epg` / `get_simple_data_table` ingest path is not wired (XMLTV + demo cover the guide).  
  `app/src/main/java/com/nova/iptv/data/remote/XtreamApi.kt`

## EPG / Guide

- [ ] Guide cells use fractional `fillMaxWidth` + `offset`; a custom layout measuring `pxPerHour` would be more accurate at 2/12-hour windows.  
  `app/src/main/java/com/nova/iptv/ui/guide/GuideScreen.kt`
- [ ] Mini preview in the guide corner (settings.preview) is not mounted — Home owns the only preview player.  
  `GuideScreen.kt`
- [ ] `XmltvChannelDao` has no `listAll`; auto-match falls back to vowel searches. Add `SELECT * FROM xmltv_channels`.  
  `app/src/main/java/com/nova/iptv/data/local/dao/Daos.kt`

## Recordings

- [ ] `RecordingService` copies the HTTP body as TS. HLS master playlists will need a segment follower / Media3 `DataSink` / Transformer.  
  `app/src/main/java/com/nova/iptv/data/recordings/RecordingService.kt`
- [ ] Programme Info → Remind is not hooked to `ReminderWorker.schedule`.  
  `GuideScreen.kt`, `ReminderWorker.kt`
- [ ] Delete-watched completed files is not scheduled.

## Multi-view / Search

- [ ] Long-press channel picker UI (favorites list overlay) is flagged (`pickerFor`) but the picker composable is not drawn.  
  `app/src/main/java/com/nova/iptv/ui/multiview/MultiViewScreen.kt`
- [ ] Search IME should request the leanback keyboard explicitly on some OEM images.

## Settings / backup

- [ ] Restore reapplies playlist metadata only, not channel rows (user must “Update now”).  
  `app/src/main/java/com/nova/iptv/data/backup/BackupManager.kt`
- [ ] Clear image + EPG cache action is not bound to a button handler.
- [ ] Locked-groups editor (checkbox list) is missing; groups can be locked only via DataStore / demo Adult flag.

## Visual / store

- [ ] Bundle `Outfit-Regular.ttf` / `Outfit-Medium.ttf` under `app/src/main/res/font/` (network was unavailable in CI). Theme currently uses `FontFamily.SansSerif`.  
  `app/src/main/java/com/nova/iptv/ui/theme/Theme.kt`
- [ ] Raster 320×180 banner + 512 icon + 1080×1920 poster should be exported from `store/` into `res/drawable` / Play Console. Vector banner ships today.  
  `app/src/main/res/drawable/tv_banner.xml`
- [ ] Extract remaining hardcoded English in Settings rows into `strings.xml`.  
  `app/src/main/java/com/nova/iptv/ui/settings/SettingsScreen.kt`

## Build / QA

- [ ] Commit `gradle/wrapper/gradle-wrapper.jar` (generate with Android Studio or `gradle wrapper`).  
  `gradle/wrapper/`
- [ ] Add a `:baselineprofile` module that records Home + Player on a rooted emulator instead of the static `baseline-prof.txt`.
- [ ] Hilt `HiltTestApplication` for instrumentation tests that touch `@Inject` ViewModels.  
  `app/src/androidTest/`
- [ ] 10k-channel scroll fps is manual-only (no Macrobenchmark yet).
