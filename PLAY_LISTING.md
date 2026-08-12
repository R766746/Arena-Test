# Play Console — TV listing draft

**Application name:** NOVA  
**Package:** `com.nova.iptv`  
**Category:** Video Players & Editors  
**Form factor:** Android TV (Leanback required)

## Short description (80)

NOVA is a player only. It does not provide IPTV channels or VOD — add a playlist you already have.

## Full description

NOVA is a 10-foot IPTV *player* for Android TV.

It does **not** include, sell, host, or recommend any live TV, movie, or series catalogue. You must add a playlist you are already authorised to use (M3U/M3U8 URL, local file, or Xtream Codes portal).

What NOVA does:

• Live TV in a three-pane Home (categories, groups, channels) built for a D-pad  
• XMLTV and Xtream EPG with a full-screen guide  
• Movies & series posters, continue watching, detail pages  
• Catch-up / timeshift when your provider supports it  
• Optional local recordings (quality equals the live stream — no transcode)  
• 2 / 4 / 9 multi-view, search, parental PIN, backup/restore  
• Tuned for low-RAM sticks (Fire TV class): one extra preview decoder, never nine plus preview  

What NOVA does **not** do:

• Provide IPTV or VOD content  
• Circumvent DRM or geo-blocks  
• Include adult or pirated streams  

You are responsible for the legality of every URL you add.

A built-in showcase catalogue uses public-domain test streams so you can evaluate the interface offline. It is not a content service.

## Feature graphic / TV banner

320×180 leanback banner: midnight `#07080D`, accent `#3D8BFD`, wordmark NOVA.  
Store poster 1080×1920: same system, “A player. Not a provider.”

## Data safety (draft)

- No account with the developer  
- Playlists and PINs stay on device (EncryptedSharedPreferences / DataStore)  
- Network only to hosts *you* configure  
- Optional SAF folder for recordings and backups  

## Support / policy line

NOVA is a player only and does not provide IPTV content.
