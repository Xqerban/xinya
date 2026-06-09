# 离线语音接口接入说明

## 1. 模块边界

本模块负责提供离线语音能力和识别结果，不负责决定识别成功后跳转哪个页面、控制哪个设备或调用哪个业务接口。

前端/业务模块负责：

- 监听唤醒、命令词和听写结果。
- 根据 `intentCode` 执行页面跳转、机器人控制或业务请求。
- 调用 TTS 播放业务反馈。
- 根据页面生命周期启动或停止对应语音能力。

当前已在真机/机器人验证可用的能力：

| 能力 | 接口 | 实现 | 状态 |
| --- | --- | --- | --- |
| 离线唤醒 | `WakeupEngine` | AIKit IVW | 可用 |
| 离线命令词 | `CommandWordEngine` | AIKit ESR | 可用 |
| 离线听写 | `OfflineIatEngine` | MSC 本地听写 | 可用 |
| 离线语音合成 | `OfflineTtsEngine` | AIKit XTTS | 可用 |

统一语音编排接口：

`app/src/main/java/com/xinya/dtx/core/voice/VoiceInteractionService.kt`

内置命令目录：

`app/src/main/java/com/xinya/dtx/core/voice/VoiceCommandCatalog.kt`

公共接口和结果模型位于：

`app/src/main/java/com/xinya/dtx/core/speech/OfflineSpeechEngines.kt`

Hilt 接口绑定位于：

`app/src/main/java/com/xinya/dtx/di/VoiceModule.kt`

## 2. 接入方式

项目使用 Hilt。ViewModel 或其他 Hilt 管理的类应依赖接口，不应直接依赖 `Aikit*` 或 `Msc*` 实现类。

```kotlin
@HiltViewModel
class ExampleViewModel @Inject constructor(
    private val wakeupEngine: WakeupEngine,
    private val commandWordEngine: CommandWordEngine,
    private val iatEngine: OfflineIatEngine,
    private val ttsEngine: OfflineTtsEngine,
) : ViewModel()
```

所有启动、停止、配置和播报方法均为 `suspend` 方法，应在协程中调用。识别结果通过 `Flow` 返回。

同一时刻不要同时启动多个需要麦克风的引擎。切换唤醒、命令词和听写前，应先停止当前麦克风引擎。

## 3. 推荐：统一语音编排接口

正式产品接入优先使用 `VoiceInteractionService`，不要由页面分别控制三个麦克风引擎。

它负责：

- 待机时持续识别主/副唤醒词、免唤醒紧急词和医护免唤醒指令。
- AIKit 命令词识别窗口结束后自动续接监听。
- 唤醒后回复“我在”，并切换到 MSC 离线听写。
- 保持 60 秒连续对话窗口，收到有效指令后刷新窗口。
- 连续对话超时后自动回到待机监听。
- 紧急词打断当前 TTS，并输出带触发时间的紧急事件。
- 多指令只输出最高优先级第一条，并提示患者逐条说。
- 连续拒识时播放重试/触屏提示，并输出弱声和触屏建议事件。
- 无法匹配业务意图时播放能力范围提示。
- 夜间拒绝非核心指令，并禁止普通 TTS。

### 接入示例

```kotlin
@HiltViewModel
class ProductVoiceViewModel @Inject constructor(
    private val voiceService: VoiceInteractionService,
) : ViewModel() {
    init {
        viewModelScope.launch {
            voiceService.events.collect { event ->
                when (event) {
                    is VoiceInteractionEvent.WakeupDetected -> showWakeupFeedback()
                    is VoiceInteractionEvent.EmergencyDetected -> pushEmergency(event)
                    is VoiceInteractionEvent.IntentDetected -> executeBusinessAction(event.intent)
                    is VoiceInteractionEvent.IntentRejectedByNightMode -> showNightModeHint()
                    is VoiceInteractionEvent.UnhandledUtterance -> showUnhandledHint()
                    is VoiceInteractionEvent.MultipleIntentsDetected -> showMultipleIntentHint()
                    is VoiceInteractionEvent.WeakVoiceDetected -> showWeakVoiceHint()
                    is VoiceInteractionEvent.TouchSelectionSuggested -> showTouchSelection()
                    is VoiceInteractionEvent.RecognitionFailure -> recordRecognitionFailure()
                    VoiceInteractionEvent.DialogTimedOut -> showStandby()
                    is VoiceInteractionEvent.Error -> reportVoiceError(event)
                }
            }
        }
    }

    fun startVoice() = viewModelScope.launch {
        voiceService.start()
    }

    fun stopVoice() = viewModelScope.launch {
        voiceService.stop()
    }
}
```

