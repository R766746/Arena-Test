# NOVA IPTV — Test TODO

Use this checklist one item at a time. Do not combine fixes from different test items.

For each test:

1. Perform only the current unchecked test.
2. Record the device, result, and short notes.
3. If it fails, make the smallest isolated correction.
4. Rebuild and repeat the same test until it passes.
5. Mark it complete before proceeding to the next item.

## Test environment

- Device/model:
- Android version:
- App build/commit:
- Connection type:
- Playlist type/provider:

## Checklist

- [ ] 01 — Exit popup focus
  - Press Back from the Home screen.
  - Confirm focus moves to **Stay**.
  - Confirm Left/Right moves only between **Stay** and **Exit**.
  - Confirm Up/Down does not move the background selection.
  - Confirm Back closes the popup and restores the previous screen focus.
  - Result/notes:

- [ ] 02 — Xtream import duration
  - Add a valid Xtream Codes account.
  - Record the time from Import until Home opens.
  - Confirm progress does not remain indefinitely on Saving to library.
  - Confirm only one playlist entry is created.
  - Import time:
  - Result/notes:

- [ ] 03 — Live TV immediately after import
  - After Xtream import completes, open Live TV.
  - Confirm channel categories and channels appear without a manual refresh.
  - Record the time until the first channel is visible.
  - Load time:
  - Result/notes:

- [ ] 04 — Main category switching
  - Switch Live TV → Movies → Series → Live TV at least five times.
  - Confirm cached lists appear immediately after the first visit.
  - Confirm focus remains visible and responsive.
  - Result/notes:

- [ ] 05 — Posters and category persistence
  - Open Movies and wait for visible posters to load.
  - Switch to Series, then return to Movies.
  - Confirm previously loaded posters reappear immediately.
  - Confirm categories do not temporarily disappear or reorder.
  - Repeat from Series to Live TV and back.
  - Result/notes:

- [ ] 06 — Live channel playback
  - Play channels from at least three categories.
  - Confirm playback starts, channel information is correct, and Back returns correctly.
  - Test one unavailable stream and confirm a useful error appears.
  - Result/notes:

- [ ] 07 — Movie playback
  - Open and play at least three movies.
  - Confirm title, poster, stream, playback controls, and Back behavior.
  - Result/notes:

- [ ] 08 — Series and episode playback
  - Open at least two series.
  - Confirm details, seasons, episodes, posters, and episode playback.
  - Confirm returning from playback restores the previous selection.
  - Result/notes:

- [ ] 09 — M3U URL import
  - Add a valid M3U URL.
  - Confirm import completes and channels appear.
  - Confirm the new playlist appears only once.
  - Result/notes:

- [ ] 10 — Invalid credentials retention
  - Enter an invalid Xtream username or password and submit.
  - Confirm a clear error appears.
  - Confirm name, server URL, username, and password remain available to correct.
  - Correct the credentials and confirm import succeeds.
  - Result/notes:

- [ ] 11 — Existing playlist update
  - Choose Update now for an existing playlist.
  - Confirm existing content remains visible while updating.
  - Confirm updated content replaces stale content.
  - Confirm no duplicate playlist is created.
  - Result/notes:

- [ ] 12 — Delete the last playlist
  - Delete all playlists.
  - Confirm the Welcome/Add Playlist screen appears.
  - Restart the app and confirm no demo or empty Home library appears.
  - Result/notes:

- [ ] 13 — Multiple playlists
  - Add at least two playlists.
  - Select each playlist and confirm its channels, movies, and series are isolated correctly.
  - Restart the app and confirm the last selected playlist remains active.
  - Result/notes:

- [ ] 14 — EPG background loading
  - Add or update a playlist with EPG enabled.
  - Confirm channels appear before EPG processing finishes.
  - Confirm programme data appears later without freezing navigation.
  - Result/notes:

- [ ] 15 — Search
  - Search for a known channel, movie, and series.
  - Open each result and confirm it targets the correct item.
  - Confirm clearing search is immediate.
  - Result/notes:

- [ ] 16 — Favorites and history
  - Add and remove channel favorites.
  - Play live, movie, and episode content.
  - Confirm Favorites and Recently Watched update and survive restart.
  - Result/notes:

- [ ] 17 — All popup focus behavior
  - Test channel actions, PIN, EPG assignment, delete confirmation, and exit popups.
  - Confirm each popup captures focus and blocks background D-pad movement.
  - Confirm dismissal restores sensible focus.
  - Result/notes:

- [ ] 18 — Mobile layout and keyboard
  - Test the Welcome, M3U, and Xtream forms on a phone-sized screen.
  - Confirm every field and button can be reached by scrolling.
  - Confirm the keyboard does not permanently cover the active field or Import button.
  - Result/notes:

- [ ] 19 — Android TV D-pad navigation
  - Navigate every main category using only the D-pad, OK, and Back.
  - Confirm there are no focus traps, invisible focus states, or unexpected background movement.
  - Result/notes:

- [ ] 20 — Launch and offline behavior
  - Test a cold launch with internet access.
  - Close and relaunch the app.
  - Disable internet and relaunch again.
  - Confirm saved library content appears immediately while offline.
  - Confirm unavailable network operations fail without blocking navigation.
  - Result/notes:

## Completion summary

- Passed:
- Failed:
- Deferred:
- Remaining known issues:
- Final device/build tested:
