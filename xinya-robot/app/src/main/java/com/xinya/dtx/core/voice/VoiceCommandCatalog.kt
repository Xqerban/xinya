package com.xinya.dtx.core.voice

import com.xinya.dtx.core.speech.CommandPhrase
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central command catalogue for offline recognition.
 *
 * Business modules consume intent codes and decide what action should be executed.
 */
@Singleton
class VoiceCommandCatalog @Inject constructor(
    private val config: VoiceInteractionConfig,
) {
    fun standbyCommands(): List<CommandPhrase> = buildList {
        config.allWakeWords().forEach { add(CommandPhrase(INTENT_WAKEUP, it, PRIORITY_WAKEUP)) }
        config.emergencyKeywords.forEach { add(CommandPhrase(INTENT_EMERGENCY_HELP, it, PRIORITY_EMERGENCY)) }
        config.clinicianCommands.forEach { (phrase, intentCode) ->
            add(CommandPhrase(intentCode, phrase, PRIORITY_CLINICIAN))
        }
    }

    fun dialogCommands(): List<CommandPhrase> = buildList {
        addAll(standbyCommands())
        addAll(DEFAULT_DIALOG_COMMANDS)
    }

    fun findIntentCode(text: String): String? {
        return dialogCommands()
            .sortedByDescending(CommandPhrase::priority)
            .firstOrNull { text.contains(it.phrase) }
            ?.intentCode
    }

    fun isClinicianIntent(intentCode: String): Boolean =
        config.clinicianCommands.values.any { it == intentCode }

    companion object {
        const val INTENT_WAKEUP = "VOICE_WAKEUP"
        const val INTENT_EMERGENCY_HELP = "EMERGENCY_HELP"

        private const val PRIORITY_EMERGENCY = 1000
        private const val PRIORITY_CLINICIAN = 900
        private const val PRIORITY_WAKEUP = 800

        val DEFAULT_DIALOG_COMMANDS = listOf(
            CommandPhrase("SYSTEM_INTERRUPT", "停止", 700),
            CommandPhrase("SYSTEM_INTERRUPT", "闭嘴", 700),
            CommandPhrase("SYSTEM_INTERRUPT", "停下", 700),
            CommandPhrase("EDUCATION_STOP", "停止宣教", 650),
            CommandPhrase("EDUCATION_STOP", "结束宣教", 650),
            CommandPhrase("APP_CONFIRM", "确认提交", 600),
            CommandPhrase("APP_CONFIRM", "确认", 580),
            CommandPhrase("EDUCATION_START", "开始宣教", 550),
            CommandPhrase("EDUCATION_START", "播放宣教", 550),
            CommandPhrase("LIGHT_OPEN", "打开灯光", 500),
            CommandPhrase("LIGHT_OPEN", "开灯", 500),
            CommandPhrase("LIGHT_CLOSE", "关闭灯光", 500),
            CommandPhrase("LIGHT_CLOSE", "关灯", 500),
            CommandPhrase("VOLUME_UP", "调高声音", 500),
            CommandPhrase("VOLUME_UP", "声音大一点", 500),
            CommandPhrase("VOLUME_DOWN", "调低声音", 500),
            CommandPhrase("VOLUME_DOWN", "声音小一点", 500),
            CommandPhrase("ROBOT_GO_BEDSIDE", "到床边", 500),
            CommandPhrase("ROBOT_MOVE_CLOSER", "再近一点", 500),
            CommandPhrase("ROBOT_MOVE_BACK", "后退一点", 500),
            CommandPhrase("MUSIC_OPEN", "打开音乐", 500),
            CommandPhrase("MUSIC_OPEN", "播放音乐", 500),
            CommandPhrase("SERVICE_WATER", "我要喝水", 500),
            CommandPhrase("SERVICE_CALL_NURSE", "帮我呼叫护士", 750),
            CommandPhrase("SERVICE_TURN_OVER", "我想翻身", 500),
            CommandPhrase("CARE_ORAL", "口腔护理", 500),
            CommandPhrase("CARE_PERIANAL", "肛周护理", 500),
            CommandPhrase("PRO_ORAL", "口腔PRO", 500),
            CommandPhrase("AGENT_PSYCHOLOGY", "我心情不好", 500),
            CommandPhrase("QUERY_BLOOD", "血像", 450),
            CommandPhrase("QUERY_TEMPERATURE", "今天体温", 450),
            CommandPhrase("QUERY_MEDICATION_TIME", "下次用药时间", 450),
            CommandPhrase("PRO_TEMPERATURE", "体温", 400),
            CommandPhrase("PRO_WEIGHT", "体重", 400),
            CommandPhrase("PRO_PAIN", "疼痛", 400),
            CommandPhrase("PRO_WATER", "饮水", 400),
        )
    }
}
