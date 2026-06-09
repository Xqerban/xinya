package com.xinya.dtx.feature.home.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xinya.dtx.core.speech.OfflineTtsEngine
import com.xinya.dtx.core.speech.WakeupEngine
import com.xinya.dtx.core.voice.OfflineVoiceEvent
import com.xinya.dtx.core.voice.OfflineVoiceFacade
import com.xinya.dtx.core.voice.VoiceInteractionEvent
import com.xinya.dtx.core.voice.VoiceInteractionService
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

enum class VoiceDebugAiKitAction {
    TestTts,
    TestWakeupReply,
    StartWakeup,
    StartCommands,
    StartFullVoice,
}

data class VoiceDebugUiState(
    val status: String = "待命",
    val logs: List<String> = emptyList(),
)

@HiltViewModel
class VoiceDebugViewModel @Inject constructor(
    private val offlineVoiceFacade: OfflineVoiceFacade,
    private val wakeupEngine: WakeupEngine,
    private val ttsEngine: OfflineTtsEngine,
    private val voiceInteractionService: VoiceInteractionService,
) : ViewModel() {
    private val _uiState = MutableStateFlow(VoiceDebugUiState())
    val uiState: StateFlow<VoiceDebugUiState> = _uiState.asStateFlow()

    private var pendingAiKitAction: VoiceDebugAiKitAction? = null

    init {
        viewModelScope.launch {
            offlineVoiceFacade.events.collectLatest { event ->
                when (event) {
                    is OfflineVoiceEvent.Transcript -> appendLog("听写: ${event.result.text}")
                    is OfflineVoiceEvent.CommandDebug ->
                        appendLog("命令词: ${event.result.key} <- ${event.result.rawText}")
                    is OfflineVoiceEvent.IntentResolved ->
                        appendLog("意图: ${event.intent.code} <- ${event.intent.utterance}")
                    is OfflineVoiceEvent.IntentUnhandled ->
                        appendLog("未处理意图: ${event.intent.code}")
                }
            }
        }
        viewModelScope.launch {
            wakeupEngine.events.collectLatest { event ->
                appendLog("唤醒: ${event.keyword}")
            }
        }
        viewModelScope.launch {
            voiceInteractionService.state.collectLatest { snapshot ->
                appendLog("完整语音状态: ${snapshot.state}, 夜间=${snapshot.isNightMode}")
            }
        }
        viewModelScope.launch {
            voiceInteractionService.events.collectLatest { event ->
                when (event) {
                    is VoiceInteractionEvent.WakeupDetected -> {
                        updateStatus("完整语音：连续对话中")
                        appendLog("完整语音唤醒: ${event.keyword}, 主唤醒=${event.isPrimary}")
                    }
                    is VoiceInteractionEvent.EmergencyDetected -> {
                        updateStatus("完整语音：紧急事件")
                        appendLog("完整语音紧急: ${event.intent.code} <- ${event.intent.utterance}")
                    }
                    is VoiceInteractionEvent.IntentDetected ->
                        appendLog(
                            "完整语音意图: ${event.intent.code} <- ${event.intent.utterance}, " +
                                "角色=${event.intent.speakerRole}, 优先级=${event.intent.priority}"
                        )
                    is VoiceInteractionEvent.IntentRejectedByNightMode ->
                        appendLog("完整语音夜间拒绝: ${event.intent.code} <- ${event.intent.utterance}")
                    is VoiceInteractionEvent.UnhandledUtterance ->
                        appendLog("完整语音无法处理: ${event.text}")
                    is VoiceInteractionEvent.MultipleIntentsDetected ->
                        appendLog(
                            "完整语音多指令: 执行=${event.selected.code}, " +
                                "忽略=${event.ignored.joinToString { it.code }}"
                        )
                    is VoiceInteractionEvent.WeakVoiceDetected ->
                        appendLog(
                            "完整语音弱声: 最大音量=${event.maxVolume}, " +
                                "连续失败=${event.consecutiveFailures}"
                        )
                    is VoiceInteractionEvent.TouchSelectionSuggested ->
                        appendLog("完整语音建议触屏: 连续失败=${event.consecutiveFailures}")
                    is VoiceInteractionEvent.RecognitionFailure ->
                        appendLog(
                            "完整语音拒识: 连续失败=${event.consecutiveFailures}, " +
                                "原因=${event.reason}"
                        )
                    VoiceInteractionEvent.DialogTimedOut -> {
                        updateStatus("完整语音：待机监听中")
                        appendLog("完整语音超时: 已自动返回待机")
                    }
                    is VoiceInteractionEvent.Error ->
                        appendLog("完整语音错误[${event.operation}]: ${event.cause.message}")
                }
            }
        }
    }

    fun queueAiKitAction(action: VoiceDebugAiKitAction) {
        pendingAiKitAction = action
    }

    fun runPendingAiKitAction() {
        when (pendingAiKitAction) {
            VoiceDebugAiKitAction.TestTts -> testTts()
            VoiceDebugAiKitAction.TestWakeupReply -> testWakeupReply()
            VoiceDebugAiKitAction.StartWakeup -> startWakeup()
            VoiceDebugAiKitAction.StartCommands -> startCommands()
            VoiceDebugAiKitAction.StartFullVoice -> startFullVoice()
            null -> Unit
        }
        pendingAiKitAction = null
    }

    fun onAiKitPermissionDenied() {
        pendingAiKitAction = null
        updateStatus("权限不足")
        appendLog("错误: AIKit 调试需要麦克风和设备授权相关权限")
    }

    fun onMicPermissionDenied() {
        updateStatus("权限不足")
        appendLog("错误: 听写需要麦克风权限")
    }

    fun startWakeup() = launchAction("启动唤醒中") {
        wakeupEngine.start()
        updateStatus("AIKit 唤醒监听中")
        appendLog("已启动 AIKit 唤醒监听")
    }

    fun startCommands() = launchAction("启动命令词测试中") {
        offlineVoiceFacade.initializeDefaultCommands()
        updateStatus("AIKit 命令词识别中")
        appendLog("已启动 AIKit 命令词测试")
    }

    fun startIat() = launchAction("启动听写中") {
        offlineVoiceFacade.beginFreeSpeechCapture()
        updateStatus("MSC 听写中")
        appendLog("已启动 MSC 离线听写")
    }

    fun startFullVoice() = launchAction("启动完整语音中") {
        stopSingleEngineTests()
        voiceInteractionService.start()
        updateStatus("完整语音：待机监听中")
        appendLog("已启动完整语音服务，请直接说唤醒词、紧急词或医护指令")
    }

    fun testTts() = launchAction("TTS 测试中") {
        offlineVoiceFacade.speak("离线语音测试开始，请说开始宣教或确认提交。")
        updateStatus("TTS 完成")
        appendLog("已完成离线 TTS 播报")
    }

    fun testWakeupReply() = launchAction("测试“我在”中") {
        offlineVoiceFacade.speak("我在")
        updateStatus("“我在”测试完成")
        appendLog("已完成唤醒回复“我在”播报")
    }

    fun stopAll() = launchAction("停止中") {
        voiceInteractionService.stop()
        offlineVoiceFacade.stopFreeSpeechCapture()
        offlineVoiceFacade.stopCommandCapture()
        wakeupEngine.stop()
        ttsEngine.stop()
        updateStatus("已停止")
        appendLog("已停止所有离线语音调试任务")
    }

    private suspend fun stopSingleEngineTests() {
        offlineVoiceFacade.stopFreeSpeechCapture()
        offlineVoiceFacade.stopCommandCapture()
        wakeupEngine.stop()
        ttsEngine.stop()
    }

    fun clearLogs() {
        _uiState.value = _uiState.value.copy(logs = emptyList())
    }

    private fun launchAction(status: String, action: suspend () -> Unit) {
        viewModelScope.launch {
            updateStatus(status)
            runCatching { action() }
                .onFailure { error ->
                    updateStatus("失败")
                    appendLog("错误: ${error.message ?: error.javaClass.simpleName}")
                }
        }
    }

    private fun updateStatus(status: String) {
        _uiState.value = _uiState.value.copy(status = status)
    }

    private fun appendLog(message: String) {
        val nextLogs = (_uiState.value.logs + message).takeLast(12)
        _uiState.value = _uiState.value.copy(logs = nextLogs)
    }
}
