package com.despertador.data.audio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID

class AudioStorageManager(
    private val filesDir: File,
    private val validator: Mp3Validator = Mp3Validator()
) {

    private val alarmsDir: File by lazy {
        File(filesDir, "alarms").apply {
            if (!exists()) mkdirs()
        }
    }

    /**
     * Imports an audio stream into internal storage (`filesDir/alarms/`).
     * - Limits file size to 50 MB (rejects if >50 MB or <1 KB)
     * - Computes SHA-256 hash in-flight
     * - Validates MP3 header structure
     * - Moves atomically to `track_<sha256>.mp3` with deduplication
     */
    /**
     * Imports an audio stream into internal storage (`filesDir/alarms/`).
     * - Inspects initial header bytes in memory before disk ingestion
     * - Limits file size to 50 MB (rejects if >50 MB)
     * - Computes SHA-256 hash in-flight
     * - Moves atomically to `track_<sha256>.mp3` with deduplication
     */
    suspend fun importAudioStream(
        inputStream: InputStream,
        originalFileName: String? = null
    ): Result<ImportedAudioTrack> = withContext(Dispatchers.IO) {
        val resolvedFileName = originalFileName?.ifBlank { "custom_alarm.mp3" } ?: "custom_alarm.mp3"
        val tempFile = File(alarmsDir, "temp_${UUID.randomUUID()}.tmp")

        try {
            inputStream.use { stream ->
                // 1. Inspect initial bytes in memory before persisting
                val headerBuffer = ByteArray(10)
                var headerBytesRead = 0
                while (headerBytesRead < headerBuffer.size) {
                    val count = stream.read(headerBuffer, headerBytesRead, headerBuffer.size - headerBytesRead)
                    if (count == -1) break
                    headerBytesRead += count
                }

                val headerStream = java.io.ByteArrayInputStream(headerBuffer, 0, headerBytesRead)
                if (!validator.validateHeader(headerStream)) {
                    throw AudioValidationException("Invalid MP3 header (neither ID3v2 nor MPEG Layer III frame sync detected).")
                }

                // 2. Stream to temp file with bounded size and in-line SHA-256 computation
                val digest = MessageDigest.getInstance("SHA-256")
                val maxSizeBytes = 50 * 1024 * 1024L // 50 MB
                var totalBytes = headerBytesRead.toLong()

                digest.update(headerBuffer, 0, headerBytesRead)

                FileOutputStream(tempFile).use { outputStream ->
                    outputStream.write(headerBuffer, 0, headerBytesRead)
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (stream.read(buffer).also { bytesRead = it } != -1) {
                        totalBytes += bytesRead
                        if (totalBytes > maxSizeBytes) {
                            throw AudioValidationException("Audio file exceeds maximum size limit of 50 MB.")
                        }
                        digest.update(buffer, 0, bytesRead)
                        outputStream.write(buffer, 0, bytesRead)
                    }
                    outputStream.flush()
                }

                val sha256Hex = digest.digest().joinToString("") { "%02x".format(it) }
                val destinationFile = File(alarmsDir, "track_$sha256Hex.mp3")

                if (destinationFile.exists()) {
                    // Deduplicated: file already exists in sandbox
                    tempFile.delete()
                } else {
                    val renamed = tempFile.renameTo(destinationFile)
                    if (!renamed) {
                        java.nio.file.Files.move(
                            tempFile.toPath(),
                            destinationFile.toPath(),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING
                        )
                    }
                }

                Result.success(
                    ImportedAudioTrack(
                        file = destinationFile,
                        filePath = destinationFile.absolutePath,
                        sha256Hash = sha256Hex,
                        originalFileName = resolvedFileName
                    )
                )
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            if (tempFile.exists()) tempFile.delete()
            throw e
        } catch (e: Exception) {
            if (tempFile.exists()) {
                tempFile.delete()
            }
            Result.failure(e)
        }
    }

    /**
     * Deletes the specified file if no remaining alarms reference it.
     */
    suspend fun pruneIfUnreferenced(
        filePath: String,
        isReferenced: suspend (String) -> Boolean
    ) = withContext(Dispatchers.IO) {
        val file = File(filePath)
        if (file.exists() && file.parentFile == alarmsDir) {
            if (!isReferenced(filePath)) {
                file.delete()
            }
        }
    }

    /**
     * Startup reconciliation: sweeps alarms directory against active database paths.
     * Deletes unreferenced `.mp3` files and stale `.tmp` files older than 1 hour.
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
}
