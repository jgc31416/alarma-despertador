# Research: Audio Selection, In-App Caching, Validation, and Playback Engine

**Project:** Despertador (GPLv3 Native Android Alarm Clock)  
**Target Platform:** Android 8.0 (API 26, `minSdk`) to Android 15 (API 35, `targetSdk`)  
**Document Status:** Complete  
**Date:** October 2026  

---

## 1. Executive Summary & Architectural Decisions

Despertador enables users to wake up to custom local MP3 audio media tracks. An alarm clock has a zero-tolerance threshold for failure: if an audio track fails to decode, is inaccessible due to permission revocations or external file deletion, or throws an unhandled media framework exception, the user oversleeps.

Based on primary documentation, Android Open Source Project (AOSP) implementation details, and Android Jetpack architecture guidelines, this document establishes the following architectural standards:

1. **Storage Access Framework (SAF) & Private Internal Sandboxing**:
   - The user selects audio files using `ActivityResultContracts.OpenDocument` (`ACTION_OPEN_DOCUMENT`) with MIME filter `audio/*` / `audio/mpeg`.
   - The app **never** streams directly from the external content URI during an alarm trigger. Persistable URI permissions are vulnerable to file relocation, SD card ejection, cloud unmounting, and OEM reboot permission revocation bugs.
   - The selected stream is read via `ContentResolver.openInputStream(uri)` and copied into application-private internal storage (`context.filesDir/alarms/`) using an atomic temporary-file-and-rename pattern with SHA-256 content addressing for automatic deduplication.
2. **Defensive MP3 Validation & Metadata Extraction**:
   - File validation follows a fail-fast, defense-in-depth pipeline:
     1. File size bounds check (minimum 1 KB, maximum 50 MB to prevent internal storage exhaustion).
     2. Magic byte pre-validation (validating either an ID3v2 container header or an MPEG Audio Layer III frame sync word) before passing bytes to native media extractors.
     3. `MediaMetadataRetriever` extraction strictly wrapped in unchecked `RuntimeException` guards, verifying `METADATA_KEY_MIMETYPE == "audio/mpeg"` and `METADATA_KEY_HAS_AUDIO == "yes"`.
     4. Duration sanity check (`METADATA_KEY_DURATION` parsed to a positive integer between 1 second and 60 minutes).
3. **Storage Lifecycle Management & Orphan Pruning**:
   - Despertador manages up to 5 alarm slots (`AlarmSlot`). Multiple alarm slots can reference the same cached audio file (deduplicated by SHA-256 hash).
   - Storage lifecycle follows a **database-first transactional pruning** rule: disk deletion is never executed before Room database mutations are committed.
   - Orphan cleanup is performed in two places:
     - On alarm slot update/deletion: unreferenced files are pruned asynchronously on `Dispatchers.IO` if reference count drops to 0.
     - On application cold start: an orphan sweep cross-references the contents of `context.filesDir/alarms/` against all active `audio_file_path` values in the Room database, deleting unreferenced `.mp3` files and stale `.tmp` files older than 1 hour.
4. **Playback Engine Recommendation**:
   - **Recommendation: Jetpack AndroidX Media3 ExoPlayer (`androidx.media3:media3-exoplayer`)**.
   - While `android.media.MediaPlayer` has zero APK size overhead, its brittle 10-state native C++ state machine, silent failure modes on OEM device ROMs, unhandled native `IllegalStateException` crashes, and flawed MP3 loop seam handling make it a liability for mission-critical alarm ringing.
   - Media3 ExoPlayer provides rock-solid state management, predictable gapless looping via `REPEAT_MODE_ONE`, out-of-the-box `USAGE_ALARM` audio attributes with automatic audio focus handling, local wake-lock abstraction (`C.WAKE_MODE_LOCAL`), smooth programmatic volume ramping for escalating alarms, and unified behavior across all Android OEM distributions.

---

## 2. Storage Access Framework (SAF) File Selection & Sandboxing

### 2.1 The Danger of External Content URIs for Alarms

When an Android app launches an audio picker via the Storage Access Framework:
```kotlin
val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? -> ... }
launcher.launch(arrayOf("audio/mpeg", "audio/*"))
```
The returned `Uri` is typically a `content://` URI backed by an external `DocumentsProvider` (e.g., `com.android.providers.downloads.documents`, `com.google.android.apps.docs.storage`, or OEM SD card providers).

