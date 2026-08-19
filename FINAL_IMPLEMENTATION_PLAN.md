# Nova IPTV — Final Implementation and Release Plan

This plan reconciles the repository audit, the previous project audit, and the
second GitHub audit against commit `ca0cf87a85f4440c4ef744ab20977ae603c918ae`.

## Release verdict

Current status: **No-go for release**.

The complete application is on `arena/019ff733-arena-test`, while `origin/main`
currently contains only `README.md`. The branch must be reviewed and merged into
the intended release branch before tags, CI, or store builds can represent the app.

## Findings accepted as release blockers

### B0 — Integrate the application branch

- Review and merge `arena/019ff733-arena-test` into the intended default/release
  branch, or explicitly designate this branch as the release branch.
- Add branch protection, pull-request checks, and a reproducible release workflow.
- Do not publish from a branch that only contains the README.

### B1 — Credential and sensitive-data containment

- Disable HTTP logging in release and redact usernames/passwords from all error
  messages, diagnostics, backup logs, and provider URLs.
- Stop treating credential-bearing Xtream/EPG URLs as safe durable data. Store portal,
  username, and secret separately; migrate existing records without exposing secrets.
- Exclude Room, DataStore, encrypted vault, fallback vault, and PIN material according
  to an explicit backup policy. “Omit passwords” must also omit credential-bearing EPG
  and stream URLs.
- Restrict user-installed CA trust to debug builds and require an explicit warning for
  cleartext HTTP providers.
- Add automated tests that scan logs and exported backup JSON for secrets.

### B2 — Recording correctness and overlap policy

- Choose one supported policy: concurrent recordings with independent service/jobs,
  or reject overlapping schedules before saving them. Do not silently cancel the
  previous recording.
- Add provider headers to recording requests and reconnects.
- Replace WorkManager-as-clock assumptions with a documented tolerance/recovery model;
  detect late starts and record the actual start time.
- Add startup reconciliation for RECORDING rows, orphan files, service death, reboot,
  low storage, provider EOF, HLS encryption/byte-range incompatibility, and partial TS.
- Validate source/program eligibility and show the user when a requested recording
  cannot be scheduled or is outside the available catch-up window.

### B3 — Centralized parental authorization

- Enforce locked-channel/group authorization at a shared playback/navigation boundary,
  not only the normal Home row callback.
- Cover Home actions, Guide, Search, numeric tuning, channel zap, Last Channel startup,
  Multi-view assignment/fullscreen, catch-up, and detail playback.
- Require PIN creation before enabling parental controls; never treat an empty hash as
  a valid PIN. Clear failed attempts and ensure remote input focuses the PIN field.
- Decide which sensitive settings require the PIN (at minimum playlist deletion,
  backup/restore, reset/cache clearing, parental changes, and provider credentials).

### B4 — Provider header consistency

- Define one normalized header model for User-Agent, Referer, Origin, cookies, and any
  provider-specific values.
- Apply it to main playback, retry, preview, Multi-view, recordings, EPG, and Xtream
  stream fetches.
- Preserve headers in the current `PlayTarget`/retry path and add fake-server 401/403
  tests for every playback mode.

### B5 — Playlist ownership and lifecycle

- Add a user-facing active-playlist selector.
- When the selected playlist is deleted or becomes invalid, atomically select a valid
  replacement or return to the welcome screen and clear `lastPlaylistId`.
- Delete or migrate channels, VOD, episodes, EPG sources, XMLTV mappings, programs,
  diagnostics, favorites/watch history, and playlist-bound recordings according to a
  documented retention policy.
- Make refresh persistence transactional and distinguish valid empty provider data
  from failed/partial imports.

### B6 — Correct search and resume behavior

- Program search results must carry their channel target and open programme details,
  EPG, or catch-up—not pass a program ID as a live channel ID.
- Persist playback position/duration during playback with throttled writes; restore
  movie, series episode, and catch-up position safely.
- Make Continue Watching reflect actual progress and remove/complete entries according
  to the selected retention policy.

