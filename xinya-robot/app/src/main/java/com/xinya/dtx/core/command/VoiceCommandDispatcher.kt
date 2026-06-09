package com.xinya.dtx.core.command

import com.xinya.dtx.core.voice.VoiceIntent
import javax.inject.Inject
import javax.inject.Singleton

fun interface VoiceIntentHandler {
    suspend fun handle(intent: VoiceIntent)
}

@Singleton
class VoiceCommandDispatcher @Inject constructor() {
    private val handlers = linkedMapOf<String, VoiceIntentHandler>()

    fun register(intentCode: String, handler: VoiceIntentHandler) {
        handlers[intentCode] = handler
    }

    suspend fun dispatch(intent: VoiceIntent): Boolean {
        val handler = handlers[intent.code] ?: return false
        handler.handle(intent)
        return true
    }
}