Relying on this URI directly by persisting permissions via `contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)` introduces severe failure vectors:
1. **File Relocation / Deletion**: The user moves the MP3 file to another directory, renames it, or deletes it from their external storage or Downloads folder.
2. **Removable Media Ejection**: The MP3 is on a micro-SD card or USB OTG drive that is unmounted or removed.
3. **Cloud Document Providers**: Providers like Google Drive or Microsoft OneDrive return content URIs that can require network connectivity or re-authentication to open. If the device is in airplane mode overnight, the alarm fails to play sound.
4. **OEM Permission Inconsistencies**: On several OEM Android builds (Xiaomi MIUI/HyperOS, Huawei EMUI, Samsung OneUI), persistable URI permissions have been documented to drop or fail to reload across device restarts.
5. **Scoped Storage Quirks**: On Android 10+ (API 29+) and Android 11+ (API 30+), external storage permissions and document access policies continue to shift.

### 2.2 Sandboxing into `context.filesDir/alarms/`

By copying the audio file into the application's private internal storage (`context.filesDir/alarms/`):
- **100% Reliability**: Internal storage is physically hosted on the device's internal flash memory, always mounted, and immune to external file modifications or network drops.
- **Zero Runtime Permissions**: Accessing `context.filesDir` requires no Android runtime permissions (`READ_EXTERNAL_STORAGE` or Android 13+ `READ_MEDIA_AUDIO`).
- **Persistence Across Cache Clears**: We intentionally use `context.filesDir` instead of `context.cacheDir`. According to Android documentation, files in `cacheDir` can be deleted by the Android system at any time without warning when the device runs low on disk storage. An alarm sound must never be purged behind the user's back.
- **Privacy & Security**: Internal files are sandboxed with Linux file system permissions (`0700`), accessible only by Despertador's Linux UID.

### 2.3 Atomic Stream Copy and Deduplication Architecture

A naive copy directly to `alarm_1.mp3` is susceptible to corrupt files if the app process is killed or the device runs out of storage midway through the copy. 

To ensure data integrity, the copy pipeline employs:
1. **Bounded Stream Reading**: Enforce a maximum file size limit (e.g., 50 MB). If a user attempts to select a 4 GB audio file or an ISO renamed to `.mp3`, the copy is aborted immediately.
2. **SHA-256 Content Hashing**: As the stream is read, an in-line `MessageDigest` calculates the SHA-256 hash. The final file is named `track_<sha256>.mp3`.
   - **Automatic Deduplication**: If a user selects the same MP3 for Alarm Slot 1 and Alarm Slot 3, the hash matches and the app avoids duplicating bytes on internal storage.
3. **Atomic File Move**: The stream is initially written to a temporary file (`temp_<uuid>.tmp`). Only after format validation passes is the temp file atomically renamed (via `File.renameTo()` or `java.nio.file.Files.move`) to its final destination in `context.filesDir/alarms/`.

```
[SAF Content Uri]
       │
       ▼ (openInputStream)
┌───────────────────────────────────────────────┐
│ Stream Copy to temp_<uuid>.tmp               │
│ - Buffer: 8 KB                                │
│ - Max Size Guard: 50 MB                       │
│ - In-line SHA-256 Digest Calculation         │
└───────────────────────────────────────────────┘
       │
       ▼
┌───────────────────────────────────────────────┐
│ MP3 Format & Header Validation                │
│ - Magic Bytes (ID3v2 or MPEG Frame Sync)      │
│ - MediaMetadataRetriever (Safe Try/Catch)     │
│ - Duration > 0ms & <= 3600s                   │
└───────────────────────────────────────────────┘
       │
   [Valid?]
  ┌────┴────┐
  │         │
[Yes]      [No] ──► Delete temp file & throw ValidationException
  │
  ▼
┌───────────────────────────────────────────────┐
│ Atomic Move / Deduplication                   │
│ - If track_<sha256>.mp3 exists: delete temp   │
│ - Else: rename temp to track_<sha256>.mp3    │
└───────────────────────────────────────────────┘
```

---

## 3. Format Validation, MP3 Header Inspection, and Safe Metadata Extraction

Passing arbitrary binary files directly to native Android media extractors (`MediaMetadataRetriever` / `MediaExtractor`) can trigger native crashes, memory leaks, or unhandled `RuntimeException` instances. We implement a two-phase validation strategy: byte-level header inspection followed by protected metadata extraction.

### 3.1 Binary Pre-Validation: MP3 File Structure

An MP3 (MPEG-1 or MPEG-2 Audio Layer III) file conforms to one of two structural beginnings:
1. **Prefixed with an ID3v2 Metadata Container**: Starts at offset 0 with ASCII characters `"ID3"` (`0x49 0x44 0x33`).
2. **Raw MPEG Audio Stream**: Starts at offset 0 directly with an MPEG audio frame sync word (`0xFF 0xFB`, `0xFF 0xFA`, or `0xFF 0xF3`).

