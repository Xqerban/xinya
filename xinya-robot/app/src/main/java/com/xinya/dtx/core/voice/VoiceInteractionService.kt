package com.xinya.dtx.core.voice

import com.xinya.dtx.core.command.VoiceIntentParser
import com.xinya.dtx.core.speech.CommandPhrase
import com.xinya.dtx.core.speech.CommandWordEngine
import com.xinya.dtx.core.speech.OfflineIatEngine
import com.xinya.dtx.core.speech.OfflineTtsEngine
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface VoiceInteractionEvent {
    data class WakeupDetected(
        val keyword: String,
        val isPrimary: Boolean,
        val occurredAtMillis: Long,
    ) : VoiceInteractionEvent

    data class EmergencyDetected(
        val intent: VoiceIntent,
        val occurredAtMillis: Long,
    ) : VoiceInteractionEvent

    data class IntentDetected(val intent: VoiceIntent) : VoiceInteractionEvent

    data class IntentRejectedByNightMode(val intent: VoiceIntent) : VoiceInteractionEvent

    data class UnhandledUtterance(val text: String) : VoiceInteractionEvent

    data class MultipleIntentsDetected(
        val selected: VoiceIntent,
        val ignored: List<VoiceIntent>,
    ) : VoiceInteractionEvent

    data class WeakVoiceDetected(
        val maxVolume: Int,
        val consecutiveFailures: Int,
    ) : VoiceInteractionEvent

    data class TouchSelectionSuggested(val consecutiveFailures: Int) : VoiceInteractionEvent

    data class RecognitionFailure(
        val consecutiveFailures: Int,
        val reason: String,
    ) : VoiceInteractionEvent

    data object DialogTimedOut : VoiceInteractionEvent

    data class Error(val operation: String, val cause: Throwable) : VoiceInteractionEvent
}

/**
 * Product-facing offline voice orchestrator.
 *
 * Standby uses AIKit command recognition for wake words, emergency terms, and clinician commands.
 * After wakeup it switches to MSC offline IAT for a continuous dialog window. Business actions are
 * emitted as events and deliberately remain outside the speech module.
 */