业务模块收到 `EmergencyDetected` 后，负责护士站推送、患者状态读取、报警落库等动作。语音模块只负责紧急词识别、打断当前 TTS 和输出标准事件。

### 动态替换连续对话命令

```kotlin
voiceService.updateDialogCommands(
    listOf(
        CommandPhrase("LIGHT_OPEN", "打开灯光", 500),
        CommandPhrase("LIGHT_OPEN", "开灯", 500),
    )
)
```

动态替换只影响唤醒后的普通对话命令。主/副唤醒词、紧急词和医护免唤醒指令始终保留。

## 4. 离线唤醒

### API

```kotlin
interface WakeupEngine {
    val events: Flow<WakeupEvent>
    suspend fun start()
    suspend fun stop()
}

data class WakeupEvent(
    val keyword: String,
    val isPrimary: Boolean,
)
```

### 调用示例

```kotlin
init {
    viewModelScope.launch {
        wakeupEngine.events.collect { event ->
            // 前端在这里展示唤醒状态、播放反馈或开始下一阶段识别。
            onWakeup(event.keyword, event.isPrimary)
        }
    }
}

fun startWakeup() = viewModelScope.launch {
    wakeupEngine.start()
}

fun stopWakeup() = viewModelScope.launch {
    wakeupEngine.stop()
}
```

### 修改唤醒词

唤醒词配置位于：

`app/src/main/java/com/xinya/dtx/core/voice/VoiceInteractionConfig.kt`

修改以下字段后重新编译安装：

```kotlin
val primaryWakeWord: String = "小护小护"
val secondaryWakeWords: List<String> = listOf("小芽小芽", "机器人")
```

AIKit 唤醒启动时会将 `allWakeWords()` 写入运行时关键词文件。

注意：新增唤醒词是否能够识别，还受甲方提供的 AIKit IVW 资源和授权能力限制，不能只以代码配置作为成功依据。

## 5. 离线命令词

### API

```kotlin
interface CommandWordEngine {
    suspend fun updateGrammar(commands: List<CommandPhrase>)
    suspend fun startListening()
    suspend fun stopListening()
    val results: Flow<CommandRecognitionResult>
    val debugResults: Flow<CommandDebugResult>
}

data class CommandPhrase(
    val intentCode: String,
    val phrase: String,
    val priority: Int = 0,
)
```

`intentCode` 是交给前端/业务层使用的稳定指令编号。`phrase` 是患者实际说出的短语。多个短语可以映射到同一个 `intentCode`。

### 配置并启动命令词

```kotlin
private val commands = listOf(
    CommandPhrase("LIGHT_OPEN", "打开灯光", priority = 80),
    CommandPhrase("LIGHT_OPEN", "开灯", priority = 80),
    CommandPhrase("EDUCATION_START", "开始宣教", priority = 90),
    CommandPhrase("SYSTEM_INTERRUPT", "停止", priority = 100),
    CommandPhrase("SYSTEM_INTERRUPT", "闭嘴", priority = 100),
)

fun startCommands() = viewModelScope.launch {
    commandWordEngine.updateGrammar(commands)
    commandWordEngine.startListening()
}
```

### 监听命令词结果

```kotlin
init {
    viewModelScope.launch {
        commandWordEngine.results.collect { result ->
            when (result.intentCode) {
                "LIGHT_OPEN" -> openLight()
                "EDUCATION_START" -> openEducation()
                "SYSTEM_INTERRUPT" -> stopCurrentTask()
            }
        }
    }
}
```

### 增删普通命令词

普通命令词不存放在 `VoiceInteractionConfig`。

