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