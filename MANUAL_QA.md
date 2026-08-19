# NOVA manual QA

Run on an Android TV emulator (API 26–35) **and** a Fire TV 4K stick if available. D-pad only unless noted.

NOVA is a player only. Use the bundled showcase or a playlist you are licensed to use.

## 1. Playlist import

| Step | Expected |
| --- | --- |
| Add playlist → M3U URL of a small list | Progress: connecting → parsing → saving. Channels appear under Live TV. |
| Add playlist → Xtream portal / user / pass | Live + VOD categories populate. Password is **not** in logcat. |
| Wrong password | Error maps to “Invalid credentials (401)” / expired copy. |
| Unknown host | “Could not resolve host…” |
| Empty file | “The playlist contained no channels.” |
| Add playlist → Local file (SAF) | Same parse path as URL. |
| Refresh an existing list after starring 3 channels | Favorites and user order survive. Stream URLs update. |
| Delete demo | Blocked. Snack / no-op. |

Automated: `M3uParserTest` (mixed quotes, no attributes, VOD).

## 2. 10k-channel playlist

Import a large M3U (or concatenate the fixture). 

- First frame of Home &lt; 2 s on a warm start (cached Room).
- Scroll All Channels with D-pad: **no ANR**, list stays ≥ 30 fps (systrace / `adb shell dumpsys gfxinfo com.nova.iptv`).
- Memory in Diagnostics does not climb unbounded after 2 minutes of scrolling.

## 3. EPG

| Step | Expected |
| --- | --- |
| Showcase guide | Grid from −6 h to +16 h, now-line, current progress. |
| XMLTV URL (xml or xml.gz) | Streaming ingest, sources listed in Settings → EPG. |
| Exact `tvg-id` | Matched automatically. |
| `CNN HD` vs XMLTV `CNN` | Normalized-name fallback. |
| Long-press channel → Assign EPG | Search display-names, persist `epgId`. |
| Time shift +1 h | Programme start/end move; enable debug overlay if it looks wrong. |
| Update on start | Default **OFF**. Turning it ON schedules `EpgRefreshWorker` at launch. |

Automated: `XmltvParserTest`, `EpgMatcherTest`, `NameNormalizerTest`.

## 4. Zap + memory

From Player, press CHANNEL_UP/DOWN (or D-pad up/down) 20 times.

- Surface is reused; zap is immediate prepare of the next item.
- Heap in Diagnostics before/after should stay within ~20 MB.
- `lastChannelId` is restored on next `LAST_CHANNEL` startup.

## 5. Preview player

Home + preview enabled, focus a live row.

- Preview pane plays muted.
- Leave Home (Guide / Settings / Back): preview ExoPlayer is released (`PlayerBudget` mode is no longer `FULLSCREEN_PLUS_PREVIEW`).
- Low-RAM path: preview pane is hidden.

## 6. Catch-up URL

Use the Xtream fixture in `CatchupUrlBuilderTest`:

- Path form: `{portal}/timeshift/{user}/{pass}/{duration}/{start}/{stream}.m3u8`
- PHP form when `catchup-source` mentions `timeshift.php`
- M3U: `?utc=&lutc=` or token replacement
- Player shows **CATCH-UP** pill, not LIVE. Timeline is seekable.

## 7. Recordings

| Step | Expected |
| --- | --- |
| Guide → programme → Record | Row appears as Scheduled. WorkManager OneTime at start − padding. |
| Fake clock / short programme | Status → Recording → Completed. File under SAF tree or app movies dir. |
| Free space &lt; 1.5× estimate | Schedule refused. |
| Play completed | Progressive playback in Player. |
| UI note | “Recording quality equals the live stream…” |

Reminders: if Record is not set, ReminderWorker can fire 2 minutes before.

## 8. Multi-view

- Default 4-up. D-pad moves tile focus; only focused tile has audio.
- OK → fullscreen of that channel.
- Long-press tile → pick from favorites.
- 9-up on low-RAM: warning, stays at 4.
- Decoder init failure: drops to 4.
- Leave screen: all extra players released.

## 9. Parental PIN

Enable PIN in Settings → Parental. Lock a group.

- Opening a locked group / channel shows PIN dialog.
- Wrong PIN stays gated.
- Correct PIN plays.
- Lock settings: section list requires PIN.

PIN is a salted SHA-256 in DataStore, never plaintext.

## 10. Backup / restore

Settings → General → Backup to file (omit passwords). Restore on a **second emulator**.

- Playlists and favorites return.
- Xtream passwords are absent when omitted; user re-enters them.

## 11. D-pad settings

From Home → Settings, D-pad down through every left-rail row (Playlists … About). Each row focuses with a 2 dp accent ring. Right pane updates. Left from the pane returns to the section list. Automated: `SettingsDpadTest`.

## 12. Back stack

Player → Guide (MENU / Guide key) → Back → Player or previous.  
Player → Back → Home.  
Home → Back → exit confirm. Back again / Exit finishes Activity. Player never finishes the Activity itself.

## 13. Picture-in-picture

On a device that supports PiP, leave Player with Home. `enterPictureInPictureMode` 16:9. Audio continues via `PlaybackService`. Leanback-only devices hide the now-playing notification.

## 14. Themes

Settings → Appearance: Midnight / Dark / Cinema / Light. Accent swatches apply live via `CompositionLocal`. Font scale and animation off (`ANIM_OFF` = 0 ms) honor immediately.

## 15. Low-RAM path

Force `LowRam` (or use a 1.5 GB image):

- Preview off
- No blur (solid panels)
- Multi-view max 4
- RGB_565 Coil
- Small ExoPlayer buffers

## 16. First run

Cold install: splash 1.6 s → Add Playlist wizard. “Browse showcase” seeds demo. Empty groups show friendly copy, never a blank pane.

## 17. Global keys

| Key | Action |
| --- | --- |
| GUIDE / G | TV Guide |
| SEARCH / S | Search |
| MENU | Settings, or player options when playing |
| CHANNEL ± | Zap while on Player |

## 18. Search + voice

Type with 200 ms debounce. Sections: channels, programmes ±8 h, movies, series. KEYCODE_SEARCH opens the speech recognizer when present.

---

### Instrumentation

```
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedAndroidTest
```

Unit tests do not need a device. Compose tests need an emulator.
