package com.despertador.data.audio

import java.io.File

class AudioValidationException(message: String, cause: Throwable? = null) : Exception(message, cause)

data class ImportedAudioTrack(
    val file: File,
    val filePath: String = file.absolutePath,
    val sha256Hash: String,
    val originalFileName: String
)
