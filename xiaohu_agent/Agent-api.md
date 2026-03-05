### 4.5 【Agent 服务接口规范】后端调用 AI 智能体的接口约定

> **本节面向 Agent 开发同事。**  
> 系统共有两个独立智能体：**小芽（心理陪护）** 和 **小护士（护理宣教）**，分别对应不同接口路径前缀。  
> 后端作为调用方，请按规范实现 HTTP 服务并启动，后端通过 `xinya.ai.base-url` 指向服务地址。

---

#### 整体调用链路

```
机器人端
  │
  │  POST /api/agent/chat  (agentType=psych 或 nurse)
  ▼
Spring Boot 后端
  │  ① 查询患者信息、对话历史、血象数据（补全上下文）
  │  ② 根据 agentType 路由至对应智能体接口
  │       psych  →  POST {base-url}/v1/psych/chat
  │       nurse  →  POST {base-url}/v1/nurse/chat
  │  ③ 解析响应：更新心理能量 / 希望之树积分 / 触发预警 / 推送提醒
  │
  ▼
机器人端（返回最终响应）

── 独立触发（非对话触发）──────────────────────────────────
后端监测到症状主诉  →  POST {base-url}/v1/nurse/symptom-trigger
后端血象数据变更    →  POST {base-url}/v1/nurse/reminder-plan
```

---

## 一、心理陪护智能体（小芽 · psych）

---

#### A. 对话推理接口（含心理能量评估 & 危机风控）

```
POST {base-url}/v1/psych/chat
```

> 后端每次收到用户消息时调用。小芽需在单次推理中同时完成：对话回复、**五维心理能量评估**、**危机风控分级**。

**请求体（后端 → Agent）：**

