# 智能机器人离线语音方案

## 1. 当前基线

- 甲方指定 SDK 仅以以下两个包为准：
  - AIKit runtime files are vendored under `app/libs` and `app/src/main/assets/iflytek/aikit`.
  - MSC runtime files are vendored under `app/libs`, `app/src/main/jniLibs`, and `app/src/main/assets/iflytek/iat`.
- 当前项目 `xinya-robot` 已接入：
  - `AIKit.aar`：离线唤醒、命令词、TTS
  - `Msc.jar + libmsc.so + iat/*.jet`：离线语音听写

## 2. 需求边界

基础能力仍按原表格执行：

- `ASR`：语音转文字
- `NLP`：理解意图 + 生成回答
- `TTS`：文字转语音
- `机器人功能控制`
- `应用功能控制`
- `语音录制`
- `视频录制`

甲方新增交互规范补充了以下关键约束：

- 主唤醒词：`小护小护`
- 副唤醒词：`小芽小芽`、`机器人`
- 连续对话窗口：默认按甲方口径实现为 `60 秒`
- 免唤醒紧急词固定不可修改：
  - `护士`
  - `救命`
  - `求助`
  - `疼`
  - `好痛`
  - `难受`
  - `不舒服`
  - `快来`
- 医护免唤醒高优先级指令：
  - `设备巡检`
  - `开启对讲`
  - `关闭报警`
  - `暂停服务`
  - `重启语音`
- 优先级：`紧急 > 医疗 > 环境 > 查询`
- 夜间模式：`22:00 - 06:00`
- 非唤醒、非求助状态：不识别、不存储、不上传

## 3. SDK 能力分工

### 3.1 AIKit

负责：

- 离线唤醒
- 离线命令词识别
- 离线语音合成

使用目录：

- `app/libs/AIKit.aar`
- `app/src/main/assets/iflytek/aikit/...`

### 3.2 MSC 离线听写

负责：

- 自由表达的离线语音听写
- 用于业务问答、弱结构化表达、无法被命令词完全覆盖的场景

使用目录：

- `app/libs/Msc.jar`
- `app/src/main/jniLibs/.../libmsc.so`
- `app/src/main/assets/iflytek/iat/*.jet`

## 4. 总体交互流水线

### 4.1 待机态

- 常驻监听免唤醒求助词
- 常驻监听医护免唤醒指令
- 常驻监听唤醒词
- 非上述三类输入，不做识别结果落库，不存储音频

### 4.2 唤醒式交互

1. 检测主/副唤醒词
2. 播放提示音 + 灯效反馈 + TTS `我在`
3. 进入连续对话窗口
4. 在窗口内优先走命令词识别
5. 命中固定意图则直接执行
6. 未命中固定意图则转离线听写
7. 听写文本进入本地 NLP
8. 路由到机器人控制 / 应用控制 / 本地知识问答 / 录音录像控制
9. TTS 反馈结果
10. 超时后退回待机态

### 4.3 免唤醒紧急交互

1. 检测求助关键词
2. 立即打断当前任务
3. 执行本地报警流程
4. 记录触发时间、关键词、上下文状态
5. 本地加密保存求助音频
6. 触发护士站告警 / 对讲流程

### 4.4 医护免唤醒交互

1. 检测医护专用指令
2. 优先级高于患者普通指令
3. 直接执行设备/服务控制
4. 记录审计日志

## 5. 本地 NLP 方案

不采用“单一开放问答模型”路线，采用双通道：

### 5.1 固定意图通道

适用：

- 设备控制
- 服务呼叫
- 页面打开
- 录音录像控制
- 固定格式填报

实现：

- AIKit 命令词优先
- 命中后输出标准化 `intentCode`
- 由本地 `CommandRouter` 分发

### 5.2 自由表达通道

适用：

- `我心情不好`
- `怎么做口腔护理`
- `今天体温是多少`
- `我好疼`

实现：

- MSC 离线听写转文本
- 本地规则分类 + 槽位提取
- 命中知识问题时查询本地知识库
- 命中业务录入时提取参数
- 未命中时回复：`这个我暂时无法帮到你`

