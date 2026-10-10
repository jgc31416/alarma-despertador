package com.despertador.data.audio

import java.io.InputStream

class Mp3Validator {

    /**
     * Inspects the first few bytes of the input stream to check for:
     * 1. ID3v2 container header ("ID3", versions 2.2, 2.3, 2.4)
     * 2. Raw MPEG Layer III frame sync word (0xFF followed by 0xE0 sync bits, valid MPEG version, Layer III)
     *
     * Returns true if a valid MP3 header or frame sync is detected, false otherwise.
     */
    fun validateHeader(inputStream: InputStream): Boolean {
        val header = ByteArray(10)
        var bytesRead = 0
        while (bytesRead < header.size) {
            val count = inputStream.read(header, bytesRead, header.size - bytesRead)
            if (count == -1) break
            bytesRead += count
        }

        if (bytesRead < 4) {
            return false
        }

        // Case 1: ID3v2 Tag Container ("ID3")
        if (header[0] == 0x49.toByte() && header[1] == 0x44.toByte() && header[2] == 0x33.toByte()) {
            val majorVersion = header[3].toInt() and 0xFF
            return majorVersion in 2..4
        }

        // Case 2: Raw MPEG Audio Frame Sync Header
        val byte0 = header[0].toInt() and 0xFF
        val byte1 = header[1].toInt() and 0xFF

        val isSyncWord = (byte0 == 0xFF) && ((byte1 and 0xE0) == 0xE0)
        if (!isSyncWord) {
            return false
        }

        val mpegVersionBits = (byte1 shr 3) and 0x03
        val layerBits = (byte1 shr 1) and 0x03

        val isValidMpegVersion = mpegVersionBits != 0x01 // 01 is reserved
        val isLayer3 = layerBits == 0x01                 // 01 is Layer III (MP3)

        return isValidMpegVersion && isLayer3
    }
}
