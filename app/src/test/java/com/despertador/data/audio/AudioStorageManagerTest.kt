package com.despertador.data.audio

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File

class AudioStorageManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var alarmsDir: File
    private lateinit var validator: Mp3Validator
    private lateinit var storageManager: AudioStorageManager

    // Valid 2 KB mock MP3 payload starting with ID3v2.3 header
    private val validMp3Payload: ByteArray by lazy {
        val header = byteArrayOf(
            0x49, 0x44, 0x33, // "ID3"
            0x03, 0x00, 0x00,
            0x00, 0x00, 0x01, 0x00
        )
        val body = ByteArray(2048) { it.toByte() }
        header + body
    }

    @Before
    fun setUp() {
        val rootDir = tempFolder.newFolder("filesDir")
        alarmsDir = File(rootDir, "alarms")
        validator = Mp3Validator()
        storageManager = AudioStorageManager(rootDir, validator)
    }

    @Test
    fun importsValidMp3SuccessfullyWithSha256Naming() = runTest {
        val stream = ByteArrayInputStream(validMp3Payload)
        val result = storageManager.importAudioStream(stream, "wake_up.mp3")

        assertTrue(result.isSuccess)
        val track = result.getOrThrow()

        assertEquals("wake_up.mp3", track.originalFileName)
        assertTrue(track.filePath.startsWith(alarmsDir.absolutePath))
        assertTrue(File(track.filePath).exists())
        assertEquals(track.sha256Hash, track.file.name.removePrefix("track_").removeSuffix(".mp3"))
        assertEquals(validMp3Payload.size.toLong(), track.file.length())
    }

    @Test
    fun deduplicatesIdenticalFilesToSameDestinationFile() = runTest {
        val stream1 = ByteArrayInputStream(validMp3Payload)
        val stream2 = ByteArrayInputStream(validMp3Payload)

        val result1 = storageManager.importAudioStream(stream1, "first.mp3").getOrThrow()
        val result2 = storageManager.importAudioStream(stream2, "second.mp3").getOrThrow()

        assertEquals(result1.sha256Hash, result2.sha256Hash)
        assertEquals(result1.filePath, result2.filePath)

        // Only one physical track file should exist in alarms directory
        val mp3Files = alarmsDir.listFiles { _, name -> name.endsWith(".mp3") } ?: emptyArray()
        assertEquals(1, mp3Files.size)
    }

    @Test
    fun rejectsNonMp3AudioHeaderAndCleansUpTempFiles() = runTest {
        val invalidPayload = "This is not an audio file but plain text content".toByteArray()
        val stream = ByteArrayInputStream(invalidPayload)

        val result = storageManager.importAudioStream(stream, "not_audio.mp3")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AudioValidationException)

        // Ensure temp file was cleaned up
        val files = alarmsDir.listFiles() ?: emptyArray()
        assertEquals(0, files.size)
    }

    @Test
    fun rejectsFileExceeding50Mb() = runTest {
        // Stream that reports exceeding 50 MB (50 * 1024 * 1024 + 1)
        val maxLimit = 50 * 1024 * 1024L
        val oversizedStream = object : java.io.InputStream() {
            private var bytesRead = 0L
            override fun read(): Int {
                if (bytesRead > maxLimit + 10) return -1
                bytesRead++
                return if (bytesRead < 4) 0x49 else 0x00
            }

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (bytesRead > maxLimit + 10) return -1
                val toRead = minOf(len.toLong(), (maxLimit + 10) - bytesRead).toInt()
                if (toRead <= 0) return -1
                bytesRead += toRead
                return toRead
            }
        }

        val result = storageManager.importAudioStream(oversizedStream, "huge.mp3")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AudioValidationException)

        // Ensure temp file was deleted
        val tempFiles = alarmsDir.listFiles { _, name -> name.endsWith(".tmp") } ?: emptyArray()
        assertEquals(0, tempFiles.size)
    }

    @Test
    fun sweepOrphanedFilesDeletesUnreferencedTracksAndStaleTempFiles() = runTest {
        val stream = ByteArrayInputStream(validMp3Payload)
        val track = storageManager.importAudioStream(stream, "song.mp3").getOrThrow()

        // Create an unreferenced MP3 file
        val unreferencedFile = File(alarmsDir, "track_unreferenced12345.mp3").apply {
            writeBytes(validMp3Payload)
        }

        // Create a stale .tmp file older than 1 hour
        val staleTempFile = File(alarmsDir, "temp_stale.tmp").apply {
            writeBytes(byteArrayOf(1, 2, 3))
            setLastModified(System.currentTimeMillis() - 4_000_000L) // > 1 hour
        }

        // Create a recent .tmp file (should NOT be deleted yet)
        val recentTempFile = File(alarmsDir, "temp_recent.tmp").apply {
            writeBytes(byteArrayOf(1, 2, 3))
            setLastModified(System.currentTimeMillis())
        }

        // Sweep keeping only `track.filePath`
        storageManager.sweepOrphanedFiles(activeFilePaths = setOf(track.filePath))

        assertTrue("Active track should still exist", track.file.exists())
        assertFalse("Unreferenced file should be deleted", unreferencedFile.exists())
        assertFalse("Stale temp file should be deleted", staleTempFile.exists())
        assertTrue("Recent temp file should still exist", recentTempFile.exists())
    }

    @Test
    fun pruneIfUnreferencedDeletesFileWhenNotReferenced() = runTest {
        val stream = ByteArrayInputStream(validMp3Payload)
        val track = storageManager.importAudioStream(stream, "song.mp3").getOrThrow()

        // Reference check returns false -> deleted
        storageManager.pruneIfUnreferenced(track.filePath) { false }
        assertFalse(track.file.exists())
    }

    @Test
    fun pruneIfUnreferencedRetainsFileWhenStillReferenced() = runTest {
        val stream = ByteArrayInputStream(validMp3Payload)
        val track = storageManager.importAudioStream(stream, "song.mp3").getOrThrow()

        // Reference check returns true -> retained
        storageManager.pruneIfUnreferenced(track.filePath) { true }
        assertTrue(track.file.exists())
    }
}