## 6. 模块设计

建议新增以下模块：

- `core/speech/`
  - `WakeupEngine`
  - `CommandWordEngine`
  - `OfflineIatEngine`
  - `OfflineTtsEngine`
- `core/voice/`
  - `VoiceInteractionService`
  - `VoiceSessionManager`
  - `VoicePriorityResolver`
- `core/nlp/`
  - `IntentClassifier`
  - `SlotExtractor`
  - `LocalKnowledgeService`
- `core/command/`
  - `CommandRouter`
  - `RobotActionHandler`
  - `AppActionHandler`
  - `EmergencyHandler`
- `core/media/`
  - `AudioRecordService`
  - `VideoRecordService`
  - `UploadPolicy`

## 7. 配置化要求

甲方要求指令表可增删，因此不能把命令写死在代码中。

建议采用以下本地配置模型：

- `offline_command_group`
  - `code`
  - `name`
  - `priority`
  - `enabled`
- `offline_command`
  - `code`
  - `groupCode`
  - `intentCode`
  - `actionType`
  - `target`
  - `paramsJson`
  - `replyTemplate`
  - `enabled`
- `offline_command_phrase`
  - `commandCode`
  - `phrase`
  - `weight`
  - `enabled`
- `offline_knowledge_item`
  - `code`
  - `category`
  - `keywords`
  - `questionPatterns`
  - `answerText`
  - `enabled`

第一阶段先支持本地 JSON / Room 配置驱动，后续再考虑做后台维护。

## 8. 首批 MVP 指令集

### 8.1 唤醒词

- `小护小护`
- `小芽小芽`
- `机器人`

### 8.2 免唤醒求助词

- `护士`
- `救命`
- `求助`
- `疼`
- `好痛`
- `难受`
- `不舒服`
- `快来`

### 8.3 医护免唤醒指令

- `设备巡检`
- `开启对讲`
- `关闭报警`
- `暂停服务`
- `重启语音`

### 8.4 设备控制

- `打开灯光`
- `关闭灯光`
- `调高声音`
- `调低声音`
- `到床边`
- `再近一点`
- `后退一点`
- `打开音乐`
- `停止`
- `闭嘴`

### 8.5 服务呼叫

- `我要喝水`
- `帮我呼叫护士`
- `我想翻身`

### 8.6 功能控制

- `口腔护理`
- `肛周护理`
- `口腔PRO`
- `我心情不好`

### 8.7 信息查询

- `血像`
- `今天体温`
- `下次用药时间`

## 9. 异常处理

- 微弱语音识别失败：
  - 自动尝试提高增益
  - 失败后提示：`您可以用手指轻触屏幕选择功能。`
- 指令冲突：
  - 回复：`请一个一个说，我先帮您处理第一件事。`
- 未听清：
  - 回复：`我没听清，可以再说一次吗`
- 超出能力：
  - 回复：`这个我暂时无法帮到你`
- 中断词：
  - `闭嘴`
  - `停下`

## 10. 音视频策略

- 默认：
  - 本地保存
  - 手动上传
- 可切换：
  - 联网后自动上传
- 求助音频：
  - 本地加密存储
- 视频：
  - 事件触发录制
  - 元数据先本地落库

## 11. 实现顺序

1. 完成 Gradle 依赖与 SDK 初始化
2. 建立 AIKit 资源复制到工作目录的初始化逻辑
3. 建立唤醒、命令词、TTS 抽象层
4. 建立 MSC 离线听写抽象层
5. 建立语音交互状态机
6. 建立本地意图与知识库路由
7. 接 temi 动作控制和应用控制
8. 接入录音录像和上传策略
9. 增加夜间模式、审计日志、加密存储

## 12. 当前已知风险

- AIKit demo 使用的工作目录为 `/sdcard/iflytek/`，主工程需要改成可控的应用工作目录，并在首次运行时复制资源。
- 当前 AIKit 与 MSC 属于双栈并存，后续需统一封装，避免业务层直接依赖具体 SDK。
- 摄像头录像尚未在主工程实现，后续需要补权限、录制服务和事件联动。