## High-priority stability and UX work

- Keep Guide rows mounted while refreshing; show an inline refresh indicator instead of
  replacing the entire focusable list every 30 seconds.
- Replace the 160-channel legacy guide limit with grouped/paged guide browsing or make
  the limit explicit in the UI.
- Serialize SettingsRepository transformations inside one atomic update mechanism.
- Persist SAF permissions for local playlist files and provide a repair action when the
  source URI is unavailable.
- Make backup restore partial-failure tolerant: report failed playlists and continue
  restoring independent data.
- Ensure global G/S/Menu shortcuts do not intercept text entry or stack duplicate
  Guide/Search/Settings destinations.
- Make Settings content scrollable and verify all sections on small displays.
- Respect the autoplay-preview setting and decide whether 9-up Multi-view is supported;
  otherwise remove stale 9-up documentation and strings.
- Replace numeric-zap full-catalog scans with the indexed DAO lookup.

## Performance and data-scale work

- Benchmark the current FTS4 implementation before deciding on an FTS5 migration;
  migrate only with a measured benefit and a Room migration/rebuild plan.
- Replace wildcard `LIKE` search with indexed search where benchmarks justify it.
- Add composite indexes based on measured query plans for group, playlist, year, and
  provider ordering.
- Remove duplicate/unused Home state such as the permanently empty content flow.
- Bound EPG writes with a producer/writer pipeline and cancellation rather than
  `runBlocking` in parser callbacks.
- Run low-RAM macrobenchmarks for cold start, group changes, poster focus, search
  typing, EPG refresh, Multi-view, and player launch.

## Release engineering

- Merge the application branch into the designated default/release branch.
- Add GitHub Actions for compile, unit tests, Android tests, lint, Room migrations,
  release assembly, and artifact publication.
- Add a release signing configuration that fails clearly when credentials are absent;
  never publish a debug-signed artifact.
- Generate and verify the baseline profile for the release variant.
- Add dependency/version/license review and remove stale release documentation claims.
- Use app-owned notification icons and a correctly sized Android TV banner.
- Increment version code/version name for each distributable build.

## Execution order and exit gates

### Phase 0 — Branch and safety gate

Merge/integrate the app branch, disable credential logging, fix backup exclusions,
and persist SAF permissions.

**Gate:** clean clone of the release branch contains a buildable Gradle project;
secret-scan tests pass.

### Phase 1 — Authorization and recording gate

Centralize parental checks, define overlap behavior, propagate provider headers, and
add recording recovery/reliability tests.

**Gate:** locked content cannot bypass PIN; two overlapping schedules behave according
to policy; authenticated streams and recordings work in every playback mode.

### Phase 2 — Data ownership gate

Implement active-playlist selection, transactional sync/delete cleanup, EPG ownership,
and backup partial-failure handling.

**Gate:** deleting/updating playlists cannot leave stale visible content or strand Home;
interrupted sync preserves the last complete catalog.

### Phase 3 — Search/resume/guide gate

Fix program-result routing, persist playback progress, stabilize guide refresh/focus,
and remove hard limits or clearly expose them.

**Gate:** Search opens the correct target, Continue Watching resumes correctly, and
Guide refresh does not remove focus or freeze browsing.

### Phase 4 — Performance and release gate

Benchmark/catalog optimization, release CI, migrations, signing, baseline profile,
assets, and final documentation.

**Gate:** clean install and upgrade pass on the target Android TV; offline launch,
network failure, process death, reboot, low storage, and release artifact checks pass.

## Findings explicitly not treated as new work

- MultiView `AndroidView` already has an `update` player binding.
- Fullscreen-to-MultiView has an explicit budget release guard.
- Catch-up builder already supports in-progress playback through `allowInProgress`.
- Room schema export and the 1→2 migration already exist.
- Recording Stop/Cancel UI and local recorded-file playback were implemented in the
  latest batch; they remain regression-test requirements, not reimplementation tasks.
