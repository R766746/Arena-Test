# IPTV Professional Player Analysis & Progress Report

This report evaluates the **NOVA** codebase against industry-standard, commercial-grade IPTV applications. It outlines the architecture's strengths, tracks completed enhancements, and defines the remaining feature pillars required for a complete retail-ready solution.

---

## Executive Summary & Architecture Evaluation

NOVA possesses an exceptionally robust foundational architecture designed for resource-constrained Android TV / Fire TV environments (1.5GB–2.0GB RAM sticks).

### Architectural Highlights:
*   **Decoupled & Modern Core**: Built with Jetpack Compose, Media3 (ExoPlayer), Room, and Hilt.
*   **Strict Player Budget**: Production-level coordination of player allocation to prevent OOMs on low-end hardware.
*   **Streaming Ingestion**: Batch-processed `XmlPullParser` for XMLTV to handle giant EPG files without memory bloat.

---

## Feature Status & GAP Analysis

| Category | Feature | Status | Technical Impact |
| :--- | :--- | :--- | :--- |
| **Playback** | **HTTP Header Injection** | **DONE** | Prevents `403 Forbidden` errors by sending correct `User-Agent`/`Referer`. |
| **Playback** | **Audio/Subtitle Picker** | **DONE** | Full UI sub-menu to switch languages and toggle captions. |
| **Playlist** | **In-place Syncing** | **DONE** | Playlist updates now preserve **Favorites** and **Watchlist** data. |
| **Stability** | **Android 14+ FGS Fix** | **DONE** | Resolved startup crashes by declaring Foreground Service types. |
| **Playback** | Real Display AFR | PENDING | Adapting TV refresh rates to content (e.g. 24Hz for movies) to stop judder. |
| **Playlist** | Stalker Portal Support | PENDING | Compatibility with MAG-style middleware portals. |
| **EPG** | Manual EPG Mapping | PENDING | UI to manually link program sources to streams when auto-match fails. |
| **EPG** | TMDb Enrichment | PENDING | Automatic high-quality posters and metadata for VOD items. |
| **Recording** | HLS Segment PVR | PENDING | Reliable recording for adaptive `.m3u8` streams. |
| **UI/UX** | Quick OSD Overlay | PENDING | View channel list/EPG while watching video without returning home. |
| **UI/UX** | Remote Key Mapping | PENDING | Custom shortcuts for remote buttons (Info, Menu, Color keys). |
| **Retail** | In-App Billing (IAP) | PENDING | Gating premium features behind a subscription/one-time purchase. |

---

## 1. Recently Completed Enhancements

### HTTP Header Injection
`PlayerManager` now uses a dedicated `OkHttpDataSource.Factory` for the main player. Per-stream headers (passed from `ChannelEntity`) are injected before preparation, allowing access to secure provider streams.

### Audio & Subtitle Track Selectors
Implemented a recursive sub-menu system in the player side sheet. It binds to Media3's `Tracks` state, allowing users to browse and select available audio and text tracks with user-friendly names.

### In-place Playlist Syncing (Data Preservation)
Refactored `PlaylistRepository` and `Daos` to use an "Upsert + Delete Orphans" strategy. When a playlist is updated, existing records are updated in-place rather than deleted, ensuring that a user's **Favorites**, **User Order**, and **Watchlist** status are never lost during a refresh.

---

## 2. Next Priority: Feature Deep-Dive

### Quick Overlay Channel Guide (OSD)
**Goal**: Allow users to browse categories and channels while video is playing.
**Technical**: Create an overlay `Box` in `PlayerScreen.kt` that triggers on D-pad `Up`/`Down`. Reuse `ChannelRow` components in a smaller, semi-transparent side panel.

### TMDb Enrichment for VOD
**Goal**: Fix the "empty posters" problem in VOD catalogues.
**Technical**: When a TMDb API key is present, `VodScreens` or a background worker should search TMDb by title/year and update the `posterUrl` and `backdropUrl` in the database.

### Stalker Portal Integration
**Goal**: Support the massive market of Stalker/MAG subscribers.
**Technical**: Implement a new `StalkerApi` service that handles MAC-based authentication and parses the Stalker `/portal.php` protocol.

---

## Architectural Checklist for Retail Deployment

*   [ ] **In-App Billing (IAP)**: Integrate `com.android.billingclient:billing` to unlock premium tiers.
*   [ ] **Multi-Device Sync**: Expansion to include WebDAV or Google Drive for cross-TV favorites sync.
*   [ ] **Macrobenchmarks**: Generate baseline profiles on real hardware to ensure 60fps scrolling on low-end sticks.
