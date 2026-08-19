# Nova IPTV — Full Project Audit TODO

This is a fresh audit based on a repository-wide review of navigation, data ownership,
playback, recordings, EPG, UI/focus, performance, security, and release configuration.
Items are ordered by risk, not by visual prominence.

## P0 — Correctness and data safety

- [ ] Make playlist refresh/save atomic. `PlaylistRepository.saveImported()` updates the playlist, channels, VOD, episodes, and diagnostics in separate operations. A crash or process kill can leave a mixed old/new library. Use one Room transaction (or a resumable sync state) and expose a recoverable failure state.
- [ ] Make playlist deletion complete. `deletePlaylist()` currently removes channels, VOD, and the playlist, but must also remove that playlist's episodes, EPG sources/XMLTV mappings, programs associated with its channels, and any provider-owned stale sync rows. Decide explicitly whether watch history is retained or purged.
- [ ] Handle empty provider responses safely. A successful Xtream response with an empty VOD/series list currently skips VOD replacement, which can preserve stale content. Distinguish “provider returned empty” from “request failed” and apply the selected policy consistently.
- [ ] Reconcile stale episodes during refresh. Episodes for removed series or no-longer-returned episodes can remain in Room and appear after a later detail open.
- [ ] Add recording recovery on process/device restart. Scan RECORDING rows at startup, detect orphan files, mark interrupted rows as COMPLETED/FAILED with accurate byte size, and avoid duplicate workers.
- [ ] Verify stop/cancel races. Test Stop, Cancel, app force-stop, provider EOF, and deadline completion concurrently; guarantee Stop keeps the file and Cancel removes it without a later service callback recreating the row.
- [ ] Validate every completed recording before Play. If the URI is missing, inaccessible, zero bytes, or deleted externally, show a repair/delete action instead of entering a player error loop.

## P1 — Search, catalog, and EPG architecture

- [ ] Upgrade FTS4 entities to FTS5 (or document why FTS4 is retained). Current `ChannelFts`, `ProgramFts`, and `VodFts` use `@Fts4`; add prefix/tokenization configuration, migration, rebuild, and benchmark search on a 50k+ catalog.
- [ ] Replace `LIKE '%query%'` paging filters with indexed FTS or a normalized search table. Measure first-result latency and allocation rate while typing on a low-RAM TV.
- [ ] Remove or repurpose `HomeViewModel.contentFlow`, which is permanently an empty list while the UI uses separate Paging flows. Avoid maintaining duplicate state that can confuse diagnostics and future features.
- [ ] Verify Paging invalidation after playlist sync, delete, favorite/hide, watchlist, and sort changes. Ensure visible focus is retained when a page is invalidated.
- [ ] Fix catalog ordering rules to preserve provider/category order by default, with explicit alphabetical/year/provider options. Add tests for M3U order and Xtream category order.
- [ ] Normalize genre/category data. `genresCsv` is grouped as a whole string, so multi-genre items can become one combined folder; split normalized genre rows or document the intended behavior.
- [ ] Make EPG source ownership explicit per playlist. Test deleting/replacing a playlist, two sources with overlapping channels, source failure, stale future-program pruning, and manual channel mapping.
- [ ] Remove `runBlocking` from EPG parser callbacks or isolate it behind bounded IO writes; benchmark large XMLTV imports and confirm D-pad navigation never waits on ingestion.
- [ ] Add EPG sync cancellation and progress phases for download, parse, match, write, and completion, including a visible error that preserves the last good cache.

## P1 — Navigation, focus, and feature links

- [ ] Build a route inventory test. Confirm every `Routes` destination has a reachable entry point and a valid Back path; remove or wire dead standalone Movies/Series routes if Home is now the sole browser.
- [ ] Verify startup modes HOME, FAVORITES, GUIDE, and LAST_CHANNEL after returning from Player, Detail, Recordings, Settings, Multi-view, and Add Playlist. Startup mode must not overwrite the last browsed group/focus.
- [ ] Verify Back hierarchy for search, sort popup, dialogs, detail, player controls, EPG assignment, PIN, recording confirmation, and exit confirmation. Each overlay must consume Back before navigation does.
- [ ] Verify focus restoration after Paging invalidation and after returning from movie, series episode, live channel, recording, Multi-view, and settings. Restore the exact item ID where possible, not only an index.
- [ ] Test long-press and held D-pad input at multiple repeat rates. Confirm no skipped focus, duplicate activation, crash, or content-loading work on the key-repeat path.
- [ ] Verify LEFT/RIGHT boundary behavior in all three browsers and in top search/sort controls. No hidden panel or toolbar control should become unreachable.
- [ ] Verify global GUIDE/SEARCH/MENU key shortcuts do not steal keys from text fields, player controls, dialogs, or Multi-view.
- [ ] Verify player-to-preview handoff: returning from fullscreen live playback restores the same preview/channel and does not create a blank preview or restart unexpectedly.
- [ ] Verify series detail always chooses the first playable episode when Play is pressed, while preserving selected-season/episode focus on Back.

## P1 — Playback and recording UX

- [ ] Test local recording playback for file URI, content URI, partial TS, completed TS, inaccessible file, and deletion while not playing.
- [ ] Test Player controls at slow and fast OK presses, hold OK, transport seek, Back, Up/Down channel zap, subtitles, audio, video quality, next episode, and error recovery.
- [ ] Ensure player control focus remains on the chosen action after one activation; prevent accidental reset-to-start or double Back behavior.
- [ ] Add explicit stream retry/backoff and a user-visible retry action for transient provider errors. Bound retries so a dead stream cannot spin indefinitely.
- [ ] Verify SurfaceView/fullscreen/PiP lifecycle and release of preview/multiview decoders across rotation, Back, app background, and process recreation.
- [ ] Verify simultaneous recording from EPG and Player shows one shared state, prevents duplicates, and keeps recording when navigating away.
- [ ] Add storage quota handling during recording and a clear notification when disk space, permission, or provider read failure ends a recording.
- [ ] Decide and test retention policy for partial recordings, watched recordings, failed recordings, and recordings whose source file was externally removed.

## P1 — Security and privacy

- [ ] Disable or redact OkHttp request logging in release builds. Xtream credentials are embedded in API URLs and BASIC logging can expose them in logcat.
- [ ] Audit all error/snackbar strings so URLs, usernames, passwords, tokens, and raw provider responses are never shown to users or diagnostics.
- [ ] Verify password vault migration/fallback behavior and backup export: credentials must never be exported in plaintext unless explicitly chosen by the user.
- [ ] Review cleartext traffic policy and per-playlist exceptions; allow HTTP IPTV streams only where required and show the security tradeoff.
- [ ] Test exported activities/services, notification intents, file URIs, and provider headers for unintended exposure to other apps.

## P2 — UI quality and accessibility

- [ ] Replace remaining hardcoded user-facing strings with resources, including diagnostics, player labels, preview labels, fallback descriptions, and sort labels.
- [ ] Audit all icons and controls for meaningful content descriptions, minimum TV focus target size, visible focused state, and contrast in every accent theme.
- [ ] Verify poster/channel image placeholders, failed-image fallback, aspect ratio, target-size downsampling, and memory release while scrolling 10k items.
- [ ] Verify mobile Add Playlist layout without auto-opening the keyboard, with scroll-to-focused-field and preserved invalid input after every failure path.
- [ ] Verify landscape TV, phone-sized layout, large font scale, RTL text, long provider names, Unicode/emoji titles, and malformed metadata.
- [ ] Review top toolbar alignment across Live, Movies, Series, Search, and Multi-view; controls must share one focus row and consistent Back behavior.
- [ ] Add empty/loading/error states for every browser, EPG, search, recordings, favorites, watchlist, and Multi-view path.

## P2 — Performance and resilience

- [ ] Benchmark cold start, first library render, group changes, poster focus changes, search typing, EPG refresh, and player launch on a 1.5 GB Android TV device.
- [ ] Capture Compose recompositions and frame timing while holding D-pad keys; verify stable keys, content types, immutable UI models, and no per-item expensive metadata work.
- [ ] Confirm Coil requests use target-size decoding, bounded disk/memory caches, cancellation on disposal, and no full-resolution decode for small channel logos.
- [ ] Add OkHttp connection-pool/DNS/response-size limits where safe; verify long recordings do not exhaust sockets or memory.
- [ ] Test database size and query plans at 10k/50k/100k channels and VOD items; add missing composite indexes based on measured plans.
- [ ] Verify background playlist/EPG workers obey network constraints, backoff, cancellation, and do not run duplicate syncs after reboot.
- [ ] Run macrobenchmark/baseline-profile generation on the release variant and compare against a clean install, not only an incremental build.

## P2 — Build, release, and test completeness

- [ ] Add unit tests for import error mapping, duplicate playlist detection, order preservation, empty responses, credential retention, recording state transitions, URI deletion, and route/player target serialization.
- [ ] Add integration tests with a local fake M3U, Xtream, HLS, MPEG-TS, XMLTV, and failing-provider server; avoid relying only on live providers.
- [ ] Add Room migration tests for every exported schema version, including FTS rebuild and deletion cleanup.
- [ ] Add Compose/UI tests for Back/focus overlay behavior and recording Stop/Cancel actions.
- [ ] Run release `lint`, `test`, `assembleRelease`, R8, baseline profile, and install/launch smoke tests on a clean device.
- [ ] Review dependency versions, licenses, privacy text, notification channels, app icon/banner adaptive assets, version code, and crash reporting policy before release.
- [ ] Update `TEST_TODO.md` with device/build IDs, pass/fail evidence, and known limitations; do not mark a feature complete without a reproducible test result.

## Suggested execution order

1. Data cleanup/atomic sync and recording recovery.
2. Security logging audit.
3. Navigation/focus and player regression suite.
4. FTS5/index/query performance work.
5. EPG and catalog edge cases.
6. UI/accessibility polish.
7. Release verification and final manual QA.