正式接入时，由业务模块维护 `List<CommandPhrase>`，然后调用：

```kotlin
commandWordEngine.updateGrammar(newCommands)
```

更新规则：

- 新增：向列表增加 `CommandPhrase`。
- 删除：从列表删除对应项。
- 同义词：使用相同 `intentCode`，增加不同 `phrase`。
- 优先级：数值越高，在生成语法时越靠前；它不负责业务任务抢占。
- 更新后需重新调用 `startListening()` 开始本轮识别。

项目内置正式指令目录位于：

`app/src/main/java/com/xinya/dtx/core/voice/VoiceCommandCatalog.kt`。

当前已包含设备、服务、护理、查询、宣教、中断、紧急和医护指令。前端仍只负责根据 `intentCode` 执行业务动作。

`OfflineVoiceFacade.initializeDefaultCommands()` 也会加载这份完整目录，供调试面板单独测试命令词。

如果后续指令需要从服务端或本地 JSON 动态维护，前端只需转换为 `List<CommandPhrase>` 后调用 `updateGrammar()`。

## 6. MSC 离线听写

### API

```kotlin
interface OfflineIatEngine {
    suspend fun startListening()
    suspend fun stopListening()
    val results: Flow<IatRecognitionResult>
}

data class IatRecognitionResult(
    val text: String,
    val isFinal: Boolean,
    val rawPayload: String? = null,
)
```

### 调用示例

```kotlin
init {
    viewModelScope.launch {
        iatEngine.results.collect { result ->
            if (result.isFinal) {
                submitRecognizedText(result.text)
            } else {
                showPartialText(result.text)
            }
        }
    }
}

fun startIat() = viewModelScope.launch {
    iatEngine.startListening()
}

fun stopIat() = viewModelScope.launch {
    iatEngine.stopListening()
}
```

听写模块只返回文本，不负责判断文本对应哪个业务意图。

## 7. 离线 TTS

### API

```kotlin
interface OfflineTtsEngine {
    suspend fun speak(text: String, interruptCurrent: Boolean = false, volume: Int = 50)
    suspend fun stop()
}
```

### 调用示例

```kotlin
fun speakReply(text: String) = viewModelScope.launch {
    ttsEngine.speak(text, interruptCurrent = true, volume = 50)
}

fun stopSpeaking() = viewModelScope.launch {
    ttsEngine.stop()
}
```

`interruptCurrent = true` 表示先停止当前播报，再播放新文本。

`volume` 范围为 `0..100`。统一语音服务会根据日常、夜间和紧急场景自动选择配置音量。

## 8. 调试用组合入口

`OfflineVoiceFacade` 提供了部分组合调用和统一事件：

```kotlin
sealed interface OfflineVoiceEvent {
    data class Transcript(...)
    data class CommandDebug(...)
    data class IntentResolved(...)
    data class IntentUnhandled(...)
}
```

可调用方法：

```kotlin
suspend fun initializeDefaultCommands()
suspend fun beginFreeSpeechCapture()
suspend fun stopFreeSpeechCapture()
suspend fun stopCommandCapture()
suspend fun speak(text: String, interruptCurrent: Boolean = true)
fun registerHandler(intentCode: String, handler: suspend (VoiceIntent) -> Unit)
```

`OfflineVoiceFacade` 当前主要用于调试和基础意图分发。正式业务如果需要动态命令词，建议直接使用 `CommandWordEngine`；唤醒能力直接使用 `WakeupEngine`。

## 9. 配置与资源位置

### 交互词和时间配置

`app/src/main/java/com/xinya/dtx/core/voice/VoiceInteractionConfig.kt`

当前包含：

- 主、副唤醒词。
- 紧急关键词列表。
- 医护关键词列表。
- 连续对话秒数。
- 夜间模式时间。
- 夜间反馈模式与日常、夜间、紧急 TTS 音量。
- 连续识别失败次数和弱声音量阈值。

紧急词和医护词已接入 `VoiceInteractionService` 的待机监听。护士站推送、设备巡检等业务动作由事件调用方实现。

夜间反馈模式可选：

