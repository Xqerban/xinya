# 智能护理机器人语音功能实现报告

## 1. 报告说明

本报告按照甲方语音交互需求的章节顺序，说明当前 `xinya-robot` 项目中：

- 已由语音模块完整实现的能力。
- 已由语音模块识别并提供事件/API，需要前端或业务模块执行的能力。
- 仍需其他模块配合或尚未实现的能力。
- 每项功能对应的代码位置。

状态定义：

| 状态 | 含义 |
| --- | --- |
| 完全实现 | 语音模块内部已形成完整运行链路，并已完成真机/机器人测试 |
| 已提供接口 | 语音识别、意图和事件已提供；实际业务动作由前端、机器人控制或后端执行 |
| 部分实现 | 已完成可由语音模块实现的部分，但仍需要 SDK、硬件、其他模块或验收配合 |
| 未实现 | 当前项目中尚无对应实现 |

## 2. 语音模块基础能力

当前四项离线语音基础能力均已接入并完成真机/机器人测试：

| 能力 | 状态 | 实现代码 |
| --- | --- | --- |
| AIKit 离线唤醒 | 完全实现 | `core/speech/AikitWakeupEngine.kt` |
| AIKit 离线命令词识别 | 完全实现 | `core/speech/AikitCommandWordEngine.kt` |
| MSC 离线听写 | 完全实现 | `core/speech/MscOfflineIatEngine.kt` |
| AIKit 离线 TTS | 完全实现 | `core/speech/AikitOfflineTtsEngine.kt` |
| 统一语音编排 | 完全实现 | `core/voice/VoiceInteractionService.kt` |

业务模块应优先注入和调用 `VoiceInteractionService`，而不是自行同时控制多个麦克风引擎。

## 3. 总体验收结论

当前语音模块的核心链路已经完成并通过真机/机器人测试：

- AIKit 离线唤醒、离线命令词、离线 TTS 和 MSC 离线听写均可正常运行。
- 待机监听、唤醒后回复“我在”、60 秒连续对话、超时返回待机均已完成。
- 免唤醒紧急词、医护词、普通业务指令、夜间过滤、多指令优先级、拒识反馈和弱声事件均已完成。
- 语音模块能够稳定输出标准事件和 `intentCode`，供前端、机器人控制和后端业务调用。

因此，**语音识别与语音交互编排部分可以交付前端接入**。但以下内容不能标记为整个产品已经完成，因为它们属于前端、机器人控制、后端、安全或系统保活模块：

- 灯光环、灯光、床头、音乐、机器人移动等真实设备动作。
- 护士站报警推送、报警确认/关闭、患者状态读取与业务记录。
- 求助音频加密存储、删除周期和医疗审计。
- 医护人员真实身份认证。
- 麦克风硬件增益调整。
- 应用被系统杀死后的 24 小时保活、开机自启和自动恢复。
- 医疗噪声环境下的长期误触发率、漏识别率和准确率验收。

前端正式接入的统一入口是：

```kotlin
@Inject lateinit var voiceService: VoiceInteractionService
```

前端主要消费 `voiceService.events`，根据事件中的 `intent.code` 执行业务动作。完整 API 接入说明见：

`docs/offline-voice-api-readme.md`

## 一、适用范围

需求：支持患者、医护人员与护理机器人的日常服务、设备控制、信息查询和紧急求助语音交互。

状态：**已提供接口**。

语音模块已经能够将识别文本转换为标准 `VoiceIntent`，并通过 `VoiceInteractionEvent.IntentDetected` 或 `EmergencyDetected` 输出。设备控制、页面跳转、信息查询和护士站报警由事件调用方执行。

对应代码：

- 统一事件入口：`core/voice/VoiceInteractionService.kt`
- 意图模型：`core/voice/VoiceSessionModels.kt`
- 意图解析：`core/command/VoiceIntentParser.kt`
- 命令目录：`core/voice/VoiceCommandCatalog.kt`

前端主要监听：

```kotlin
voiceService.events.collect { event ->
    when (event) {
        is VoiceInteractionEvent.IntentDetected -> executeBusinessAction(event.intent)
        is VoiceInteractionEvent.EmergencyDetected -> pushEmergency(event)
        else -> Unit
    }
}
```

## 二、设计原则

