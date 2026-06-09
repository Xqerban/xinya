package com.xinya.dtx.core.speech

import com.xinya.dtx.core.voice.VoiceInteractionConfig
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Development-only wakeup engine that emits a wake event when started.
 * This lets us exercise the voice workflow without the AIKit wakeup stack.
 */
@Singleton
class ManualWakeupEngine @Inject constructor(
    private val voiceConfig: VoiceInteractionConfig,
) : WakeupEngine {
    private val _events = MutableSharedFlow<WakeupEvent>(extraBufferCapacity = 4)
    override val events: Flow<WakeupEvent> = _events

    override suspend fun start() {
        _events.emit(
            WakeupEvent(
                keyword = voiceConfig.primaryWakeWord,
                isPrimary = true,
            )
        )
    }

    override suspend fun stop() = Unit
}