```kotlin
val nightFeedbackMode: NightFeedbackMode = NightFeedbackMode.SILENT
```

- `SILENT`：夜间不播放提示音和 TTS。
- `LOW_VOLUME`：夜间使用 `nightTtsVolume` 低音量播放。

配置文件中的 `nightFeedbackMode` 是应用启动时的默认值。正式接入时，前端可以通过统一语音服务 API 在运行时切换，无需重新编译或重启：

```kotlin
voiceService.setNightFeedbackMode(NightFeedbackMode.LOW_VOLUME)
voiceService.setNightFeedbackMode(NightFeedbackMode.SILENT)
```

监听当前运行时模式：

```kotlin
viewModelScope.launch {
    voiceService.nightFeedbackMode.collect { mode ->
        showCurrentNightFeedbackMode(mode)
    }
}
```

运行时设置当前只保存在内存中。应用进程重启后会恢复为 `VoiceInteractionConfig` 中的默认值。如果需要永久保存，调用方可使用 DataStore 保存选择，并在启动时重新调用设置 API。

相关音量配置：

```kotlin
val wakeupToneVolume: Int = 35
val dailyTtsVolume: Int = 50
val nightTtsVolume: Int = 25
val emergencyTtsVolume: Int = 70
```

### SDK 能力 ID

`app/src/main/java/com/xinya/dtx/core/speech/IflytekOfflineConfig.kt`

非 SDK 升级或甲方重新提供能力包时，不要修改能力 ID。

### SDK 凭证

凭证从项目根目录 `local.properties` 读取，并通过 `BuildConfig` 提供给语音模块：

```properties
iflytek.aiKit.appId=...
iflytek.aiKit.apiKey=...
iflytek.aiKit.apiSecret=...
iflytek.msc.appId=...
```

不要将真实凭证提交到 Git。

### 离线资源

```text
app/src/main/assets/iflytek/aikit/   AIKit 唤醒、命令词、TTS 资源
app/src/main/assets/iflytek/iat/     MSC 离线听写资源
app/libs/                            AIKit.aar、Msc.jar
app/src/main/jniLibs/                MSC 原生库
```

AIKit 资源在运行时复制到应用私有外部目录：

```text
/storage/emulated/0/Android/data/com.xinya.dtx/files/iflytek/
```

## 10. 权限与生命周期要求

调用唤醒、命令词或听写前，前端必须先获得 `RECORD_AUDIO` 权限。

页面退出、功能切换或 ViewModel 销毁时，应停止不再使用的语音引擎，避免麦克风被持续占用。

建议的基本切换流程：

```text
停止当前麦克风引擎
-> 启动目标麦克风引擎
-> 收到结果
-> 停止目标引擎
-> 执行业务动作或启动下一阶段
```

## 11. 当前完成度与边界

离线语音本身的四项基础能力已经实现并完成真机验证：

- AIKit 离线唤醒能够产出 `WakeupEvent`。
- AIKit 离线命令词能够动态加载 FSA 并产出 `CommandRecognitionResult`。
- MSC 离线听写能够产出 `IatRecognitionResult`。
- AIKit 离线 TTS 能够播放传入文本并支持停止。
- `VoiceInteractionService` 已实现待机免唤醒监听、紧急抢占、医护指令事件、60 秒连续对话、超时回待机、夜间过滤和多段指令解析。
- 已实现唤醒短提示音、拒识反馈、未知意图反馈、多指令第一条策略、连续失败统计、弱声与触屏建议事件。

以下属于业务编排，不在当前语音基础接口的已完成功能内：

- 护士站报警、患者状态记录和求助音频加密存储。
- 医护人员身份认证。语音模块目前根据专用医护指令标记 `CLINICIAN`，不能仅凭声音确认真实身份。
- 灯光、导航、音乐、护理页面等具体动作。
- 实际灯光环反馈和机器人业务任务抢占。
- 麦克风硬件增益控制；当前 SDK 接口没有提供可靠的硬件增益能力。

免唤醒监听当前通过 AIKit 命令词识别窗口自动续接实现。部署前仍需在机器人噪声环境中进行长时间稳定性、漏识别和误触发测试。
