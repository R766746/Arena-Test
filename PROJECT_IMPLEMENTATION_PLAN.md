# Nova IPTV — Audited Implementation Plan

This plan reconciles the repository audit in `PROJECT_AUDIT_TODO.md` with the
additional `sample/audit_report.md`. It intentionally excludes findings that are
already fixed or contradicted by the current source.

## Comparison outcome

### Adopt immediately — confirmed in current source

1. Persist SAF read permission for local M3U playlists. `AddPlaylistScreen` uses
   `OpenDocument` but does not call `takePersistableUriPermission`; local playlists
   can fail on restart or background refresh.
2. Redact/disable OkHttp BASIC logging in release. Xtream credentials are present
   in request URLs, while the interceptor is configured at BASIC for all variants.
3. Preserve stream headers on Player retry. `PlayerManager.retry()` currently calls
   `zap(..., emptyMap())`, which can break authenticated streams.
4. Use the indexed DAO lookup for numeric channel zap instead of loading the full
   channel snapshot into memory.
5. Remove the duplicate playlist read in `PlaylistUpdateWorker`.
6. Serialize SettingsRepository updates so concurrent updates cannot overwrite each
   other from the same stale snapshot.
7. Make Backup restore report/skip unavailable file-playlist SAF URIs instead of
   aborting the whole restore.
8. Audit backup exclusions, including `nova_vault_fallback`, DataStore settings, and
   the Room database. Keep credentials and sensitive settings out of cloud backup.
9. Add SAF URI permission and recording header/retry integration tests.
10. Improve playlist deletion, sync atomicity, stale episode/EPG cleanup, and
    recording recovery from the new project audit.

### Adopt after verification — plausible but requires device/source tests

1. Splash startup race: add a startup integration test that waits for DataStore and
   Room readiness and proves existing users never see Add Playlist.
2. MultiView lifecycle/budget: current source has an `AndroidView(update = ...)` and
   releases fullscreen before acquiring MultiView, so the reported deadlock/black
   tile is not present as described. Still test Home-button, PiP, Back, and process
   recreation transitions.
3. Recording request headers: current `RecordingService` requests streams without
   provider headers. Define how playlist User-Agent/Referer/Origin are persisted and
   pass the same normalized header policy used by playback.
4. EPG `runBlocking` callbacks: benchmark large XMLTV imports before refactoring;
   then replace with a bounded producer/writer pipeline if it blocks navigation.
5. MultiView tile preservation: inspect current state ownership and test favorite
   table emissions during a configured 2/4-tile session before changing keys.
6. Catch-up/start-over: the builder supports `allowInProgress = true` and Player uses
   it, so this report item is already addressed in the main path. Add provider-backed
   tests for active-program start-over rather than changing the guard blindly.
7. Notification icons and TV dependency upgrades: evaluate on the supported Android
   TV API range before changing dependencies or assets.

### Do not re-implement — contradicted or already fixed

- MultiView `AndroidView` missing `update`: current source already binds
  `update = { it.player = player }`.
- RecordingWorker duplicate foreground notification: current worker schedules the
  service and does not call `setForeground`; verify behavior, but do not add a second
  notification path.
- Room destructive migration/export-schema claim: current database is version 2,
  exports schemas, and has a 1→2 migration. Add future migration tests instead.
- Catch-up universally blocking live start-over: Player calls the builder with
  `allowInProgress = true`.
- MultiView always deadlocking after fullscreen: `createMultiView()` explicitly
  releases fullscreen before acquiring the decoder budget.

## Execution batches

### Batch 1 — Safety and persistence

- Persist SAF URI permission immediately after file selection.
- Add a persisted-URI validation state and a “Choose file again” repair action.
- Fix release logging and add fallback-vault backup exclusions.
- Add tests for restart/background refresh and backup of file playlists.

**Exit criteria:** a local M3U survives process restart, scheduled refresh, and a
backup/restore attempt with a missing source; no release log contains credentials.

### Batch 2 — Stream and recording reliability

- Store/reuse normalized playlist request headers for recording.
- Preserve headers in Player retry and MultiView requests.
- Add recording reconnect/backoff, startup recovery, disk-space handling, and URI
  validation before playback.
- Test Stop/save, Cancel/delete, EOF reconnect, force-stop, reboot, and provider 401/403.

**Exit criteria:** authenticated streams retry successfully, recordings remain
consistent across navigation/restart, and partial files never disappear on Stop.

### Batch 3 — Data ownership and sync integrity

- Add transactional playlist replacement/deletion cleanup for channels, VOD,
  episodes, programs, EPG sources, XMLTV mappings, diagnostics, and orphan history
  according to the chosen retention policy.
- Distinguish failed/empty provider responses from valid empty catalogs.
- Remove duplicate worker queries and stale episode/VOD rows.
- Add Room migration and transaction tests.

**Exit criteria:** interrupted sync leaves the last complete catalog; deletion leaves
no provider-owned orphan rows; valid empty categories behave predictably.

### Batch 4 — Navigation and focus regression

- Add route reachability and Back-stack tests.
- Test every overlay, toolbar, search/sort control, player return path, and held-D-pad
  path with exact focus restoration.
- Verify startup modes do not override the last category/group/item.
- Test MultiView and PiP lifecycle transitions on the target TV.

**Exit criteria:** no focus trap, duplicate Settings stack, skipped held-key action,
or random poster/category restoration in the full navigation matrix.

### Batch 5 — Catalog scale and search

- Benchmark current FTS4 and decide whether FTS5 migration is justified for the
  supported SQLite/Room versions.
- Replace wildcard search paging with indexed search where measured beneficial.
- Optimize numeric zap to DAO lookup and validate Paging invalidation/focus retention.
- Normalize provider/category ordering and multi-genre groups.

**Exit criteria:** first results and focus changes remain responsive on 50k+ channels
and VOD items on a low-RAM TV, with provider order preserved by default.

### Batch 6 — EPG and UI quality

- Add phased EPG progress, cancellation, last-good-cache behavior, and bounded writes.
- Audit all hardcoded strings, content descriptions, focus targets, contrast, image
  placeholders, long titles, Unicode, RTL, phone layout, and font scale.
- Verify empty/loading/error states for every main feature.

**Exit criteria:** EPG refresh never blocks browsing; all interactive controls are
visible, localized, focusable, and recoverable on TV and mobile layouts.

### Batch 7 — Release verification

- Run unit, Room migration, integration, Compose/UI, instrumentation, lint, release
  R8, baseline-profile, and macrobenchmark jobs.
- Test clean install, upgrade from the previous database, offline launch, network
  failure, low storage, process death, and Android TV reboot.
- Review version code/signing, privacy/backup rules, notification channels, assets,
  dependency licenses, and final crash logs.

**Exit criteria:** release APK installs as an upgrade without data loss and passes the
  completed device matrix recorded in `TEST_TODO.md`.

## Recommended first implementation

Start with Batch 1, specifically SAF permission persistence and release credential
logging. They are small, independently testable changes with the highest data and
privacy impact. Do not begin FTS5 or broad UI refactoring until these safety fixes
and the recording regression tests are complete.