@Singleton
class VoiceInteractionService @Inject constructor(
    private val commandWordEngine: CommandWordEngine,
    private val iatEngine: OfflineIatEngine,
    private val ttsEngine: OfflineTtsEngine,
    private val intentParser: VoiceIntentParser,
    private val catalog: VoiceCommandCatalog,
    private val config: VoiceInteractionConfig,
    private val sessionManager: VoiceSessionManager,
    private val clock: Clock,
    private val promptPlayer: VoicePromptPlayer,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val transitionMutex = Mutex()
    private val _events = MutableSharedFlow<VoiceInteractionEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<VoiceInteractionEvent> = _events
    val state: StateFlow<VoiceSessionSnapshot> = sessionManager.snapshot
    private val _nightFeedbackMode = MutableStateFlow(config.nightFeedbackMode)
    val nightFeedbackMode: StateFlow<NightFeedbackMode> = _nightFeedbackMode.asStateFlow()

    private var mode = Mode.STOPPED
    private var standbyLoopJob: Job? = null
    private var dialogTimeoutJob: Job? = null
    private var dialogCommands: List<CommandPhrase> = catalog.dialogCommands()
    private var consecutiveRecognitionFailures = 0
    private var currentListeningMaxVolume = 0

    init {
        scope.launch {
            commandWordEngine.results.collectLatest { result ->
                handleRecognizedText(result.rawText, result.intentCode, fromStandby = mode == Mode.STANDBY)
            }
        }
        scope.launch {
            iatEngine.volumes.collectLatest { volume ->
                if (mode == Mode.DIALOG) {
                    currentListeningMaxVolume = maxOf(currentListeningMaxVolume, volume)
                }
            }
        }
        scope.launch {
            iatEngine.results.collectLatest { result ->
                if (mode != Mode.DIALOG || !result.isFinal || result.text.isBlank()) return@collectLatest
                if (result.isNoSpeechResult()) {
                    handleRecognitionFailure(result.text)
                    return@collectLatest
                }
                consecutiveRecognitionFailures = 0
                handleDialogTranscript(result.text)
            }
        }
    }

    suspend fun start() {
        transitionMutex.withLock {
            enterStandbyLocked()
        }
    }

    suspend fun stop() {
        transitionMutex.withLock {
            mode = Mode.STOPPED
            stopTimersLocked()
            stopMicrophoneEnginesLocked()
            ttsEngine.stop()
            sessionManager.resetToStandby()
        }
    }

    /**
     * Replaces the normal dialog command catalogue. Standby emergency/wakeup/clinician terms remain
     * fixed and are always added by the service.
     */
    suspend fun updateDialogCommands(commands: List<CommandPhrase>) {
        transitionMutex.withLock {
            dialogCommands = (catalog.standbyCommands() + commands)
                .filter { it.phrase.isNotBlank() }
                .distinctBy { it.phrase }
            if (mode == Mode.DIALOG) {
                sessionManager.refreshTimeout()
                scheduleDialogTimeoutLocked()
            }
        }
    }

    suspend fun speak(
        text: String,
        interruptCurrent: Boolean = true,
        feedbackLevel: VoiceFeedbackLevel = VoiceFeedbackLevel.DAILY,
    ) {
        val volume = feedbackVolume(feedbackLevel) ?: return
        ttsEngine.speak(text, interruptCurrent, volume)
    }

    fun setNightFeedbackMode(mode: NightFeedbackMode) {
        _nightFeedbackMode.value = mode
    }

    private suspend fun handleRecognizedText(
        rawText: String,
        recognizedIntentCode: String,
        fromStandby: Boolean,
    ) {
        val parsed = intentParser.parse(rawText, recognizedIntentCode) ?: return
        when {
            parsed.priority == VoicePriority.EMERGENCY -> handleEmergency(parsed)
            parsed.speakerRole == VoiceSpeakerRole.CLINICIAN -> handleClinicianIntent(parsed)
            fromStandby && parsed.code == VoiceCommandCatalog.INTENT_WAKEUP -> handleWakeup(rawText)
            !fromStandby -> handleDialogIntent(parsed)
        }
    }

    private suspend fun handleDialogTranscript(text: String) {
        val intents = text
            .split(Regex("[，。！？；,!?;]+"))
            .flatMap { phrase ->
                val matches = dialogCommands
                    .sortedByDescending(CommandPhrase::priority)
                    .filter { phrase.contains(it.phrase) }
                    .distinctBy(CommandPhrase::intentCode)
                if (matches.isEmpty()) {
                    listOfNotNull(intentParser.parse(phrase))
                } else {
                    matches.mapNotNull { intentParser.parse(phrase, it.intentCode) }
                }
            }
            .distinctBy(VoiceIntent::code)
            .ifEmpty { listOfNotNull(intentParser.parse(text)) }
        if (intents.isEmpty()) return
        val sorted = intents.sortedBy(::priorityOrder)
        val selected = sorted.first()
        if (sorted.size > 1 && selected.priority != VoicePriority.EMERGENCY) {
            _events.emit(VoiceInteractionEvent.MultipleIntentsDetected(selected, sorted.drop(1)))
            speakDuringDialog("请一个一个说，我先帮您处理第一件事。", restartAfter = false)
        }
        when {
            selected.priority == VoicePriority.EMERGENCY -> handleEmergency(selected)
            selected.speakerRole == VoiceSpeakerRole.CLINICIAN -> handleClinicianIntent(selected)
            selected.code == "FREE_TEXT" -> handleUnhandledUtterance(selected.utterance)
            else -> handleDialogIntent(selected)
        }
        transitionMutex.withLock {
            if (mode == Mode.DIALOG) {
                restartIatLocked()
            }
        }
    }

    private suspend fun handleWakeup(rawText: String) {
        val keyword = config.allWakeWords().firstOrNull { rawText.contains(it) } ?: config.primaryWakeWord
        transitionMutex.withLock {
            if (mode != Mode.STANDBY) return
            stopMicrophoneEnginesLocked()
            sessionManager.onWakeup(keyword)
            mode = Mode.DIALOG
            _events.emit(
                VoiceInteractionEvent.WakeupDetected(
                    keyword = keyword,
                    isPrimary = keyword == config.primaryWakeWord,
                    occurredAtMillis = clock.millis(),
                )
            )
            if (config.wakeupToneEnabled && feedbackVolume(VoiceFeedbackLevel.DAILY) != null) {
                val toneVolume = if (sessionManager.isNightMode()) {
                    minOf(config.wakeupToneVolume, config.nightTtsVolume)
                } else {
                    config.wakeupToneVolume
                }
                runCatching { promptPlayer.playWakeupTone(toneVolume) }
                    .onFailure { _events.emit(VoiceInteractionEvent.Error("wakeup-tone", it)) }
            }
            feedbackVolume(VoiceFeedbackLevel.DAILY)?.let { volume ->
                runCatching { ttsEngine.speak("我在", interruptCurrent = true, volume = volume) }
                    .onFailure { _events.emit(VoiceInteractionEvent.Error("wakeup-feedback", it)) }
            }
            consecutiveRecognitionFailures = 0
            restartIatLocked()
            scheduleDialogTimeoutLocked()
        }
    }

    private suspend fun handleEmergency(intent: VoiceIntent) {
        transitionMutex.withLock {
            stopMicrophoneEnginesLocked()
            runCatching { ttsEngine.stop() }
            sessionManager.onEmergencyTriggered(intent)
            _events.emit(VoiceInteractionEvent.EmergencyDetected(intent, clock.millis()))
            enterStandbyLocked()
        }
    }

    private suspend fun handleClinicianIntent(intent: VoiceIntent) {
        transitionMutex.withLock {
            runCatching { ttsEngine.stop() }
            sessionManager.onIntentHandled(intent)
            _events.emit(VoiceInteractionEvent.IntentDetected(intent))
            if (mode == Mode.STANDBY) {
                restartStandbyRecognitionLocked()
            } else {
                sessionManager.refreshTimeout()
                scheduleDialogTimeoutLocked()
            }
        }
    }

    private suspend fun handleDialogIntent(intent: VoiceIntent) {
        transitionMutex.withLock {
            if (mode != Mode.DIALOG) return
            if (sessionManager.isNightMode() && intent.code !in config.nightAllowedIntentCodes) {
                _events.emit(VoiceInteractionEvent.IntentRejectedByNightMode(intent))
                restartIatLocked()
                return
            }
            if (intent.shouldInterruptCurrentTask) {
                runCatching { ttsEngine.stop() }
            }
            sessionManager.onIntentHandled(intent)
            _events.emit(VoiceInteractionEvent.IntentDetected(intent))
            sessionManager.refreshTimeout()
            scheduleDialogTimeoutLocked()
        }
    }

    private suspend fun handleUnhandledUtterance(text: String) {
        transitionMutex.withLock {
            if (mode != Mode.DIALOG) return
            _events.emit(VoiceInteractionEvent.UnhandledUtterance(text))
            sessionManager.refreshTimeout()
            scheduleDialogTimeoutLocked()
        }
        speakDuringDialog("这个我暂时无法帮到你", restartAfter = false)
    }

    private suspend fun handleRecognitionFailure(reason: String) {
        consecutiveRecognitionFailures += 1
        val maxVolume = currentListeningMaxVolume
        currentListeningMaxVolume = 0
        _events.emit(
            VoiceInteractionEvent.RecognitionFailure(
                consecutiveFailures = consecutiveRecognitionFailures,
                reason = reason,
            )
        )
        if (maxVolume in 1..config.weakVoiceVolumeThreshold) {
            _events.emit(
                VoiceInteractionEvent.WeakVoiceDetected(
                    maxVolume = maxVolume,
                    consecutiveFailures = consecutiveRecognitionFailures,
                )
            )
        }
        if (consecutiveRecognitionFailures >= config.maxConsecutiveRecognitionFailures) {
            _events.emit(VoiceInteractionEvent.TouchSelectionSuggested(consecutiveRecognitionFailures))
            speakDuringDialog("您可以用手指轻触屏幕选择功能。")
        } else {
            speakDuringDialog("我没听清，可以再说一次吗")
        }
    }

    private suspend fun speakDuringDialog(text: String, restartAfter: Boolean = true) {
        transitionMutex.withLock {
            if (mode != Mode.DIALOG) return
            runCatching { iatEngine.stopListening() }
            val volume = feedbackVolume(VoiceFeedbackLevel.DAILY)
            if (volume != null) {
                runCatching { ttsEngine.speak(text, interruptCurrent = true, volume = volume) }
                    .onFailure { _events.emit(VoiceInteractionEvent.Error("dialog-feedback", it)) }
            }
            if (restartAfter && mode == Mode.DIALOG) restartIatLocked()
        }
    }

    private suspend fun enterStandbyLocked() {
        stopTimersLocked()
        stopMicrophoneEnginesLocked()
        mode = Mode.STANDBY
        sessionManager.resetToStandby()
        commandWordEngine.updateGrammar(catalog.standbyCommands())
        commandWordEngine.startListening()
        standbyLoopJob = scope.launch {
            while (mode == Mode.STANDBY) {
                delay(STANDBY_RESTART_INTERVAL_MS)
                transitionMutex.withLock {
                    if (mode == Mode.STANDBY) restartStandbyRecognitionLocked()
                }
            }
        }
    }

    private suspend fun restartStandbyRecognitionLocked() {
        commandWordEngine.stopListening()
        commandWordEngine.updateGrammar(catalog.standbyCommands())
        commandWordEngine.startListening()
    }

    private suspend fun restartIatLocked() {
        currentListeningMaxVolume = 0
        iatEngine.stopListening()
        iatEngine.startListening()
    }

    private fun scheduleDialogTimeoutLocked() {
        dialogTimeoutJob?.cancel()
        dialogTimeoutJob = scope.launch {
            while (mode == Mode.DIALOG) {
                val deadline = sessionManager.snapshot.value.continuousDialogDeadlineMillis
                    ?: return@launch
                val remainingMillis = deadline - clock.millis()
                if (remainingMillis > 0) {
                    delay(remainingMillis)
                    continue
                }
                transitionMutex.withLock {
                    if (mode != Mode.DIALOG) return@withLock
                    if (sessionManager.isContinuousDialogActive()) return@withLock
                    _events.emit(VoiceInteractionEvent.DialogTimedOut)
                    enterStandbyLocked()
                }
                return@launch
            }
        }
    }

    private suspend fun stopMicrophoneEnginesLocked() {
        runCatching { commandWordEngine.stopListening() }
        runCatching { iatEngine.stopListening() }
    }

    private suspend fun stopTimersLocked() {
        val currentJob = kotlinx.coroutines.currentCoroutineContext()[Job]
        standbyLoopJob?.takeIf { it != currentJob }?.cancel()
        dialogTimeoutJob?.takeIf { it != currentJob }?.cancel()
        standbyLoopJob = null
        dialogTimeoutJob = null
    }

    private fun priorityOrder(intent: VoiceIntent): Int = when {
        intent.priority == VoicePriority.EMERGENCY -> 0
        intent.speakerRole == VoiceSpeakerRole.CLINICIAN -> 1
        intent.priority == VoicePriority.MEDICAL -> 2
        intent.priority == VoicePriority.ENVIRONMENT -> 3
        else -> 4
    }

    private fun feedbackVolume(level: VoiceFeedbackLevel): Int? {
        if (!sessionManager.isNightMode()) {
            return when (level) {
                VoiceFeedbackLevel.DAILY -> config.dailyTtsVolume
                VoiceFeedbackLevel.EMERGENCY -> config.emergencyTtsVolume
            }
        }
        return when (_nightFeedbackMode.value) {
            NightFeedbackMode.SILENT -> null
            NightFeedbackMode.LOW_VOLUME -> config.nightTtsVolume
        }
    }

    private fun com.xinya.dtx.core.speech.IatRecognitionResult.isNoSpeechResult(): Boolean {
        return text.startsWith("MSC错误:") ||
            text.contains("没有说话") ||
            text.contains("没听到")
    }

    private enum class Mode {
        STOPPED,
        STANDBY,
        DIALOG,
    }

    private companion object {
        const val STANDBY_RESTART_INTERVAL_MS = 8_500L
    }
}

enum class VoiceFeedbackLevel {
    DAILY,
    EMERGENCY,
}
