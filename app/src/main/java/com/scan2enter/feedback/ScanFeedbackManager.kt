package com.scan2enter.feedback

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator

object ScanFeedbackManager {

    private val tone = ToneGenerator(
        AudioManager.STREAM_MUSIC,
        90
    )

    fun beep() {
        tone.startTone(
            ToneGenerator.TONE_PROP_BEEP,
            120
        )
    }
}