#### ID3v2 Tag Header Format (Informal Standard ID3v2.3 / ID3v2.4)
- **Bytes 0..2**: Magic identifier `'I', 'D', '3'` (`0x49, 0x44, 0x33`).
- **Byte 3**: Major version (`0x03` for ID3v2.3, `0x04` for ID3v2.4).
- **Byte 4**: Revision version.
- **Byte 5**: Flags byte (unsynchronization, extended header, experimental, footer).
- **Bytes 6..9**: 4 bytes representing the tag size as a **synchsafe integer** (the most significant bit of each byte is 0, yielding 28 effective bits):
  $$\text{TagSize} = ((b_6 \ \& \ 0\text{x}7\text{F}) \ll 21) \ | \ ((b_7 \ \& \ 0\text{x}7\text{F}) \ll 14) \ | \ ((b_8 \ \& \ 0\text{x}7\text{F}) \ll 7) \ | \ (b_9 \ \& \ 0\text{x}7\text{F})$$
- The total ID3v2 header overhead is $10 + \text{TagSize}$ bytes (plus an extra 10 bytes if a footer is flagged in ID3v2.4).
- Immediately following the ID3v2 tag, the raw MPEG audio frame sync header should appear.

#### MPEG Audio Frame Sync Header (ISO/IEC 11172-3 / 13818-3)
An MPEG audio frame starts with a 32-bit (4-byte) header:
- **Bits 31..21 (11 bits)**: Frame sync word, all set to `1` (`0xFF` followed by upper 3 bits `111` of byte 1).
- **Bits 20..19 (2 bits)**: MPEG Audio Version ID:
  - `00`: MPEG Version 2.5
  - `01`: Reserved
  - `10`: MPEG Version 2 (ISO/IEC 13818-3)
  - `11`: MPEG Version 1 (ISO/IEC 11172-3)
- **Bits 18..17 (2 bits)**: Layer description:
  - `00`: Reserved
  - `01`: Layer III (MP3)
  - `10`: Layer II
  - `11`: Layer I
- **Bit 16 (1 bit)**: Protection bit (`0` = protected by 16-bit CRC, `1` = not protected).

Therefore, for byte 0 and byte 1 of a raw MPEG Layer III frame:
- `byte[0] == 0xFF`
- `(byte[1] & 0xE0) == 0xE0` (sync bits)
- `(byte[1] & 0x18) != 0x08` (version is not reserved)
- `(byte[1] & 0x06) == 0x02` (layer description is Layer III)

If a file fails both the ID3 check and the MPEG frame sync check, it is not an MP3 file and must be rejected immediately without invoking native media libraries.

### 3.2 Safe Metadata Extraction via `MediaMetadataRetriever`

`android.media.MediaMetadataRetriever` is the standard Android framework utility for extracting audio duration, bitrate, artist, and title. However, its native C++ implementation (`android_media_MediaMetadataRetriever.cpp`) interacts with the system `media.extractor` binder service. 

#### Critical Pitfalls of `MediaMetadataRetriever`:
1. **Unchecked Native `RuntimeException`**: When presented with an invalid or corrupt audio stream, `setDataSource()` does not return an error code or throw a checked `IOException`; it throws an unchecked `java.lang.RuntimeException("setDataSource failed: status = 0x80000000")`. Any unhandled call will crash the application process immediately.
2. **Resource Leaks**: `MediaMetadataRetriever` allocates native memory and binder interfaces. On API 29+, it implements `AutoCloseable`. On API 26-28, `release()` must be explicitly called in a `finally` block.
3. **Null Returns**: `extractMetadata()` returns `null` if a tag is missing or unreadable. Directly passing the result of `extractMetadata(METADATA_KEY_DURATION)` to `java.lang.Long.parseLong()` will throw a `NullPointerException` or `NumberFormatException`.

#### Safe Extraction Pattern:
```kotlin
fun extractMetadataSafely(file: File): AudioMetadata {
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(file.absolutePath)
        
        val mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
        if (mimeType != null && mimeType != "audio/mpeg" && mimeType != "audio/mp3") {
            throw AudioValidationException("Invalid MIME type: $mimeType. Expected audio/mpeg.")
        }

        val hasAudio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)
        if (hasAudio != null && hasAudio != "yes") {
            throw AudioValidationException("File does not contain an audio stream.")
        }

        val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull() ?: throw AudioValidationException("Unable to determine audio duration.")

        if (durationMs < 1_000L) {
            throw AudioValidationException("Audio duration too short (${durationMs}ms). Minimum is 1 second.")
        }
        if (durationMs > 3_600_000L) {
            throw AudioValidationException("Audio duration too long (${durationMs}ms). Maximum is 1 hour.")
        }

        val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
        val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)

        return AudioMetadata(
            durationMs = durationMs,
            title = title?.takeIf { it.isNotBlank() },
            artist = artist?.takeIf { it.isNotBlank() }
        )
    } catch (e: RuntimeException) {
        throw AudioValidationException("MediaMetadataRetriever failed to parse audio file: ${e.message}", e)
    } finally {
        try {
            retriever.release()
        } catch (ignored: RuntimeException) {
            // Ignore release exceptions
        }
    }
}
```

