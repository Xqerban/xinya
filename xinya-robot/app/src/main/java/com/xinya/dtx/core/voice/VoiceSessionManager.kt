package com.xinya.dtx.core.voice

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Clock
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoiceSessionManager @Inject constructor(
    private val config: VoiceInteractionConfig,
    private val clock: Clock,
) {
    private val _snapshot = MutableStateFlow(
        VoiceSessionSnapshot(
            isNightMode = isNightMode(),
        )
    )
    val snapshot: StateFlow<VoiceSessionSnapshot> = _snapshot.asStateFlow()

    fun onWakeup(keyword: String) {
        _snapshot.value = _snapshot.value.copy(
            state = VoiceSessionState.CONTINUOUS_DIALOG,
            wakeWord = keyword,
            continuousDialogDeadlineMillis = nowMillis() + config.continuousDialogSeconds * 1000L,
            isNightMode = isNightMode(),
        )
    }

    fun onIntentHandled(intent: VoiceIntent) {
        val nextState = when {
            intent.code == INTERRUPT_CODE -> VoiceSessionState.SILENT
            intent.priority == VoicePriority.EMERGENCY -> VoiceSessionState.EMERGENCY
            else -> VoiceSessionState.CONTINUOUS_DIALOG
        }
        _snapshot.value = _snapshot.value.copy(
            state = nextState,
            lastIntent = intent,
            continuousDialogDeadlineMillis = nowMillis() + config.continuousDialogSeconds * 1000L,
            isNightMode = isNightMode(),
        )
    }

    fun onEmergencyTriggered(intent: VoiceIntent) {
        _snapshot.value = _snapshot.value.copy(
            state = VoiceSessionState.EMERGENCY,
            lastIntent = intent,
            isNightMode = isNightMode(),
        )
    }

    fun resetToStandby() {
        _snapshot.value = VoiceSessionSnapshot(
            state = VoiceSessionState.STANDBY,
            isNightMode = isNightMode(),
        )
    }

    fun refreshTimeout() {
        _snapshot.value = _snapshot.value.copy(
            continuousDialogDeadlineMillis = nowMillis() + config.continuousDialogSeconds * 1000L,
            isNightMode = isNightMode(),
        )
    }

    fun isContinuousDialogActive(): Boolean {
        val deadline = _snapshot.value.continuousDialogDeadlineMillis ?: return false
        return deadline > nowMillis()
    }

    fun isNightMode(): Boolean {
        val now = LocalTime.now(clock)
        return if (config.nightModeStart <= config.nightModeEnd) {
            now >= config.nightModeStart && now < config.nightModeEnd
        } else {
            now >= config.nightModeStart || now < config.nightModeEnd
        }
    }

    private fun nowMillis(): Long = clock.millis()

    companion object {
        const val INTERRUPT_CODE = "SYSTEM_INTERRUPT"
    }
}
