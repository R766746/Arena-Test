# NOVA performance rules

Target hardware: Fire TV 4K / 1.5–2 GB Android TV sticks. Every feature is budgeted against a single hardware decoder and a ~128 MB heap class.

## 1. Strict player budget

`PlayerBudget` (`app/src/main/java/com/nova/iptv/core/perf/PlayerBudget.kt`) allows **exactly one** of:

- 1 fullscreen ExoPlayer
- 1 fullscreen + 1 muted preview
- N muted multi-view players (cap 4 on low-RAM, 9 otherwise)

Never preview **and** multi-view. `PlayerManager` asks the budget before constructing a decoder.

## 2. Coil

Configured in `CoilConfig`:

- Memory cache = 15% of heap
- Disk cache = 80 MB
- `RGB_565` when total RAM &lt; 3 GB or `ActivityManager.isLowRamDevice`
- `onTrimMemory(RUNNING_CRITICAL)` and `onLowMemory` clear the memory cache
- Loads are not prefetched beyond the next 20 logos in the focused group (Home VM)

## 3. EPG ingest

`EpgRefreshWorker` + `EpgRepositoryImpl`:

- Runs in WorkManager with `setForeground`
- Streaming `XmlPullParser` — never DOM, never a giant String
- Yields every 500 programmes
- Room inserts in batches of 500
- `VACUUM` after prune
- **Update on start is OFF by default** (`AppSettings.updateEpgOnStart = false`)

## 4. Channel list

- `TvLazyColumn` / `LazyColumn` with `key(id)`
- Now/next titles are precomputed in `HomeViewModel` every 30 s, not per frame
- Row composables are thin: number, logo tile, two `Text`s, a 3 dp bar

## 5. Recycle bitmaps / no full XML

XMLTV is pull-parsed. Coil recycles bitmaps. Logo decode failures fall back to initials tiles.

## 6. `onTrimMemory(TRIM_MEMORY_RUNNING_CRITICAL)`

`NovaApplication` drops the preview player, clears Coil memory, and cancels artwork work.

## 7. Prefetch window

Only the focused group’s next ~20 logos are allowed to hit the network. Grids use Coil’s default size (`300×450` for posters).

## 8. No heavy blur on low RAM

`LowRam.isLowRam` disables glass blur (`NovaPalette.useBlur = false`). Panels are solid 78% surfaces.

## 9. Startup

Splash → cached Home. Demo / last playlist is read from Room. EPG is **not** refreshed on start unless the user enables it.

## 10. LeakCanary + StrictMode

Debug only (`debugImplementation leakcanary`, `StrictMode` in `NovaApplication`).

## 11. Baseline Profile

`app/src/main/baseline-prof.txt` covers Home + Player + ExoPlayer / Compose first frame.

## 12. R8 full mode

`android.enableR8.fullMode=true`. Release ProGuard strips Timber `v/d/i/w`.

## 13. OOM around decode

Coil error painters and `LogoTile` catch failed decodes and draw initials. Preview / 9-up init is wrapped in `runCatching`.

## 14. Multi-view 9-up fallback

If a decoder fails to init, `PlayerManager.dropMultiViewTo(4)` and the UI switches to 4-up automatically.

---

### Diagnostics

Settings → About → Diagnostics (`DiagnosticsScreen`) reports heap, decoder count, last EPG duration, playlist size, low-RAM flag.