---

## 4. Storage Lifecycle Management & Orphan Pruning

### 4.1 Domain Capacity & Reference Model

The Despertador domain model specifies:
- Up to **5 alarm slots** (`AlarmSlot`).
- Each alarm slot persists an optional reference to an `AudioMediaTrack` containing `filePath` (or `null` if using the default bundled sound).
- Multiple alarm slots may choose the same MP3 file. Because files are named by SHA-256 hash (`track_<sha256>.mp3`), multiple alarms share the exact same physical file on disk.

### 4.2 Database-First Deletion Ordering

Deleting a physical file before updating the database creates a catastrophic race condition: if the app is killed or the database write fails, the database retains a path to a non-existent file, causing the alarm to ring silently.

**Strict Invariant**: Database mutations must always complete successfully **before** unreferenced disk files are deleted.

```
User Action: Delete Alarm Slot / Change Audio Track
       │
       ▼
┌───────────────────────────────────────────────┐
│ 1. Execute Room Database Transaction          │
│    - Update alarm entity with new track path  │
│    - Or delete alarm slot from Room           │
└───────────────────────────────────────────────┘
       │
   [Committed?]
  ┌────┴────┐
  │         │
[Yes]      [No] ──► Abort. Disk untouched.
  │
  ▼
┌───────────────────────────────────────────────┐
│ 2. Asynchronous Reference Check (IO)          │
│    SELECT COUNT(*) FROM alarms                │
│    WHERE audio_file_path = :oldFilePath       │
└───────────────────────────────────────────────┘
       │
   [Count == 0?]
  ┌────┴────┐
  │         │
[Yes]      [No] ──► Retain file (still in use by another slot)
  │
  ▼
┌───────────────────────────────────────────────┐
│ 3. Prune File From Disk                       │
│    File(oldFilePath).delete()                 │
└───────────────────────────────────────────────┘
```

### 4.3 Cold Startup Reconciliation Sweep

If the app process terminates abruptly between Step 1 (database commit) and Step 3 (file deletion), an orphaned file remains on disk. Additionally, interrupted file copy operations can leave stale `.tmp` files.

During app cold start (e.g., in `Application.onCreate()` or via a background coroutine in the repository initialization), an orphan reconciliation sweep is executed:
1. Query Room for all distinct active audio paths:
   ```sql
   SELECT DISTINCT audio_file_path FROM alarms WHERE audio_file_path IS NOT NULL
   ```
2. Inspect the `context.filesDir/alarms/` directory:
   - For every `.mp3` file on disk: if its path is not present in the active set, delete it.
   - For every `.tmp` file on disk: if its last modified timestamp is older than 1 hour (3,600,000 ms), delete it.

---

## 5. Playback Engine Evaluation: MediaPlayer vs Jetpack Media3 ExoPlayer

For a native Android alarm clock, playback reliability is the top functional requirement. We evaluate both candidates against the specific requirements of looping background playback on `USAGE_ALARM`.

### 5.1 Comparative Analysis Matrix