```json
{
  "sessionId": "session-uuid-xxx",
  "patientContext": {
    "patientId": "p-001",
    "name": "张小明",
    "stage": "PRETREATMENT",
    "stageName": "预处理期",
    "daysInStage": 10,
    "psychEnergy": 55,
    "treeLevel": 3,
    "age": 35,
    "gender": "MALE",
    "diagnosis": "急性髓系白血病"
  },
  "history": [
    { "role": "user",      "content": "昨天很难受" },
    { "role": "assistant", "content": "我理解，昨天的治疗确实很辛苦..." }
  ],
  "message": "今天感觉好一点了，但还是很害怕"
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `sessionId` | String | 会话 ID，用于 Agent 侧维护记忆 |
| `patientContext` | Object | 患者当前状态快照 |
| `patientContext.stage` | String | 临床阶段枚举（见数据字典） |
| `patientContext.psychEnergy` | Integer | 当前心理能量 0-100 |
| `history` | Array | 最近 10 轮对话，`role` 为 `user` 或 `assistant` |
| `message` | String | 用户本次输入 |

**Agent 响应（Agent→ 后端）：**

```json
{
  "reply": "听到你说好一点了，我真的很高兴。害怕是很正常的感受，能告诉我你现在最担心的是什么吗？",

  "energyAssessment": {
    "cognitiveGrowth":    2,
    "emotionRegulation":  3,
    "behaviorChange":     1,
    "socialConnection":   0,
    "selfEfficacy":       2,
    "totalDelta":         8,
    "hopeTreeExpDelta":   10,
    "assessmentNote":     "患者主动表达改善迹象，情绪调节维度得分较高"
  },

  "crisisAssessment": {
    "crisisAlert":    false,
    "crisisLevel":    "none",
    "crisisKeywords": [],
    "emotionSignals": ["轻度焦虑", "恐惧"],
    "action":         "none",
    "mindfulnessGuide": null
  },

  "recommendedQuestions": [
    "现在最让你担心的是什么？",
    "想做一个让心情平静下来的呼吸练习吗？"
  ],

  "agentMeta": {
    "model": "deepseek-chat",
    "tokensUsed": 512,
    "latencyMs": 1200
  }
}
```

---

##### energyAssessment — 心理能量五维评估模型

后端将依据此对象完成心理能量更新和希望之树积分发放，**请务必返回**。

| 字段 | 类型 | 必须 | 说明 |
|---|---|---|---|
| `cognitiveGrowth` | Integer | ✅ | **认知成长**：是否产生新认知/接受新信息，0-5 分 |
| `emotionRegulation` | Integer | ✅ | **情绪调节**：情绪是否趋向平稳/积极，0-5 分 |
| `behaviorChange` | Integer | ✅ | **行为改变**：是否表达出行动意愿（如"我想试试"），0-5 分 |
| `socialConnection` | Integer | ✅ | **社交连接**：是否提及家人支持/表达感谢，0-5 分 |
| `selfEfficacy` | Integer | ✅ | **自我效能**：是否展现自我掌控感/康复信心，0-5 分 |
| `totalDelta` | Integer | ✅ | 五维总分（即本轮 `psychEnergyDelta`，后端直接使用） |
| `hopeTreeExpDelta` | Integer | ✅ | 本轮对话奖励的希望之树经验值（建议范围 0-15） |
| `assessmentNote` | String | ❌ | 评估理由简述，供后端日志记录 |

> **评分规则建议：**  
> - 每维度 0 分 = 未体现，1-2 分 = 轻度体现，3-4 分 = 明显体现，5 分 = 强烈体现  
> - `totalDelta` 建议 = 五维之和 / 5（取整），范围控制在 **-10 ~ +20**  
> - 若整体对话消极（如大量负面情绪输出），`totalDelta` 可为负值  
> - `hopeTreeExpDelta` 与 `totalDelta` 正相关，但不应超过 15

---

##### crisisAssessment — 危机干预风控模型

| 字段 | 类型 | 必须 | 说明 |
|---|---|---|---|
| `crisisAlert` | Boolean | ✅ | 是否触发危机预警（后端据此推送医护 PAD） |
| `crisisLevel` | String | ✅ | 危机等级：`none` / `watch` / `warning` / `critical` |
| `crisisKeywords` | String[] | ✅ | 命中的危机信号词，未命中传 `[]` |
| `emotionSignals` | String[] | ✅ | 本轮检测到的情绪信号标签（见下表），未检测传 `[]` |
| `action` | String | ✅ | 建议后端执行的动作（见下表） |
| `mindfulnessGuide` | Object \| null | ✅ | 当 `action` 含正念引导时返回，否则为 `null` |

**crisisLevel 与 action 映射关系：**

| `crisisLevel` | 含义 | `action` 取值 | 后端行为 |
|---|---|---|---|
| `none` | 无风险 | `none` | 正常流程 |
| `watch` | 轻度消极情绪，需关注 | `log_only` | 记录情绪标签，不预警 |
| `warning` | 检测到无助感/绝望/放弃治疗意念 | `mindfulness_guide` | 返回正念引导内容给机器人端 + 创建 `warning` 预警推送医护 |
| `critical` | 明确的自伤/轻生表达 | `alert_and_notify` | 创建 `critical` 预警 + 推送医护 + 机器人端呼叫护士 |

**emotionSignals 情绪信号标签参考：**

```
轻度焦虑 / 中度焦虑 / 重度焦虑
轻度抑郁 / 中度抑郁
无助感 / 绝望感 / 放弃意念
孤独感 / 恐惧 / 愤怒
积极应对 / 感恩 / 康复信念
```

**mindfulnessGuide 结构（当 action = `mindfulness_guide` 时返回）：**

```json
{
  "mindfulnessGuide": {
    "type": "breathing",
    "title": "4-7-8 放松呼吸练习",
    "instruction": "我们一起来做个呼吸练习好吗？用鼻子吸气4秒，屏住呼吸7秒，再用嘴慢慢呼出8秒...",
    "durationSeconds": 120,
    "mediaUrl": null
  }
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `type` | String | `breathing`（呼吸练习）/ `grounding`（落地练习）/ `imagery`（正念冥想） |
| `title` | String | 练习名称，显示在机器人端屏幕 |
| `instruction` | String | 引导词，机器人端语音播报 |
| `durationSeconds` | Integer | 练习时长（秒） |
| `mediaUrl` | String \| null | 配套音频/视频 URL（可选） |

---

#### B. 推荐提问接口

```
POST {base-url}/v1/psych/recommendations
```

**请求体：**

```json
{
  "patientContext": {
    "patientId": "p-001",
    "stage": "PRETREATMENT",
    "psychEnergy": 55,
    "treeLevel": 3
  },
  "recentHistory": [
    { "role": "user",      "content": "今天感觉好一点了" },
    { "role": "assistant", "content": "听到你说好一点了，我真的很高兴..." }
  ]
}
```

**Agent 响应：**

```json
示例：
{
  "questions": [
    "今天心情怎么样？",
    "身体上有什么新的不适感吗？",
    "想聊聊对下一步治疗的感受吗？",
    "要不要一起做个放松练习？"
  ]
}
```

---

## 二、护理宣教智能体（小护士 · nurse）

---

#### C. 对话推理接口

```
POST {base-url}/v1/nurse/chat
```

> 小护士负责宣教答疑，请求体结构与小芽相同，但响应中 **不需要** 返回 `energyAssessment` 和 `crisisAssessment`（由心理智能体负责）。

**请求体：** 同 `/v1/psych/chat`（`sessionId`、`patientContext`、`history`、`message`）

**Agent 响应：**

```json
{
  "reply": "您问的这个问题很好！预处理期恶心主要是化疗药物刺激消化道引起的，一般在用药后24-48小时内最明显...",
  "recommendedQuestions": [
    "恶心厉害时有什么饮食建议？",
    "什么情况下需要马上告诉护士？"
  ],
  "recommendedContents": [
    {
      "contentId": "ec-003",
      "reason": "患者询问恶心相关问题，推荐对应宣教视频"
    }
  ],
  "agentMeta": {
    "model": "deepseek-chat",
    "tokensUsed": 380,
    "latencyMs": 900
  }
}
```

| 字段 | 类型 | 必须 | 说明 |
|---|---|---|---|
| `reply` | String | ✅ | 护理宣教回复正文 |
| `recommendedQuestions` | String[] | ✅ | 追问引导（2-4 条） |
| `recommendedContents` | Array | ❌ | 对话中识别到的相关宣教内容推荐，后端将推送给机器人端播放 |
| `recommendedContents[].contentId` | String | ✅ | 宣教内容 ID（对应 `education_contents` 表） |
| `recommendedContents[].reason` | String | ❌ | 推荐理由，供日志记录 |

---

#### D. 情景触发式内容推荐接口

```
POST {base-url}/v1/nurse/symptom-trigger
```

> **触发时机：** 非对话流程。后端在以下两种场景主动调用：  
> 1. 患者 PRO 打卡时，某症状评分 ≥ 阈值（如恶心评分 ≥ 2）  
> 2. 对话中小护士或小芽识别到症状关键词（后端解析后回调）  
>
> Agent 返回**精准匹配的宣教内容 + 即时推送文案**，后端立即下发给机器人端展示。

**请求体（后端 → Agent）：**

```json
{
  "patientId": "p-001",
  "patientContext": {
    "stage": "PRETREATMENT",
    "stageName": "预处理期",
    "daysInStage": 10,
    "psychEnergy": 55
  },
  "triggerSource": "pro_checkin",
  "detectedSymptoms": [
    {
      "symptomKey":  "nausea",
      "symptomName": "恶心",
      "score":       3,
      "maxScore":    3
    },
    {
      "symptomKey":  "fatigue",
      "symptomName": "乏力",
      "score":       2,
      "maxScore":    3
    }
  ],
  "viewedContentIds": ["ec-001", "ec-002"]
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `triggerSource` | String | 触发来源：`pro_checkin`（打卡触发）/ `conversation`（对话触发） |
| `detectedSymptoms` | Array | 检测到的症状列表及评分 |
| `detectedSymptoms[].symptomKey` | String | 症状标识符（标准化，见参考表） |
| `detectedSymptoms[].score` | Integer | 当前评分 |
| `detectedSymptoms[].maxScore` | Integer | 最高分（用于计算严重度） |
| `viewedContentIds` | String[] | 患者已观看过的内容 ID，Agent 应优先推荐**未看过的**内容 |

**症状 Key 参考表：**

| symptomKey | 中文名 | 主要相关阶段 |
|---|---|---|
| `nausea` | 恶心/呕吐 | PRETREATMENT、TRANSPLANT |
| `fatigue` | 乏力 | 全阶段 |
| `oral_mucositis` | 口腔黏膜炎 | TRANSPLANT、REBUILD |
| `fever` | 发热 | TRANSPLANT、REBUILD |
| `diarrhea` | 腹泻 | PRETREATMENT、REBUILD |
| `anxiety` | 焦虑/紧张 | 全阶段 |
| `appetite_loss` | 食欲不振 | PRETREATMENT、TRANSPLANT |
| `skin_rash` | 皮疹 | REBUILD（排异反应） |

**Agent 响应：**

```json
{
  "pushMessage": "小明，我注意到你今天恶心感比较明显。我为你找了一个专门讲这个的小视频，一起看看怎么缓解吧～",
  "recommendedContents": [
    {
      "contentId":       "ec-003",
      "title":           "认识预处理：恶心呕吐应对指南",
      "matchedSymptom":  "nausea",
      "priority":        1,
      "reason":          "患者恶心评分3/3，推荐核心应对视频"
    },
    {
      "contentId":       "ec-007",
      "title":           "预处理期饮食调整小贴士",
      "matchedSymptom":  "appetite_loss",
      "priority":        2,
      "reason":          "恶心伴食欲下降，补充饮食管理知识"
    }
  ],
  "hopeTreeExpDelta": 15,
  "agentMeta": {
    "model": "deepseek-chat",
    "latencyMs": 800
  }
}
```

| 字段 | 类型 | 必须 | 说明 |
|---|---|---|---|
| `pushMessage` | String | ✅ | 机器人端展示的推送文案（亲切口吻），后端透传 |
| `recommendedContents` | Array | ✅ | 推荐内容列表，按 `priority` 升序排列 |
| `recommendedContents[].contentId` | String | ✅ | 宣教内容 ID |
| `recommendedContents[].matchedSymptom` | String | ✅ | 对应的症状 Key |
| `recommendedContents[].priority` | Integer | ✅ | 优先级，1 最高 |
| `recommendedContents[].reason` | String | ❌ | 推荐理由，供日志 |
| `hopeTreeExpDelta` | Integer | ✅ | 触发推送奖励的希望之树经验值（范围 0-15） |

---

#### E. 血象趋势个性化提醒推荐接口

```
POST {base-url}/v1/nurse/reminder-plan
```

> **触发时机：** 后端在以下场景调用：  
> 1. **即时提醒**：医护在 PAD 端录入新的血象检测结果，后端立即调用  
> 2. **定时提醒**：每日凌晨 2 点，后端对所有活跃患者批量调用，生成次日提醒计划  
>
> Agent 根据血象趋势和当前临床阶段，生成**今日/明日的推送内容计划**（含推送时间、内容、文案）。

**请求体（后端 → Agent）：**

```json
{
  "patientId": "p-001",
  "patientContext": {
    "stage": "REBUILD",
    "stageName": "重建期",
    "daysInStage": 8,
    "psychEnergy": 62,
    "treeLevel": 4
  },
  "bloodTrend": {
    "latestRecord": {
      "recordDate":   "2026-03-02",
      "wbc":          1.2,
      "neutrophil":   0.5,
      "platelet":     35,
      "hemoglobin":   88
    },
    "history": [
      { "recordDate": "2026-02-28", "wbc": 0.8,  "neutrophil": 0.3, "platelet": 22,  "hemoglobin": 82 },
      { "recordDate": "2026-03-01", "wbc": 1.0,  "neutrophil": 0.4, "platelet": 28,  "hemoglobin": 85 },
      { "recordDate": "2026-03-02", "wbc": 1.2,  "neutrophil": 0.5, "platelet": 35,  "hemoglobin": 88 }
    ],
    "trends": {
      "wbcTrend":          "RISING",
      "neutrophilTrend":   "RISING",
      "plateletTrend":     "RISING",
      "hemoglobinTrend":   "RISING"
    }
  },
  "planType": "daily_schedule",
  "viewedContentIds": ["ec-001", "ec-005"]
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `bloodTrend.latestRecord` | Object | 最新一次血象数据（单位：wbc/neutrophil × 10⁹/L，platelet × 10⁹/L，hemoglobin g/L） |
| `bloodTrend.history` | Array | 最近 7 天血象记录 |
| `bloodTrend.trends.*` | String | 后端预计算的趋势：`RISING`（上升）/ `FALLING`（下降）/ `STABLE`（平稳） |
| `planType` | String | `immediate`（即时提醒）/ `daily_schedule`（每日计划） |

**Agent 响应：**

```json
{
  "patientId": "p-001",
  "planType": "daily_schedule",
  "trendInterpretation": "白细胞和中性粒细胞连续3天上升，提示造血重建进展良好，但血小板仍低需注意出血防护",
  "reminderPlan": [
    {
      "reminderId":    "r-001",
      "scheduledTime": "08:30",
      "type":          "education_push",
      "contentId":     "ec-009",
      "pushMessage":   "早上好！你的血象在悄悄变好了～今天我们来了解一下血小板低时要注意什么哦",
      "priority":      1,
      "triggerReason": "血小板35，低于正常值，推送出血防护宣教"
    },
    {
      "reminderId":    "r-002",
      "scheduledTime": "15:00",
      "type":          "encouragement",
      "contentId":     null,
      "pushMessage":   "小明，你的白细胞今天达到1.2了！这是你的希望之树正在茁壮成长的信号，继续加油！",
      "priority":      2,
      "triggerReason": "白细胞连续上升，给予正向激励"
    },
    {
      "reminderId":    "r-003",
      "scheduledTime": "20:00",
      "type":          "education_push",
      "contentId":     "ec-012",
      "pushMessage":   "睡前小知识：重建期为什么要每天测血象？点击了解背后的原因～",
      "priority":      3,
      "triggerReason": "阶段匹配：重建期血象监测知识宣教"
    }
  ],
  "immediateAlert": null,
  "hopeTreeExpDeltaPerCompletion": 15
}
```

| 字段 | 类型 | 必须 | 说明 |
|---|---|---|---|
| `trendInterpretation` | String | ✅ | 血象趋势解读文本，供医护 PAD 展示 |
| `reminderPlan` | Array | ✅ | 今日提醒计划列表 |
| `reminderPlan[].reminderId` | String | ✅ | 提醒唯一 ID（Agent 生成，后端用于去重） |
| `reminderPlan[].scheduledTime` | String | ✅ | 建议推送时间（`HH:mm` 格式，后端按此排队推送） |
| `reminderPlan[].type` | String | ✅ | `education_push`（宣教推送）/ `encouragement`（激励通知）/ `medication_reminder`（用药提醒） |
| `reminderPlan[].contentId` | String \| null | ✅ | 关联的宣教内容 ID，无内容时为 `null` |
| `reminderPlan[].pushMessage` | String | ✅ | 机器人端展示的推送文案 |
| `reminderPlan[].priority` | Integer | ✅ | 优先级，当日提醒数量过多时后端按此裁减 |
| `reminderPlan[].triggerReason` | String | ❌ | 触发理由，供日志 |
| `immediateAlert` | Object \| null | ✅ | **即时预警**：当血象出现危急值时（如 wbc < 0.5）返回，否则为 `null` |
| `hopeTreeExpDeltaPerCompletion` | Integer | ✅ | 每完成一条提醒推送奖励的希望之树经验值 |

**immediateAlert（危急血象即时预警）结构：**

```json
{
  "immediateAlert": {
    "level":      "warning",
    "indicator":  "neutrophil",
    "value":      0.2,
    "threshold":  0.5,
    "message":    "中性粒细胞极低（0.2），感染风险极高，建议护士立即评估并执行保护性隔离措施",
    "pushMessageToPatient": "小明，今天的检查结果显示你的免疫细胞需要特别保护，护士阿姨一会儿会来看你，有什么不舒服要第一时间告诉她哦"
  }
}
```

> 后端收到 `immediateAlert` 不为 `null` 时，将同步创建医护预警并推送至 PAD，同时将 `pushMessageToPatient` 下发给机器人端播报。

---

#### F. 护理推荐提问接口

```
POST {base-url}/v1/nurse/recommendations
```

**请求体：**

```json
{
  "patientContext": {
    "patientId": "p-001",
    "stage": "PRETREATMENT",
    "psychEnergy": 55
  },
  "recentSymptoms": ["nausea", "fatigue"],
  "recentHistory": [
    { "role": "user",      "content": "吃东西就想吐" },
    { "role": "assistant", "content": "恶心呕吐在预处理期很常见..." }
  ]
}
```

**Agent 响应：**

```json
{
  "questions": [
    "今天恶心的感觉是什么时候最严重？",
    "有试过少量多餐吗？",
    "想看一个缓解恶心的小视频吗？",
    "口腔里有没有出现溃疡或不适？"
  ]
}
```

---

## 三、通用约定

---

#### G. 接口规范与错误处理

| 约定项 | 要求 |
|---|---|
| **协议** | HTTP/HTTPS，`Content-Type: application/json` |
| **超时** | 后端调用超时设定为 **30 秒**，请确保正常响应在此时限内，另外优质调用时间应该控制在**15秒** |
| **认证** | 后端请求头会携带 `X-Api-Key: {配置的密钥}`，请校验 |
| **响应格式** | 直接返回业务数据 JSON，**不需要**包装 `code/message/data` 外层 |
| **降级策略** | 超时或异常时后端使用兜底回复，业务流程不中断 |

**出错时返回（HTTP 5xx）：**

```json
{
  "error": "model_overloaded",
  "message": "当前服务繁忙，请稍后重试"
}
```