### 2.1 安全优先：紧急求助免唤醒

状态：**语音识别链路完全实现，报警业务已提供接口**。

- 待机状态直接监听固定紧急词。
- 紧急词优先级最高。
- 命中后停止当前 TTS 和麦克风引擎。
- 输出带触发时间的 `EmergencyDetected`。
- 随后恢复待机监听。

对应代码：

- 紧急词配置：`VoiceInteractionConfig.kt` 的 `emergencyKeywords`
- 紧急语法生成：`VoiceCommandCatalog.standbyCommands()`
- 紧急解析：`VoiceIntentParser.parse()`
- 紧急抢占和事件：`VoiceInteractionService.handleEmergency()`

前端/业务仍需实现：

- 推送护士站报警。
- 停止机器人导航、音乐和业务任务。
- 记录患者状态、报警落库和上传。

### 2.2 隐私合规

状态：**部分实现**。

- 日常自由听写只在唤醒后启动。
- 待机仅运行本地 AIKit 命令词识别，用于唤醒词、紧急词和医护词。
- 当前识别链路不主动上传音频。
- MSC 调试配置会在应用私有目录保存 `iat.wav`，正式隐私策略需要确认是否关闭该调试音频保存。

对应代码：

- 待机/连续对话引擎切换：`VoiceInteractionService.enterStandbyLocked()`、`handleWakeup()`
- MSC 音频路径配置：`MscOfflineIatEngine.configureRecognizer()`

未实现：

- 求助音频本地加密存储。
- 音频保存周期、删除策略和医疗审计策略。

### 2.3 适配患者：短指令、弱声和容错

状态：**部分实现**。

- 已配置短指令和同义词。
- 已通过 MSC 音量回调检测弱声。
- 已输出 `WeakVoiceDetected` 和 `TouchSelectionSuggested`。
- 连续拒识后自动提示触屏选择。

对应代码：

- 弱声音量回调：`MscOfflineIatEngine.volumes`
- 弱声检测和失败计数：`VoiceInteractionService.handleRecognitionFailure()`
- 阈值配置：`VoiceInteractionConfig.weakVoiceVolumeThreshold`

未实现：

- 自动提升麦克风硬件增益。该能力需要 temi/Android 硬件接口支持。

### 2.4 医护优先

状态：**语音优先级实现，真实医护身份认证未实现**。

- 医护专用指令可以免唤醒识别。
- 医护指令输出角色 `CLINICIAN`。
- 多指令排序时，医护高于患者普通指令。

对应代码：

- 医护指令配置：`VoiceInteractionConfig.clinicianCommands`
- 医护语法：`VoiceCommandCatalog.standbyCommands()`
- 角色标记：`VoiceIntentParser.parse()`
- 优先级排序：`VoiceInteractionService.priorityOrder()`

限制：

- 当前根据“医护专用指令内容”标记角色，不能仅凭声音确认说话者真实身份。

### 2.5 降噪适配和高可靠

状态：**部分实现，仍需环境验收**。

- AIKit/MSC 使用本地 VAD 和离线模型。
- 已实现静音拒识、自动重新监听、错误事件和调试日志。
- 已完成普通真机/机器人功能测试。

仍需完成：

- 在呼吸机、监护仪和空调噪声环境中做长时间误触发、漏识别和准确率验收。
- 根据验收结果调整 AIKit/MSC 参数和弱声阈值。

## 三、唤醒词定义

### 3.1 主唤醒词“小护小护”

状态：**完全实现**。

- 主唤醒词已配置。
- 命中后输出 `WakeupDetected`。
- 播放柔和短提示音。
- 根据夜间策略播放“我在”。
- 切换到连续对话状态。

对应代码：

- 主唤醒词：`VoiceInteractionConfig.primaryWakeWord`
- 唤醒处理：`VoiceInteractionService.handleWakeup()`
- 短提示音：`VoicePromptPlayer.playWakeupTone()`
- “我在”TTS：`VoiceInteractionService.handleWakeup()`

### 3.2 副唤醒词“小芽小芽、机器人”

状态：**完全实现**。

- 副唤醒词已配置并和主唤醒词共用相同流程。
- `WakeupDetected.isPrimary` 可供前端区分主、副唤醒词。

对应代码：

