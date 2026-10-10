# Research In-App MP3 Caching and ExoPlayer/MediaPlayer Lifecycle

Type: research
Status: resolved
Blocked by:

## Question
What is the optimal implementation pattern to safely copy selected audio files from Android Storage Access Framework (`ACTION_OPEN_DOCUMENT`) into `context.filesDir`, validate MP3 format/headers, prune unreferenced files when alarms are deleted, and decide between lightweight `android.media.MediaPlayer` vs Jetpack `androidx.media3.exoplayer` for reliable background looping playback?

## Answer
The detailed research findings with primary source citations have been documented in [`docs/research/audio-playback-caching.md`](../../docs/research/audio-playback-caching.md).

Key answers summary:
1. **SAF File Selection & Sandboxing**: Use `ActivityResultContracts.OpenDocument` (`ACTION_OPEN_DOCUMENT`) with MIME filter `audio/mpeg` and `audio/*`. Never stream directly from external content URIs during alarm ringing (vulnerable to external file moves, SD card unmounting, cloud auth drops, and OEM permission revocation on reboot). Copy the stream via `ContentResolver.openInputStream(uri)` into `context.filesDir/alarms/` using an atomic temp-file-and-rename pattern, enforcing a 50 MB size ceiling and SHA-256 content-addressing (`track_<sha256>.mp3`) for automatic deduplication across alarm slots.
2. **Defensive MP3 Validation & Metadata Extraction**: Pass files through a defense-in-depth pipeline:
   - Header magic byte pre-validation: check for ID3v2 container (`0x49 0x44 0x33`) or raw MPEG Layer III frame sync word (`0xFF` with `0xE0` mask and Layer III bits `0x02`) before invoking native decoders.
   - Guarded `MediaMetadataRetriever`: wrap in unchecked `RuntimeException` catches to prevent native crashes, verify `METADATA_KEY_MIMETYPE == "audio/mpeg"` and `METADATA_KEY_HAS_AUDIO == "yes"`, validate duration is positive and within 1s to 60min, and ensure clean `release()` in `finally`.
3. **Storage Lifecycle Management & Pruning**: Enforce strict **database-first transactional deletion** (never delete a file from disk before the Room DB transaction commits).
   - On alarm slot update/deletion: check if the old track path has remaining references (`SELECT COUNT(*)`); delete from disk if count is 0.
   - On cold start: run an orphan sweep comparing disk files in `context.filesDir/alarms/` against all active paths in Room, purging unreferenced `.mp3` files and stale `.tmp` files older than 1 hour.
4. **Playback Engine Recommendation**: **Jetpack AndroidX Media3 ExoPlayer (`androidx.media3:media3-exoplayer`)** is strongly recommended over platform `MediaPlayer`. While `MediaPlayer` adds 0 KB to APK size, its brittle 10-state native state machine, unhandled `IllegalStateException` crashes, gap-riddled looping seams on MP3 padding, and OEM-specific failure modes make it a hazard for an alarm clock. Media3 ExoPlayer provides seamless gapless looping (`REPEAT_MODE_ONE`), robust unified state management, out-of-the-box `USAGE_ALARM` with automatic audio focus (`handleAudioFocus = true`), local wake-lock abstraction (`C.WAKE_MODE_LOCAL`), smooth programmatic volume ramping for escalating alarms, and identical behavior across all Android OEMs.