| Evaluation Dimension | `android.media.MediaPlayer` (Platform API) | `androidx.media3.exoplayer` (Jetpack Media3) |
| :--- | :--- | :--- |
| **Google Official Guidance** | **Legacy framework component**. Google recommends against using it for new apps. | **Recommended standard** for all media playback on modern Android (developer.android.com/media/media3). |
| **APK Binary Footprint** | **0 KB** overhead (bundled in Android OS framework ROM). | **~1.5 MB - 2.5 MB** added APK size (when using ProGuard/R8 with `media3-exoplayer` only). |
| **State Machine Safety** | **Extremely brittle**. 10-state native C++ state machine (Idle, Initialized, Preparing, Prepared, Started, Paused, Stopped, PlaybackCompleted, End, Error). Calling methods out of order throws uncatchable native errors or `IllegalStateException`. | **Robust single-threaded architecture**. Unified reactive `Player` interface (`STATE_IDLE`, `STATE_BUFFERING`, `STATE_READY`, `STATE_ENDED`). Methods like `play()`, `prepare()`, `stop()` are safe to call from any state. |
| **Looping Behavior** | `mediaPlayer.isLooping = true`. Encounters audible stutter/silence at loop seams due to MP3 encoder delay/padding. On certain OEM ROMs, looping abruptly terminates after multiple iterations. | `exoPlayer.repeatMode = Player.REPEAT_MODE_ONE`. Pre-buffers frames and trims encoder delay/padding, ensuring consistent, gapless looping across all devices. |
| **Audio Attributes (`USAGE_ALARM`)** | Supported via `setAudioAttributes(AudioAttributes.Builder().setUsage(USAGE_ALARM).build())`. | Supported via `AudioAttributes.Builder().setUsage(C.USAGE_ALARM).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build()`. |
| **Audio Focus Handling** | **Manual implementation required**. Developer must manually construct `AudioFocusRequest`, handle `OnAudioFocusChangeListener`, and request/abandon focus via `AudioManager`. | **Automatic and declarative**. Pass `handleAudioFocus = true` to `setAudioAttributes()`. ExoPlayer manages focus transitions, pausing, and ducking automatically. |
| **Volume Ramping (Escalating Alarm)** | Manual `setVolume(left, right)`. Frequent calls during playback on older devices can cause audio crackling. | `player.volume = float`. Thread-safe, seamless volume adjustments suitable for smooth coroutine-based volume ramp-up animations. |
| **WakeLock Management** | `mediaPlayer.setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)`. Requires manual release coordination to avoid battery drain. | `exoPlayer.setWakeMode(C.WAKE_MODE_LOCAL)`. Automatically acquires and releases partial wake locks in sync with playback state. |
| **OEM Fragmentation** | **High risk**. Backed by device-specific Stagefright / NuPlayer implementations modified by OEMs (Samsung, Xiaomi, MediaTek). Known to exhibit inconsistent bugs across device models. | **Zero OEM variance**. Media3 runs as application code calling standard Android `MediaCodec` NDK APIs. Codec workarounds are actively maintained in Jetpack. |
| **Testability** | Very difficult to unit test on JVM. Robolectric's `ShadowMediaPlayer` does not replicate native decoder state transitions. | Highly testable. Supports `Player` interface mocking, fake players, and test fixtures. |

### 5.2 Deep-Dive: State Machine and Error Recovery

`MediaPlayer`'s native state machine is a frequent source of production crashes in Android alarm applications:
- If `start()` is invoked before `onPrepared()` fires, an `IllegalStateException` crashes the thread.
- If an error occurs, `MediaPlayer.OnErrorListener.onError(mp, what, extra)` is invoked. If the callback returns `false`, `MediaPlayer` calls `OnCompletionListener`, which might inadvertently trigger alarm completion logic while the player is broken.
- Recreating a broken `MediaPlayer` inside a Foreground Service while the screen is locked requires resetting native binders (`mp.reset()`, `mp.release()`), which can hang on vendor audio HAL crashes.

In contrast, Media3 ExoPlayer provides:
- Clear, typed error callbacks via `Player.Listener.onPlayerError(PlaybackException)`.
- Non-blocking error recovery: calling `player.prepare()` followed by `player.play()` safely restarts playback without recreating the player instance.
- Direct coroutine integration: playback state changes map directly to Kotlin `StateFlow` or coroutine events.

### 5.3 Architectural Recommendation for Despertador

**Decision: Adopt `androidx.media3:media3-exoplayer`.**

**Rationale**:
1. An alarm clock is an emergency-critical utility. An unexpected native `IllegalStateException` or an unhandled OEM codec hang translates directly into a missed alarm for the user.
2. The APK size overhead (~1.5 MB with R8) is negligible on modern Android devices (minSdk 26+).
3. The automatic audio focus handling (`handleAudioFocus = true`) and built-in wake lock management (`C.WAKE_MODE_LOCAL`) drastically simplify the Foreground Service lifecycle, eliminating subtle wake lock leaks or race conditions during screen-off states.
4. Programmatic volume ramping for escalating alarms is smooth and crash-resilient with `player.volume`.

---

## 6. Complete Kotlin Reference Implementations

Below are complete, production-ready reference implementations designed specifically for Despertador.

### 6.1 `AudioTrackStorageManager.kt`
Handles SAF copying, SHA-256 deduplication, atomic file movement, and orphan pruning.

