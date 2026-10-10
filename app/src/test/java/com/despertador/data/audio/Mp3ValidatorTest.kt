package com.despertador.data.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream

class Mp3ValidatorTest {

    private lateinit var validator: Mp3Validator

    @Before
    fun setUp() {
        validator = Mp3Validator()
    }

    @Test
    fun validatesId3v23HeaderSuccessfully() {
        // ID3v2.3 header: "ID3" (0x49, 0x44, 0x33), major version 3, revision 0, flags 0, synchsafe size
        val id3Header = byteArrayOf(
            0x49, 0x44, 0x33, // "ID3"
            0x03,             // major version 3
            0x00,             // revision 0
            0x00,             // flags
            0x00, 0x00, 0x02, 0x00 // synchsafe size
        )
        val stream = ByteArrayInputStream(id3Header + ByteArray(100))
        assertTrue(validator.validateHeader(stream))
    }

    @Test
    fun validatesId3v24HeaderSuccessfully() {
        // ID3v2.4 header: "ID3", major version 4
        val id3Header = byteArrayOf(
            0x49, 0x44, 0x33,
            0x04,
            0x00,
            0x00,
            0x00, 0x00, 0x01, 0x00
        )
        val stream = ByteArrayInputStream(id3Header + ByteArray(50))
        assertTrue(validator.validateHeader(stream))
    }

    @Test
    fun validatesRawMpegLayer3FrameSyncHeaderSuccessfully() {
        // Raw MPEG-1 Layer 3:
        // Byte 0: 0xFF
        // Byte 1: 0xFB (sync bits 111, MPEG-1: 11, Layer III: 01, No CRC: 1 -> 1111 1011 = 0xFB)
        val rawMpegHeader = byteArrayOf(
            0xFF.toByte(),
            0xFB.toByte(),
            0x90.toByte(), // Bitrate, sampling rate, padding, private
            0x44.toByte()  // Channel mode, mode extension, copyright, original, emphasis
        ) + ByteArray(200)

        val stream = ByteArrayInputStream(rawMpegHeader)
        assertTrue(validator.validateHeader(stream))
    }

    @Test
    fun validatesRawMpeg2Layer3FrameSyncHeaderSuccessfully() {
        // Raw MPEG-2 Layer 3:
        // Byte 0: 0xFF
        // Byte 1: 0xF3 (sync bits 111, MPEG-2: 10, Layer III: 01, No CRC: 1 -> 1111 0011 = 0xF3)
        val rawMpegHeader = byteArrayOf(
            0xFF.toByte(),
            0xF3.toByte(),
            0x90.toByte(),
            0x44.toByte()
        ) + ByteArray(200)

        val stream = ByteArrayInputStream(rawMpegHeader)
        assertTrue(validator.validateHeader(stream))
    }

    @Test
    fun rejectsNonMp3FileHeaders() {
        // PNG header: 89 50 4E 47 0D 0A 1A 0A
        val pngHeader = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00
        )
        assertFalse(validator.validateHeader(ByteArrayInputStream(pngHeader)))

        // RIFF / WAV header
        val wavHeader = "RIFF....WAVEfmt ".toByteArray(Charsets.US_ASCII)
        assertFalse(validator.validateHeader(ByteArrayInputStream(wavHeader)))

        // Random text file
        val textFile = "Hello world this is not an audio file".toByteArray(Charsets.UTF_8)
        assertFalse(validator.validateHeader(ByteArrayInputStream(textFile)))
    }

    @Test
    fun rejectsTruncatedStreamUnder4Bytes() {
        val tiny = byteArrayOf(0x49, 0x44)
        assertFalse(validator.validateHeader(ByteArrayInputStream(tiny)))
    }
}