- `VoiceInteractionConfig.secondaryWakeWords`
- `VoiceInteractionConfig.allWakeWords()`
- `VoiceInteractionService.handleWakeup()`

### 3.3 唤醒约束

| 需求 | 状态 | 说明 |
| --- | --- | --- |
| 主、副唤醒词功能一致 | 完全实现 | 任意唤醒词均进入连续对话 |
| 连续对话超时后重新唤醒 | 完全实现 | 60 秒后自动回到待机 |
| 主唤醒词优先于副唤醒词 | 部分实现 | 可区分主副词，但未配置独立识别阈值 |
| 灯光环变为呼吸灯 | 已提供接口 | 前端监听 `WakeupDetected` 后控制灯光 |

## 四、交互模式

### 模式 A：唤醒式交互

状态：**语音流程完全实现，业务动作已提供接口**。

流程：

```text
待机命令词监听
-> 命中唤醒词
-> 短提示音 / “我在”
-> MSC 离线听写
-> 意图解析
-> IntentDetected
-> 前端执行业务动作
```

对应代码：`VoiceInteractionService`。

### 模式 B：60 秒连续对话

状态：**完全实现**。

- 唤醒后开启 60 秒窗口。
- 窗口内无需重复唤醒。
- 有效指令后刷新 60 秒。
- 静音拒识不会刷新 60 秒。
- 超时输出 `DialogTimedOut` 并回到待机。

对应代码：

- 时长配置：`VoiceInteractionConfig.continuousDialogSeconds`
- 状态和截止时间：`VoiceSessionManager`
- 超时任务：`VoiceInteractionService.scheduleDialogTimeoutLocked()`

说明：需求结尾写有“30 秒内不用重复唤醒”，与正文 60 秒冲突。当前实现以正文的 **60 秒** 为准。

### 模式 C：免唤醒紧急交互

状态：**语音链路完全实现，报警业务已提供接口**。

- 待机直接识别紧急词。
- 紧急事件最高优先级。
- 停止当前 TTS。
- 输出 `EmergencyDetected`。

24 小时监听限制：

- 应用进程和 `VoiceInteractionService` 持续运行时可以连续监听。
- 系统杀进程、应用崩溃后的保活和自动恢复需要前台服务、开机自启或机器人系统策略支持。

## 五、免唤醒求助关键词

状态：**关键词识别完全实现，求助动作已提供接口**。

已配置关键词：

```text
护士、救命、求助、疼、好痛、难受、不舒服、快来
```

对应代码：`VoiceInteractionConfig.emergencyKeywords`。

语音模块输出：

```kotlin
VoiceInteractionEvent.EmergencyDetected(
    intent = ...,
    occurredAtMillis = ...,
)
```

已包含触发时间和关键词文本。

前端/业务需要实现：

- 推送报警至护士站大屏/主机。
- 获取和记录患者状态。
- 报警确认、关闭和审计。

## 六、医护人员专用语音指令

状态：**语音识别和优先级完全实现，具体动作已提供接口**。

已配置：

| 说法 | intentCode |
| --- | --- |
| 设备巡检 | `CLINICIAN_DEVICE_INSPECTION` |
| 开启对讲 | `CLINICIAN_INTERCOM_OPEN` |
| 关闭报警 | `CLINICIAN_ALARM_CLOSE` |
| 暂停服务 | `CLINICIAN_SERVICE_PAUSE` |
| 重启语音 | `CLINICIAN_VOICE_RESTART` |

对应代码：`VoiceInteractionConfig.clinicianCommands`。

前端监听 `IntentDetected`，根据 `intent.code` 执行具体业务。

## 七、指令分类与示例

状态：**语音识别和 intentCode 输出完全实现，设备/页面动作已提供接口**。

内置命令统一维护在：

`core/voice/VoiceCommandCatalog.kt`

### 7.1 设备与环境控制

| 说法 | intentCode | 语音状态 | 前端责任 |
| --- | --- | --- | --- |
| 打开/关闭灯光 | `LIGHT_OPEN` / `LIGHT_CLOSE` | 完全实现 | 控制灯光 |
| 调高/调低声音 | `VOLUME_UP` / `VOLUME_DOWN` | 完全实现 | 调整系统/机器人音量 |
| 到床边 | `ROBOT_GO_BEDSIDE` | 完全实现 | 调用 temi 导航 |
| 再近一点/后退一点 | `ROBOT_MOVE_CLOSER` / `ROBOT_MOVE_BACK` | 完全实现 | 调用移动接口 |
| 打开/播放音乐 | `MUSIC_OPEN` | 完全实现 | 播放音乐 |

