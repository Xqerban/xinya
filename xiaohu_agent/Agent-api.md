

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