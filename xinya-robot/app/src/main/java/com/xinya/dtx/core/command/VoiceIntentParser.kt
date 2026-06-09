package com.xinya.dtx.core.command

import com.xinya.dtx.core.voice.VoiceIntent
import com.xinya.dtx.core.voice.VoiceInteractionConfig
import com.xinya.dtx.core.voice.VoiceCommandCatalog
import com.xinya.dtx.core.voice.VoicePriority
import com.xinya.dtx.core.voice.VoiceSpeakerRole
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoiceIntentParser @Inject constructor(
    private val config: VoiceInteractionConfig,
    private val catalog: VoiceCommandCatalog,
) {
    fun parse(rawText: String, recognizedIntentCode: String? = null): VoiceIntent? {
        val text = rawText.trim()
        if (text.isBlank()) return null

        if (config.emergencyKeywords.any { text.contains(it) }) {
            return VoiceIntent(
                code = "EMERGENCY_HELP",
                utterance = text,
                priority = VoicePriority.EMERGENCY,
                speakerRole = VoiceSpeakerRole.PATIENT,
                requiresWakeup = false,
                shouldInterruptCurrentTask = true,
                replyText = "已为您触发紧急求助。",
            )
        }

        val code = recognizedIntentCode ?: catalog.findIntentCode(text) ?: "FREE_TEXT"
        val isClinician = catalog.isClinicianIntent(code)

        return VoiceIntent(
            code = code,
            utterance = text,
            priority = when (code) {
                "SYSTEM_INTERRUPT" -> VoicePriority.MEDICAL
                else -> when {
                    isClinician -> VoicePriority.MEDICAL
                    code.startsWith("PRO_") || code.startsWith("CARE_") -> VoicePriority.MEDICAL
                    code.startsWith("LIGHT_") ||
                        code.startsWith("VOLUME_") ||
                        code.startsWith("ROBOT_") ||
                        code.startsWith("MUSIC_") -> VoicePriority.ENVIRONMENT
                    else -> VoicePriority.QUERY
                }
            },
            speakerRole = if (isClinician) VoiceSpeakerRole.CLINICIAN else VoiceSpeakerRole.PATIENT,
            requiresWakeup = !isClinician,
            shouldInterruptCurrentTask = code == "SYSTEM_INTERRUPT",
        )
    }

    fun parseAll(rawText: String): List<VoiceIntent> {
        return rawText
            .split(Regex("[，。！？；,!?;]+"))
            .mapNotNull(::parse)
            .ifEmpty { listOfNotNull(parse(rawText)) }
    }
}
