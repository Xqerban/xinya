package com.xinya.dtx.core.voice

import com.xinya.dtx.core.command.VoiceCommandDispatcher
import com.xinya.dtx.core.command.VoiceIntentParser
import com.xinya.dtx.core.speech.CommandDebugResult
import com.xinya.dtx.core.speech.CommandWordEngine
import com.xinya.dtx.core.speech.IatRecognitionResult
import com.xinya.dtx.core.speech.OfflineIatEngine
import com.xinya.dtx.core.speech.OfflineTtsEngine
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

sealed interface OfflineVoiceEvent {
    data class Transcript(val result: IatRecognitionResult) : OfflineVoiceEvent
    data class CommandDebug(val result: CommandDebugResult) : OfflineVoiceEvent
    data class IntentResolved(val intent: VoiceIntent) : OfflineVoiceEvent
    data class IntentUnhandled(val intent: VoiceIntent) : OfflineVoiceEvent
}

@Singleton
class OfflineVoiceFacade @Inject constructor(
    private val iatEngine: OfflineIatEngine,
    private val commandWordEngine: CommandWordEngine,
    private val ttsEngine: OfflineTtsEngine,
    private val intentParser: VoiceIntentParser,
    private val dispatcher: VoiceCommandDispatcher,
    private val commandCatalog: VoiceCommandCatalog,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _events = MutableSharedFlow<OfflineVoiceEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<OfflineVoiceEvent> = _events
    @Volatile private var ownsCommandCapture = false
    @Volatile private var ownsIatCapture = false

    init {
        scope.launch {
            commandWordEngine.debugResults.collectLatest { result ->
                _events.emit(OfflineVoiceEvent.CommandDebug(result))
            }
        }
        scope.launch {
            commandWordEngine.results.collectLatest { result ->
                if (!ownsCommandCapture) return@collectLatest
                val intent = intentParser.parse(result.rawText, result.intentCode) ?: return@collectLatest
                _events.emit(OfflineVoiceEvent.IntentResolved(intent))
                val handled = dispatcher.dispatch(intent)
                if (!handled) {
                    _events.emit(OfflineVoiceEvent.IntentUnhandled(intent))
                }
            }
        }
        scope.launch {
            iatEngine.results.collectLatest { result ->
                if (!ownsIatCapture) return@collectLatest
                _events.emit(OfflineVoiceEvent.Transcript(result))
                if (!result.isFinal || result.text.isBlank() || result.isNoSpeechResult()) return@collectLatest
                val intent = intentParser.parse(result.text) ?: return@collectLatest
                _events.emit(OfflineVoiceEvent.IntentResolved(intent))
                val handled = dispatcher.dispatch(intent)
                if (!handled) {
                    _events.emit(OfflineVoiceEvent.IntentUnhandled(intent))
                }
            }
        }
    }

    suspend fun initializeDefaultCommands() {
        ownsCommandCapture = true
        commandWordEngine.updateGrammar(commandCatalog.dialogCommands())
        commandWordEngine.startListening()
    }

    suspend fun beginFreeSpeechCapture() {
        ownsIatCapture = true
        iatEngine.startListening()
    }

    suspend fun stopFreeSpeechCapture() {
        ownsIatCapture = false
        iatEngine.stopListening()
    }

    suspend fun stopCommandCapture() {
        ownsCommandCapture = false
        commandWordEngine.stopListening()
    }

    suspend fun speak(text: String, interruptCurrent: Boolean = true) {
        ttsEngine.speak(text, interruptCurrent)
    }

    fun registerHandler(intentCode: String, handler: suspend (VoiceIntent) -> Unit) {
        dispatcher.register(intentCode) { intent -> handler(intent) }
    }

    private fun IatRecognitionResult.isNoSpeechResult(): Boolean {
        return text.startsWith("MSC错误:") ||
            text.contains("没有说话") ||
            text.contains("没听到")
    }
}