### 7.2 服务和功能控制

| 说法 | intentCode |
| --- | --- |
| 我要喝水 | `SERVICE_WATER` |
| 帮我呼叫护士 | `SERVICE_CALL_NURSE` |
| 我想翻身 | `SERVICE_TURN_OVER` |
| 口腔护理 | `CARE_ORAL` |
| 肛周护理 | `CARE_PERIANAL` |
| 口腔PRO | `PRO_ORAL` |
| 我心情不好 | `AGENT_PSYCHOLOGY` |

### 7.3 信息查询

| 说法 | intentCode |
| --- | --- |
| 血像 | `QUERY_BLOOD` |
| 今天体温 | `QUERY_TEMPERATURE` |
| 下次用药时间 | `QUERY_MEDICATION_TIME` |

### 7.4 宣教与系统控制

| 说法 | intentCode |
| --- | --- |
| 开始/播放宣教 | `EDUCATION_START` |
| 停止/结束宣教 | `EDUCATION_STOP` |
| 确认/确认提交 | `APP_CONFIRM` |
| 停止/闭嘴/停下 | `SYSTEM_INTERRUPT` |

动态增删命令 API：

```kotlin
voiceService.updateDialogCommands(commands)
```

### 7.5 需求中尚未加入默认命令目录的指令

以下能力在需求范围中出现，但当前 `VoiceCommandCatalog.DEFAULT_DIALOG_COMMANDS` 尚未配置对应说法和 `intentCode`：

| 需求能力 | 当前状态 | 后续处理 |
| --- | --- | --- |
| 温湿度控制或查询 | 未加入默认命令目录 | 确认业务 `intentCode` 后由语音模块加入命令目录 |
| 床头升降 | 未加入默认命令目录 | 确认机器人/床体控制接口和 `intentCode` 后加入 |
| 普通时间查询 | 未加入默认命令目录 | 确认页面或系统时间查询接口后加入 |

前端也可以在运行时通过 `voiceService.updateDialogCommands(commands)` 替换连续对话命令目录，但正式产品建议把稳定指令统一维护在 `VoiceCommandCatalog.kt`，避免不同页面使用不同口径。

## 八、拒识与异常处理

### 8.1 弱声或模糊

状态：**弱声检测和提示实现；硬件增益未实现**。

- 采集 MSC 音量回调。
- 弱声拒识时输出 `WeakVoiceDetected`。
- 连续失败达到阈值时输出 `TouchSelectionSuggested`。
- 自动播报“您可以用手指轻触屏幕选择功能。”

对应代码：

- `MscOfflineIatEngine.volumes`
- `VoiceInteractionService.handleRecognitionFailure()`
- `VoiceInteractionConfig.maxConsecutiveRecognitionFailures`
- `VoiceInteractionConfig.weakVoiceVolumeThreshold`

### 8.2 多指令冲突

状态：**完全实现**。

- 按 `紧急 > 医护 > 医疗 > 环境 > 查询` 选择最高优先级第一条。
- 输出 `MultipleIntentsDetected`，其中包含执行项和忽略项。
- 自动播报“请一个一个说，我先帮您处理第一件事。”

对应代码：`VoiceInteractionService.handleDialogTranscript()`、`priorityOrder()`。

### 8.3 交互中断

状态：**停止 TTS 完全实现，停止业务动作已提供接口**。

- “停止、闭嘴、停下”映射到 `SYSTEM_INTERRUPT`。
- 语音模块立即停止当前 TTS。
- 输出 `IntentDetected(SYSTEM_INTERRUPT)`。

前端/业务需收到事件后停止导航、音乐、宣教和页面任务。

### 8.4 未唤醒时说普通话

状态：**完全实现**。

待机语法只包含：

- 主、副唤醒词。
- 紧急词。
- 医护免唤醒指令。

其他普通说话不会进入 MSC 自由听写，也不会生成普通业务意图。

### 8.5 唤醒后无指令

状态：**完全实现**。

60 秒后自动输出 `DialogTimedOut` 并返回待机。

