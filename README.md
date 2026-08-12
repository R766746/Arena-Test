# NOVA

A 10-foot IPTV **player** for Android TV. It does not provide channels, movies, or series.

```
applicationId  com.nova.iptv
minSdk         26
targetSdk      35
compileSdk     35
```

Single-activity Compose + `androidx.tv` + Media3 ExoPlayer + Hilt + Room + DataStore.

## What you get

- TiviMate-style three-pane Home (categories · groups · channels / posters)
- Full-screen EPG grid, catch-up, timeshift, local PVR
- M3U URL / local file / Xtream Codes import (streaming parse)
- Movies, series, detail, continue watching
- 2 / 4 / 9 multi-view with a hard decoder budget
- Parental PIN, backup/restore, four themes, midnight `#07080D` + accent `#3D8BFD`
- Showcase catalogue so the UI is usable on an emulator with no credentials

## Open in Android Studio

1. Android Studio Ladybug+ / AGP 8.7, JDK 17, SDK 35.
2. Open this folder. Let it generate `gradle/wrapper/gradle-wrapper.jar` if it is missing (`File → Sync` or `gradle wrapper --gradle-version 8.11.1`).
3. Create an **Android TV** emulator (API 28–35, 1080p, leanback).
4. Run `app`.

```
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

## First run

Splash (1.6 s) → Add Playlist. **Browse showcase** installs ~40 demo channels, synthetic EPG (−6 h…+16 h), and a small VOD shelf. Or paste an M3U / Xtream portal you already own.

## Architecture

```
ui/          home guide player vod settings search recordings multiview playlist splash
domain/      model usecase
data/        playlist epg local remote player recordings backup
di/  nav/  core/util  core/perf
```

`PlayerBudget` enforces 1 fullscreen + 1 preview **or** N multi-view decoders, never both. See [PERFORMANCE.md](PERFORMANCE.md).

## Tests & release

- Unit: M3U parser, XMLTV, EPG matcher, catch-up URLs — `app/src/test`
- Compose: Home panes, settings rail — `app/src/androidTest`
- Manual script: [MANUAL_QA.md](MANUAL_QA.md)
- Play listing draft: [PLAY_LISTING.md](PLAY_LISTING.md)
- Leftover work: [TODOS.md](TODOS.md)

## Legal

NOVA is a player only and does not provide IPTV content. You are responsible for every stream URL you add.