```kotlin
package com.despertador.audio

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID

class AudioTrackStorageManager(
    private val context: Context,
    private val validator: Mp3Validator = Mp3Validator()
) {
    private val alarmsDir: File by lazy {
        File(context.filesDir, "alarms").apply {
            if (!exists()) mkdirs()
        }
    }

    data class ImportedAudioTrack(
        val file: File,
        val sha256: String,
        val displayName: String,
        val durationMs: Long
    )

    /**
     * Safely copies an audio stream from an external SAF Uri into internal storage,
     * validates its MP3 structure and duration, and deduplicates via SHA-256 hash.
     */
    suspend fun importAudioFromSaf(sourceUri: Uri): ImportedAudioTrack = withContext(Dispatchers.IO) {
        val displayName = queryDisplayName(sourceUri) ?: "custom_alarm.mp3"
        val tempFile = File(alarmsDir, "temp_${UUID.randomUUID()}.tmp")

        try {
            // 1. Stream copy to temp file with bounded size and hash calculation
            val digest = MessageDigest.getInstance("SHA-256")
            val maxSizeBytes = 50 * 1024 * 1024L // 50 MB limit
            var totalBytes = 0L

            context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                FileOutputStream(tempFile).use { outputStream ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        totalBytes += bytesRead
                        if (totalBytes > maxSizeBytes) {
                            throw AudioValidationException("Audio file exceeds maximum size limit of 50 MB.")
                        }
                        digest.update(buffer, 0, bytesRead)
                        outputStream.write(buffer, 0, bytesRead)
                    }
                    outputStream.flush()
                }
            } ?: throw IOException("Unable to open input stream for URI: $sourceUri")

            if (totalBytes < 1024L) {
                throw AudioValidationException("Audio file is too small (minimum 1 KB).")
            }

            val sha256Hex = digest.digest().joinToString("") { "%02x".format(it) }

            // 2. Format & Metadata Validation
            val metadata = validator.validateAndExtract(tempFile)

            // 3. Deduplication and Atomic Move
            val destinationFile = File(alarmsDir, "track_${sha256Hex}.mp3")
            if (destinationFile.exists()) {
                // File already cached for another alarm slot; delete temp
                tempFile.delete()
            } else {
                val renamed = tempFile.renameTo(destinationFile)
                if (!renamed) {
                    // Fallback to NIO move if atomic rename fails
                    java.nio.file.Files.move(
                        tempFile.toPath(),
                        destinationFile.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING
                    )
                }
            }

            ImportedAudioTrack(
                file = destinationFile,
                sha256 = sha256Hex,
                displayName = metadata.title ?: displayName,
                durationMs = metadata.durationMs
            )
        } catch (e: Exception) {
            if (tempFile.exists()) tempFile.delete()
            throw e
        }
    }

    /**
     * Deletes the specified file if no remaining alarms reference it.
     */
    suspend fun pruneIfUnreferenced(filePath: String, isReferenced: suspend (String) -> Boolean) =
        withContext(Dispatchers.IO) {
            val file = File(filePath)
            if (file.exists() && file.parentFile == alarmsDir) {
                if (!isReferenced(filePath)) {
                    file.delete()
                }
            }
        }

    /**
     * Startup reconciliation: sweeps alarms directory against active database paths.
     */
    suspend fun sweepOrphanedFiles(activeFilePaths: Set<String>) = withContext(Dispatchers.IO) {
        val files = alarmsDir.listFiles() ?: return@withContext
        val now = System.currentTimeMillis()
        val oneHourMs = 3_600_000L

        for (file in files) {
            if (file.name.endsWith(".tmp")) {
                if (now - file.lastModified() > oneHourMs) {
                    file.delete()
                }
            } else if (file.name.endsWith(".mp3")) {
                if (file.absolutePath !in activeFilePaths) {
                    file.delete()
                }
            }
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) cursor.getString(index) else null
            } else null
        }
    }
}
```

---

### 6.2 `Mp3Validator.kt`
Validates ID3v2/MPEG headers and wraps `MediaMetadataRetriever` in defensive guards.