### 8.6 指令模糊无法识别

状态：**完全实现**。

- 第一次拒识播报：“我没听清，可以再说一次吗”。
- 连续失败达到阈值后播报触屏建议。
- 输出 `RecognitionFailure`。

### 8.7 超出能力范围

状态：**完全实现**。

- 未匹配到内置命令时识别为 `FREE_TEXT`。
- 输出 `UnhandledUtterance`。
- 自动播报：“这个我暂时无法帮到你”。

## 九、夜间模式

状态：**语音侧完全实现，灯光反馈已提供接口**。

- 自动判断 `22:00–06:00`。
- 仅允许求助、核心护理、系统中断和医护指令。
- 普通指令输出 `IntentRejectedByNightMode`。
- 夜间反馈支持运行时切换：
  - `NightFeedbackMode.SILENT`
  - `NightFeedbackMode.LOW_VOLUME`

配置代码：

- `VoiceInteractionConfig.nightModeStart/nightModeEnd`
- `VoiceInteractionConfig.nightAllowedIntentCodes`
- `VoiceInteractionConfig.nightFeedbackMode`

运行时 API：

```kotlin
voiceService.setNightFeedbackMode(NightFeedbackMode.SILENT)
voiceService.setNightFeedbackMode(NightFeedbackMode.LOW_VOLUME)
voiceService.nightFeedbackMode.collect { mode -> ... }
```

前端需实现：

- 夜间仅灯光反馈。
- 夜间模式设置持久化；当前 API 切换只在应用进程内生效。

## 十、交互流程图

当前代码流程：

```text
                  +----------------------+
                  | STANDBY 待机监听     |
                  | 唤醒/紧急/医护词     |
                  +----------+-----------+
                             |
          +------------------+------------------+
          |                                     |
       唤醒词                                紧急/医护词
          |                                     |
 短提示音 + “我在”                     Emergency/Intent 事件
          |                                     |
 MSC 连续对话 60 秒                       前端执行业务动作
          |
 意图解析 / 拒识 / 夜间过滤
          |
 IntentDetected 交给前端
          |
 超时后返回 STANDBY
```

## 十一、音量与提示音规范

状态：**语音音量和提示音实现，报警音业务已提供接口**。

配置：

```kotlin
wakeupToneVolume = 35
dailyTtsVolume = 50
nightTtsVolume = 25
emergencyTtsVolume = 70
```

实现：

- 唤醒短提示音：`VoicePromptPlayer`
- AIKit TTS 可传入 `0..100` 音量：`OfflineTtsEngine.speak(..., volume)`
- 场景音量选择：`VoiceInteractionService.feedbackVolume()`

说明：

- `emergencyTtsVolume` 和紧急播报 API 已准备，但护士站报警声和具体紧急播报内容由业务模块决定。

## 十二、隐私与安全

| 需求 | 状态 | 说明 |
| --- | --- | --- |
| 唤醒后临时启用自由听写 | 完全实现 | MSC 仅在连续对话状态启动 |
| 非唤醒状态仅检测唤醒/紧急/医护特征 | 完全实现 | 使用本地 AIKit 命令词引擎 |
| 不上传普通音频 | 完全实现于当前语音模块 | 当前离线引擎没有上传逻辑 |
| 不保存无关对话 | 部分实现 | MSC 当前配置了应用私有 `iat.wav` 调试路径，正式上线需确认关闭 |
| 求助音频本地加密存储 | 未实现 | 需要录音、安全和数据模块共同实现 |

## 十三、交互总结

| 需求总结 | 当前状态 |
| --- | --- |
| 主/副唤醒词后执行日常操作 | 完全实现语音流程；业务动作通过接口交给前端 |
| 不舒服、疼、护士免唤醒求助 | 完全实现语音识别；护士站报警通过接口交给业务 |
| 连续说需求无需重复唤醒 | 完全实现，当前为 60 秒 |
| 医护指令免唤醒、高优先级 | 完全实现语音识别和事件输出 |

## 14. 前端必须接入的接口

正式产品接入统一使用：

```kotlin
@Inject lateinit var voiceService: VoiceInteractionService
```

生命周期：

```kotlin
voiceService.start()
voiceService.stop()
```

核心事件：

