# 心芽 DTx 后端 API 接口文档

> 项目：骨髓移植隔离病房数字疗法系统  
> 后端框架：Spring Boot 3.2.2 · Java 17  
> 数据库：MySQL 8.x  
> Swagger UI：`http://localhost:8080/swagger-ui.html`

---

## 统一响应格式

所有接口均返回 `ApiResponse<T>` 结构：

```json
{
  "code": 200,
  "message": "success",
  "data": { }
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `code` | int | 200 = 成功，500 = 服务端错误 |
| `message` | String | 描述信息 |
| `data` | T | 业务数据，失败时为 null |

---

## 数据字典

### ClinicalStage（临床阶段枚举）

| 枚举值 | 中文名 | 顺序 |
|---|---|---|
| `ADMISSION` | 入仓期 | 1 |
| `PRETREATMENT` | 预处理期 | 2 |
| `TRANSPLANT` | 移植期 | 3 |
| `REBUILD` | 重建期 | 4 |
| `DISCHARGE` | 出仓期 | 5 |

> 阶段只能向相邻阶段流转（前进或后退一步），跨级流转返回错误。

### PatientDto

```json
{
  "id": "String (UUID)",
  "name": "String",
  "stage": "ClinicalStage",
  "psychEnergy": "Integer (0-100)",
  "treeLevel": "Integer (1-7)",
  "admissionDate": "LocalDate (yyyy-MM-dd)",
  "roomNumber": "String | null"
}
```

---

## 一、患者管理

**Base URL：** `/api/patients`  **Tag：** `患者管理`

---

### 1.1 创建患者档案

```
POST /api/patients
```

**请求体：**

```json
{
  "name": "张小明",
  "roomNumber": "A101",
  "admissionDate": "2026-03-01"
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `name` | String | ✅ | 患者姓名 |
| `roomNumber` | String | ❌ | 病房号 |
| `admissionDate` | LocalDate | ✅ | 入院日期 |

**响应：** `ApiResponse<PatientDto>`

---

### 1.2 查询单个患者

```
GET /api/patients/{id}
```

| 参数 | 位置 | 类型 | 说明 |
|---|---|---|---|
| `id` | path | String | 患者 UUID |

**响应：** `ApiResponse<PatientDto>`，患者不存在返回 `data: null`

---

### 1.3 获取所有患者列表

```
GET /api/patients
```

**响应：** `ApiResponse<List<PatientDto>>`

---

## 二、智能体对话

**Base URL：** `/api/agent`  **Tag：** `智能体对话`

> 当前 AI 处于 Demo 模式（`xinya.ai.enabled=false`），返回默认回复。  
> 每次对话自动检测危机关键词，触发后 `crisisAlert=true`。

---

### 2.1 发送对话消息

```
POST /api/agent/chat
```

**请求体：**

```json
{
  "patientId": "p-001",
  "agentType": "psych",
  "message": "今天感觉很难受",
  "sessionId": "可选，不传则自动生成"
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `patientId` | String | ✅ | 患者 ID |
| `agentType` | String | ✅ | `psych`（小芽-心理）或 `nurse`（小护士-护理） |
| `message` | String | ✅ | 用户消息内容 |
| `sessionId` | String | ❌ | 会话 ID，不传则自动生成 UUID |

**响应：** `ApiResponse<AgentChatResponse>`

```json
{
  "reply": "我理解您的感受...",
  "psychEnergyDelta": 5,
  "recommendedQuestions": ["今天心情怎么样？", "想做个放松练习吗？"],
  "crisisAlert": false
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `reply` | String | AI 回复内容 |
| `psychEnergyDelta` | Integer | 本次对话心理能量变化值 |
| `recommendedQuestions` | String[] | 推荐的后续提问 |
| `crisisAlert` | Boolean | 是否检测到危机信号（含"不想活"等关键词） |

---

### 2.2 获取推荐问题列表

```
GET /api/agent/recommendations?patientId=p-001&agentType=psych
```

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| `patientId` | query | String | ✅ | 患者 ID |
| `agentType` | query | String | ✅ | `psych` 或 `nurse` |

**响应：**

```json
{
  "code": 200,
  "data": {
    "questions": ["今天心情怎么样？", "有什么让您担心的事吗？"]
  }
}
```

---

## 三、PRO 每日打卡

**Base URL：** `/api/pro`  **Tag：** `PRO数据采集`

---

### 3.1 提交每日打卡

```
POST /api/pro/submit
```

**请求体：**

```json
{
  "patientId": "p-001",
  "recordDate": "2026-03-02",
  "answers": [
    { "questionId": "q1", "answer": "轻度", "score": 1 },
    { "questionId": "q2", "answer": "无", "score": 0 }
  ]
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `patientId` | String | ✅ | 患者 ID |
| `recordDate` | LocalDate | ❌ | 打卡日期，不传默认今天 |
| `answers` | ProAnswer[] | ❌ | 答题列表 |
| `answers[].questionId` | String | ✅ | 题目 ID |
| `answers[].answer` | String | ✅ | 答案文本 |
| `answers[].score` | Integer | ❌ | 答案分值，默认 0 |

**打卡后自动触发：**
- 患者心理能量 **+10**
- 希望之树经验 **+20**
- 每条答案持久化到 `pro_records` 表

**响应：**

```json
{
  "code": 200,
  "message": "打卡提交成功",
  "data": {
    "success": true,
    "psychEnergyDelta": 10,
    "message": "打卡成功！您的希望之树获得了成长能量。"
  }
}
```

---

## 四、希望之树

**Base URL：** `/api/hopetree`  **Tag：** `希望之树`

> 共 7 个等级，升级所需经验：100 / 250 / 450 / 700 / 1000 / 1400。  
> 满级（等级 7）时 `nextLevelExp=0`。

---

### 4.1 获取希望之树状态

```
GET /api/hopetree/{patientId}
```

| 参数 | 位置 | 类型 | 说明 |
|---|---|---|---|
| `patientId` | path | String | 患者 ID |

**响应：** `ApiResponse<HopeTreeDto>`

```json
{
  "currentLevel": 3,
  "currentExp": 120,
  "nextLevelExp": 450,
  "totalGrowthDays": 28
}
```

---

### 4.2 触发希望之树成长

```
POST /api/hopetree/grow
```

**请求体：**

```json
{
  "patientId": "p-001",
  "growthSource": "check_in",
  "expAmount": 20
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `patientId` | String | ✅ | 患者 ID |
| `growthSource` | String | ✅ | 来源：`check_in` / `education` / `conversation` |
| `expAmount` | Integer | ✅ | 增加的经验值 |

**响应：**

```json
{
  "code": 200,
  "data": {
    "success": true,
    "newLevel": 3,
    "newExp": 140,
    "levelUp": false
  }
}
```

---

## 五、临床路径

**Base URL：** `/api/clinical`  **Tag：** `临床路径`

---

### 5.1 获取患者当前临床阶段

```
GET /api/clinical/stage/{patientId}
```

| 参数 | 位置 | 类型 | 说明 |
|---|---|---|---|
| `patientId` | path | String | 患者 ID |

**响应：**

```json
{ "code": 200, "data": "PRETREATMENT" }
```

---

### 5.2 执行临床阶段流转

```
POST /api/clinical/transition
```

**请求体：**

```json
{
  "patientId": "p-001",
  "targetStage": "TRANSPLANT"
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `patientId` | String | ✅ | 患者 ID |
| `targetStage` | String | ✅ | 目标阶段（见 ClinicalStage 枚举） |

**响应：** `ApiResponse<PatientDto>`，流转非法时 `data: null`

---

## 六、护理宣教

**Base URL：** `/api/education`  **Tag：** `护理宣教`

---

### 6.1 获取宣教内容列表

```
GET /api/education/contents?category=移植期&page=1&pageSize=20
```

| 参数 | 位置 | 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|---|
| `category` | query | String | ❌ | — | 按分类筛选，不传返回全部 |
| `page` | query | int | ❌ | 1 | 页码 |
| `pageSize` | query | int | ❌ | 20 | 每页条数 |

**响应：**

```json
{
  "code": 200,
  "data": {
    "contents": [
      {
        "id": "ec-001",
        "title": "移植前你需要了解的事",
        "category": "预处理期",
        "description": "详细介绍造血干细胞移植前的准备工作",
        "contentType": "video",
        "durationSeconds": 480,
        "thumbnailUrl": "https://...",
        "mediaUrl": "https://...",
        "tags": ["移植", "准备"]
      }
    ],
    "total": 3
  }
}
```

---

## 七、数据驾驶舱（医护端）

**Base URL：** `/api/dashboard`  **Tag：** `数据驾驶舱`

---

### 7.1 获取驾驶舱概览数据

```
GET /api/dashboard/overview
```

**响应：** `ApiResponse<DashboardDto>`

```json
{
  "totalPatients": 12,
  "patientsByStage": {
    "ADMISSION": 2,
    "PRETREATMENT": 3,
    "TRANSPLANT": 4,
    "REBUILD": 2,
    "DISCHARGE": 1
  },
  "avgPsychEnergy": 62.5,
  "symptomTrends": [
    { "symptom": "恶心", "lastWeek": 6, "thisWeek": 4, "changePercent": -33.3 }
  ],
  "alerts": [
    { "level": "warning", "message": "患者 p-003 心理能量低于30", "patientId": "p-003" }
  ],
  "learningStats": {
    "avgCompletionRate": 0.72,
    "avgWatchTime": 8.5,
    "engagementRate": 0.68
  }
}
```

---

## 八、机器人数据接入

**Base URL：** `/api/robot`  **Tag：** `机器人对接`

---

### 8.1 接收机器人推送数据

```
POST /api/robot/data
```

**请求体：**

```json
{
  "patientId": "p-001",
  "dataType": "vital_signs",
  "payload": {
    "heartRate": 82,
    "temperature": 36.8,
    "bloodPressure": "120/80"
  },
  "timestamp": 1709366400000
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `patientId` | String | ✅ | 患者 ID |
| `dataType` | String | ✅ | `vital_signs` / `activity` / `voice` |
| `payload` | Map | ✅ | 数据内容（自由结构） |
| `timestamp` | Long | ✅ | 数据采集时间戳（毫秒） |

**响应：**

```json
{ "code": 200, "data": { "received": true, "timestamp": 1709366400000 } }
```

> ⚠️ 当前仅接收数据，业务处理逻辑待实现。

---

## 九、待实现接口

以下接口 **Android 端已调用，后端尚未实现**：

| 方法 | 路径 | 功能 | 优先级 |
|---|---|---|---|
| `POST` | `/api/sync/batch` | 离线数据批量同步上传 | 高 |

以下接口 **功能存在但尚未暴露 API**：

| 功能 | 说明 |
|---|---|
| 患者信息更新 | 无 PUT 接口 |
| 患者删除 | 无 DELETE 接口 |
| PRO 打卡历史查询 | 数据有但无查询接口 |
| 对话历史查询 | 数据有但无查询接口 |
| 宣教内容管理 | 无增删改接口（仅查询） |

---

## 前端对接参考

### Admin 端（Vue3 + Axios）

`baseURL = '/api'`，所有请求自动携带 Token 并解包 `data` 字段。

| 函数 | 调用接口 |
|---|---|
| `getPatients()` | `GET /api/patients` |
| `getPatient(id)` | `GET /api/patients/:id` |
| `createPatient(data)` | `POST /api/patients` |
| `updatePatientStage(patientId, targetStage)` | `POST /api/clinical/transition` |
| `getDashboardOverview()` | `GET /api/dashboard/overview` |
| `getEducationContents(params?)` | `GET /api/education/contents` |

### Android 端（Kotlin + Retrofit2）

`baseURL = http://{host}:8080/`

| 方法 | 调用接口 |
|---|---|
| `createPatient(request)` | `POST api/patients` |
| `getPatient(id)` | `GET api/patients/{id}` |
| `chat(request)` | `POST api/agent/chat` |
| `getRecommendedQuestions(patientId, agentType)` | `GET api/agent/recommendations` |
| `getCurrentStage(patientId)` | `GET api/clinical/stage/{patientId}` |
| `transitionStage(request)` | `POST api/clinical/transition` |
| `submitPro(request)` | `POST api/pro/submit` |
| `getHopeTreeStatus(patientId)` | `GET api/hopetree/{patientId}` |
| `growHopeTree(request)` | `POST api/hopetree/grow` |
| `getEducationContents(...)` | `GET api/education/contents` |
| `syncBatch(items)` | `POST api/sync/batch` ❌ 后端未实现 |