```kotlin
package com.despertador.audio

import android.media.MediaMetadataRetriever
import java.io.File
import java.io.FileInputStream
import java.io.IOException

class AudioValidationException(message: String, cause: Throwable? = null) : Exception(message, cause)

data class ValidatedAudioMetadata(
    val durationMs: Long,
    val title: String?,
    val artist: String?
)

class Mp3Validator {

    /**
     * Performs two-phase validation:
     * 1. Magic byte inspection (ID3v2 or MPEG Frame Sync).
     * 2. MediaMetadataRetriever duration and mime-type verification.
     */
    fun validateAndExtract(file: File): ValidatedAudioMetadata {
        validateHeaderBytes(file)
        return extractMetadataDefensively(file)
    }

    private fun validateHeaderBytes(file: File) {
        val header = ByteArray(10)
        val bytesRead = try {
            FileInputStream(file).use { it.read(header) }
        } catch (e: IOException) {
            throw AudioValidationException("Failed to read audio file header.", e)
        }

        if (bytesRead < 10) {
            throw AudioValidationException("File too small to contain a valid MP3 header.")
        }

        // Case 1: ID3v2 container ("ID3")
        val isId3v2 = header[0] == 0x49.toByte() && // 'I'
                      header[1] == 0x44.toByte() && // 'D'
                      header[2] == 0x33.toByte()    // '3'

        if (isId3v2) {
            val majorVersion = header[3].toInt() and 0xFF
            if (majorVersion !in 2..4) {
                throw AudioValidationException("Unsupported ID3v2 version: 2.$majorVersion")
            }
            return // Valid ID3 container found
        }

        // Case 2: Raw MPEG Audio Frame Sync (11 bits of 1s: 0xFF followed by 0xE0 mask)
        val byte0 = header[0].toInt() and 0xFF
        val byte1 = header[1].toInt() and 0xFF

        val isSyncWord = (byte0 == 0xFF) && ((byte1 and 0xE0) == 0xE0)
        val mpegVersionBits = (byte1 shr 3) and 0x03
        val layerBits = (byte1 shr 1) and 0x03

        val isValidMpegVersion = mpegVersionBits != 0x01 // 01 is reserved
        val isLayer3 = layerBits == 0x01                 // 01 is Layer III (MP3)

        if (isSyncWord && isValidMpegVersion && isLayer3) {
            return // Valid raw MPEG Layer III frame found
        }

        throw AudioValidationException(
            "File header does not match MP3 specifications (neither ID3v2 nor MPEG Layer III frame sync detected)."
        )
    }

    private fun extractMetadataDefensively(file: File): ValidatedAudioMetadata {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)

            val mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
            if (mimeType != null && mimeType != "audio/mpeg" && mimeType != "audio/mp3") {
                throw AudioValidationException("MIME type '$mimeType' is not supported. Must be 'audio/mpeg'.")
            }

            val hasAudio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)
            if (hasAudio != null && hasAudio != "yes") {
                throw AudioValidationException("File does not contain an active audio stream.")
            }

            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull()
                ?: throw AudioValidationException("Failed to read audio duration.")

            if (durationMs < 1_000L) {
                throw AudioValidationException("Audio duration (${durationMs}ms) is shorter than minimum allowed (1s).")
            }
            if (durationMs > 3_600_000L) {
                throw AudioValidationException("Audio duration (${durationMs}ms) exceeds maximum limit (1 hour).")
            }

            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.trim()?.takeIf { it.isNotEmpty() }
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?.trim()?.takeIf { it.isNotEmpty() }

            return ValidatedAudioMetadata(
                durationMs = durationMs,
                title = title,
                artist = artist
            )
        } catch (e: RuntimeException) {
            throw AudioValidationException("Media framework failed to parse audio track: ${e.message}", e)
        } finally {
            try {
                retriever.release()
            } catch (ignored: RuntimeException) {
                // Prevent release exceptions from masking primary validation errors
            }
        }
    }
}
```

---

### 6.3 `AlarmAudioPlayer.kt`
Media3 ExoPlayer configuration for looping background playback on `USAGE_ALARM` with volume ramping.

```kotlin
package com.despertador.audio

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class AlarmAudioPlayer(
    private val context: Context,
    private val coroutineScope: CoroutineScope
) {
    private var exoPlayer: ExoPlayer? = null
    private var volumeRampJob: Job? = null

    private val _playbackError = MutableStateFlow<PlaybackException?>(null)
    val playbackError: StateFlow<PlaybackException?> = _playbackError.asStateFlow()

    /**
     * Initializes and starts looping alarm playback on USAGE_ALARM stream.
     *
     * @param audioFile The local MP3 file to play.
     * @param rampDurationMs If > 0, ramps volume from 0.0 to 1.0 over this duration.
     */
    fun startAlarmPlayback(audioFile: File, rampDurationMs: Long = 0L) {
        stopPlayback()
        _playbackError.value = null

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_ALARM)
            .build()

        val player = ExoPlayer.Builder(context)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        player.repeatMode = Player.REPEAT_MODE_ONE
        player.setMediaItem(MediaItem.fromUri(audioFile.toURI().toString()))

        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                _playbackError.value = error
            }
        })

        if (rampDurationMs > 0L) {
            player.volume = 0.0f
            startVolumeRamp(player, rampDurationMs)
        } else {
            player.volume = 1.0f
        }

        player.prepare()
        player.play()
        exoPlayer = player
    }

    private fun startVolumeRamp(player: ExoPlayer, rampDurationMs: Long) {
        volumeRampJob?.cancel()
        volumeRampJob = coroutineScope.launch {
            val stepIntervalMs = 200L
            val totalSteps = (rampDurationMs / stepIntervalMs).coerceAtLeast(1)
            val volumeIncrement = 1.0f / totalSteps
            var currentVolume = 0.0f

            while (isActive && currentVolume < 1.0f) {
                delay(stepIntervalMs)
                currentVolume = (currentVolume + volumeIncrement).coerceAtMost(1.0f)
                player.volume = currentVolume
            }
        }
    }

    /**
     * Immediately stops playback, cancels volume ramping, and releases player resources.
     */
    fun stopPlayback() {
        volumeRampJob?.cancel()
        volumeRampJob = null

        exoPlayer?.let { player ->
            player.stop()
            player.release()
        }
        exoPlayer = null
    }
}
```