```kotlin
voiceService.events.collect { event ->
    when (event) {
        is VoiceInteractionEvent.EmergencyDetected -> {
            // 推送护士站报警、记录患者状态
        }
        is VoiceInteractionEvent.IntentDetected -> {
            // 根据 intent.code 执行设备控制、页面跳转或信息查询
        }
        is VoiceInteractionEvent.WakeupDetected -> {
            // 控制灯光环
        }
        is VoiceInteractionEvent.TouchSelectionSuggested -> {
            // 展示触屏选择入口
        }
        is VoiceInteractionEvent.IntentRejectedByNightMode -> {
            // 展示夜间模式反馈
        }
        else -> Unit
    }
}
```

动态配置：

```kotlin
voiceService.updateDialogCommands(commands)
voiceService.setNightFeedbackMode(NightFeedbackMode.LOW_VOLUME)
```

### 前端事件与职责对照

| 语音事件/API | 语音模块已完成 | 前端/业务模块需要完成 |
| --- | --- | --- |
| `WakeupDetected` | 输出唤醒词及主/副唤醒标记 | 控制灯光环、更新页面状态 |
| `IntentDetected` | 输出标准 `VoiceIntent` 和 `intentCode` | 执行设备控制、页面跳转、信息查询和业务请求 |
| `EmergencyDetected` | 输出紧急意图、关键词和触发时间，并打断语音任务 | 推送护士站、记录患者状态、启动报警流程 |
| `MultipleIntentsDetected` | 选择最高优先级第一条并提供忽略项 | 可展示多指令提示或记录分析日志 |
| `WeakVoiceDetected` | 输出弱声检测结果 | 可展示触屏入口或辅助提示 |
| `TouchSelectionSuggested` | 输出建议触屏事件 | 展示可点击的功能选择界面 |
| `IntentRejectedByNightMode` | 拒绝夜间非核心意图 | 展示灯光或页面反馈 |
| `DialogTimedOut` | 60 秒后退出连续对话并返回待机 | 更新界面为待机状态 |
| `setNightFeedbackMode(...)` | 运行时切换静音/低音量策略 | 提供设置入口并持久化用户选择 |

## 15. 当前未完成或需要其他模块配合的事项

- 护士站报警推送、确认、关闭和记录。
- 灯光环呼吸灯控制。
- 设备控制、机器人移动、页面跳转、音乐和信息查询业务。
- 停止机器人导航和其他非语音任务。
- 患者状态读取和紧急记录落库。
- 求助音频录制、加密存储、删除和上传策略。
- 医护人员真实身份认证。
- 麦克风硬件增益调整。
- 系统杀进程后的 24 小时保活与自动恢复。
- 医疗环境长期噪声、误触发和漏识别验收。
- 正式上线前确认并处理 MSC `iat.wav` 调试音频保存策略。

## 16. 主要代码文件索引

| 文件 | 职责 |
| --- | --- |
| `core/voice/VoiceInteractionService.kt` | 完整语音状态编排、事件、优先级、夜间、拒识 |
| `core/voice/VoiceInteractionConfig.kt` | 唤醒词、紧急词、医护词、时间、音量和策略配置 |
| `core/voice/VoiceCommandCatalog.kt` | 固定指令、同义词和 intentCode |
| `core/voice/VoiceSessionManager.kt` | 会话状态、60 秒截止时间、夜间判断 |
| `core/voice/VoiceSessionModels.kt` | 状态、优先级、角色和意图模型 |
| `core/voice/VoicePromptPlayer.kt` | 唤醒短提示音 |
| `core/command/VoiceIntentParser.kt` | 紧急、医护、医疗、环境、查询意图解析 |
| `core/speech/OfflineSpeechEngines.kt` | 提供给业务层的底层语音接口 |
| `core/speech/AikitCommandWordEngine.kt` | AIKit 离线命令词 |
| `core/speech/AikitWakeupEngine.kt` | AIKit 独立唤醒测试接口 |
| `core/speech/MscOfflineIatEngine.kt` | MSC 离线听写和音量回调 |
| `core/speech/AikitOfflineTtsEngine.kt` | AIKit 离线 TTS 和音量参数 |
| `di/VoiceModule.kt` | Hilt 语音依赖绑定 |
| `feature/home/ui/VoiceDebugViewModel.kt` | 真机语音功能测试入口和日志 |
