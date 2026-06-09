package com.xinya.dtx.core.speech

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UnavailableOfflineTtsEngine @Inject constructor() : OfflineTtsEngine {
    override suspend fun speak(text: String, interruptCurrent: Boolean, volume: Int) {
        throw IllegalStateException(
            "Offline TTS is not configured yet. Wire the client-provided AIKit implementation before calling speak()."
        )
    }

    override suspend fun stop() = Unit
}