---

## 7. Primary Source Reference Index

1. **Android Storage Access Framework (SAF) & File Access**:
   - *Access documents and other files from shared storage*:  
     [https://developer.android.com/training/data-storage/shared/documents-files](https://developer.android.com/training/data-storage/shared/documents-files)
   - *Intent ACTION_OPEN_DOCUMENT*:  
     [https://developer.android.com/reference/android/content/Intent#ACTION_OPEN_DOCUMENT](https://developer.android.com/reference/android/content/Intent#ACTION_OPEN_DOCUMENT)
   - *ActivityResultContracts.OpenDocument*:  
     [https://developer.android.com/reference/androidx/activity/result/contract/ActivityResultContracts.OpenDocument](https://developer.android.com/reference/androidx/activity/result/contract/ActivityResultContracts.OpenDocument)
   - *OpenableColumns API*:  
     [https://developer.android.com/reference/android/provider/OpenableColumns](https://developer.android.com/reference/android/provider/OpenableColumns)
   - *App-specific storage (Internal Storage vs Cache)*:  
     [https://developer.android.com/training/data-storage/app-specific](https://developer.android.com/training/data-storage/app-specific)

2. **Metadata Extraction & Android Media Framework**:
   - *MediaMetadataRetriever API Reference*:  
     [https://developer.android.com/reference/android/media/MediaMetadataRetriever](https://developer.android.com/reference/android/media/MediaMetadataRetriever)
   - *AOSP MediaMetadataRetriever C++ JNI Implementation (`frameworks/base/media/jni/android_media_MediaMetadataRetriever.cpp`)*:  
     [https://cs.android.com/android/platform/superproject/+/main:frameworks/base/media/jni/android_media_MediaMetadataRetriever.cpp](https://cs.android.com/android/platform/superproject/+/main:frameworks/base/media/jni/android_media_MediaMetadataRetriever.cpp)

3. **Audio Specifications**:
   - *ID3v2.3 Informal Standard & Header Definition*:  
     [https://id3.org/id3v2.3.0](https://id3.org/id3v2.3.0)
   - *ISO/IEC 11172-3: Information technology — Coding of moving pictures and associated audio for digital storage media at up to about 1,5 Mbit/s — Part 3: Audio (MPEG-1 Audio)*:  
     [https://www.iso.org/standard/22412.html](https://www.iso.org/standard/22412.html)

4. **Jetpack Media3 & Audio Routing**:
   - *Jetpack Media3 Overview & MediaPlayer Migration Guide*:  
     [https://developer.android.com/media/media3](https://developer.android.com/media/media3)
   - *ExoPlayer Audio Attributes & Audio Focus*:  
     [https://developer.android.com/media/media3/exoplayer/audio-focus](https://developer.android.com/media/media3/exoplayer/audio-focus)
   - *ExoPlayer API Reference (`androidx.media3.exoplayer.ExoPlayer`)*:  
     [https://developer.android.com/reference/androidx/media3/exoplayer/ExoPlayer](https://developer.android.com/reference/androidx/media3/exoplayer/ExoPlayer)
   - *AudioAttributes API (`android.media.AudioAttributes#USAGE_ALARM`)*:  
     [https://developer.android.com/reference/android/media/AudioAttributes#USAGE_ALARM](https://developer.android.com/reference/android/media/AudioAttributes#USAGE_ALARM)
   - *PowerManager and WakeLocks during Media Playback*:  
     [https://developer.android.com/training/scheduling/wakelock](https://developer.android.com/training/scheduling/wakelock)
