package com.xinya.dtx.core.voice

import android.media.AudioManager
import android.media.ToneGenerator
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay

@Singleton
class VoicePromptPlayer @Inject constructor() {
    suspend fun playWakeupTone(volume: Int) {
        val tone = ToneGenerator(AudioManager.STREAM_MUSIC, volume.coerceIn(0, 100))
        try {
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, WAKEUP_TONE_DURATION_MS)
            delay(WAKEUP_TONE_DURATION_MS.toLong())
        } finally {
            tone.release()
        }
    }

    private companion object {
        const val WAKEUP_TONE_DURATION_MS = 120
    }
}
