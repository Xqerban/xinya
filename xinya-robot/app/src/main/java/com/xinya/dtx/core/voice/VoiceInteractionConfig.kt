package com.xinya.dtx.core.voice

import java.time.LocalTime

enum class NightFeedbackMode {
    SILENT,
    LOW_VOLUME,
}

data class VoiceInteractionConfig(
    val primaryWakeWord: String = "小护小护",
    val secondaryWakeWords: List<String> = listOf("小芽小芽", "机器人"),
    val emergencyKeywords: List<String> = listOf(
        "护士",
        "救命",
        "求助",
        "疼",
        "好痛",
        "难受",
        "不舒服",
        "快来",
    ),
    val clinicianCommands: Map<String, String> = linkedMapOf(
        "设备巡检" to "CLINICIAN_DEVICE_INSPECTION",
        "开启对讲" to "CLINICIAN_INTERCOM_OPEN",
        "关闭报警" to "CLINICIAN_ALARM_CLOSE",
        "暂停服务" to "CLINICIAN_SERVICE_PAUSE",
        "重启语音" to "CLINICIAN_VOICE_RESTART",
    ),
    val continuousDialogSeconds: Int = 60,
    val maxConsecutiveRecognitionFailures: Int = 2,
    val weakVoiceVolumeThreshold: Int = 4,
    val wakeupToneEnabled: Boolean = true,
    val wakeupToneVolume: Int = 35,
    val dailyTtsVolume: Int = 50,
    val nightTtsVolume: Int = 25,
    val emergencyTtsVolume: Int = 70,
    val nightFeedbackMode: NightFeedbackMode = NightFeedbackMode.SILENT,
    val nightModeStart: LocalTime = LocalTime.of(22, 0),
    val nightModeEnd: LocalTime = LocalTime.of(6, 0),
    val nightAllowedIntentCodes: Set<String> = setOf(
        "EMERGENCY_HELP",
        "SYSTEM_INTERRUPT",
        "SERVICE_CALL_NURSE",
        "CARE_ORAL",
        "CARE_PERIANAL",
        "PRO_ORAL",
        "CLINICIAN_DEVICE_INSPECTION",
        "CLINICIAN_INTERCOM_OPEN",
        "CLINICIAN_ALARM_CLOSE",
        "CLINICIAN_SERVICE_PAUSE",
        "CLINICIAN_VOICE_RESTART",
    ),
) {
    fun allWakeWords(): List<String> = listOf(primaryWakeWord) + secondaryWakeWords

    val clinicianKeywords: List<String>
        get() = clinicianCommands.keys.toList()
}
