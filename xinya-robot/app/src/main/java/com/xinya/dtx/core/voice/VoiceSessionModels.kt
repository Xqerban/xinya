package com.xinya.dtx.core.voice

enum class VoiceSessionState {
    STANDBY,
    AWAKE,
    CONTINUOUS_DIALOG,
    EMERGENCY,
    SILENT,
}

enum class VoicePriority {
    EMERGENCY,
    MEDICAL,
    ENVIRONMENT,
    QUERY,
}

enum class VoiceSpeakerRole {
    PATIENT,
    CLINICIAN,
    SYSTEM,
}

data class VoiceIntent(
    val code: String,
    val utterance: String,
    val priority: VoicePriority,
    val speakerRole: VoiceSpeakerRole,
    val requiresWakeup: Boolean = true,
    val shouldInterruptCurrentTask: Boolean = false,
    val replyText: String? = null,
)

data class VoiceSessionSnapshot(
    val state: VoiceSessionState = VoiceSessionState.STANDBY,
    val wakeWord: String? = null,
    val continuousDialogDeadlineMillis: Long? = null,
    val isNightMode: Boolean = false,
    val lastIntent: VoiceIntent? = null,
)
