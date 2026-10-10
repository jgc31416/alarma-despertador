package com.despertador.data.audio

import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts

class AudioPickerContract : ActivityResultContracts.OpenDocument() {
    override fun createIntent(context: Context, input: Array<String>): Intent {
        val intent = super.createIntent(context, if (input.isEmpty()) AUDIO_MIME_TYPES else input)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        return intent
    }

    companion object {
        val AUDIO_MIME_TYPES = arrayOf("audio/mpeg", "audio/*")
    }
}
