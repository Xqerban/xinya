# 心芽 DTx — 接口文档 & 业务流程

> **项目：** 骨髓移植隔离病房数字疗法系统（HSCT DTx）  
> **后端：** Spring Boot 3.2.2 · Java 17 · MySQL 8.x  
> **Swagger UI：** `http://localhost:8080/swagger-ui.html`  
> **文档版本：** v2.0 · 2026-03-02

---

## 目录

1. [统一规范](#统一规范)
2. [数据字典](#数据字典)
3. [模块一：认证与权限](#模块一认证与权限)
4. [模块二：患者管理](#模块二患者管理)
5. [模块三：临床路径](#模块三临床路径)
6. [模块四：智能体对话](#模块四智能体对话)（含 [Agent 服务接口规范 ⬅️ Agent 同事看这里](#45-agent-服务接口规范后端调用-ai-智能体的接口约定)，覆盖小芽·心理陪护 & 小护士·护理宣教 共 6 个接口）
7. [模块五：PRO 每日打卡](#模块五pro-每日打卡)
8. [模块六：希望之树](#模块六希望之树)
9. [模块七：护理宣教](#模块七护理宣教)
10. [模块八：数据驾驶舱（医护端）](#模块八数据驾驶舱医护端)
11. [模块九：预警与通知](#模块九预警与通知)
12. [模块十：机器人接入](#模块十机器人接入)
13. [模块十一：离线数据同步](#模块十一离线数据同步)
14. [模块十二：内容管理（运维端）](#模块十二内容管理运维端)
15. [三端接口速查表](#三端接口速查表)

---



## 统一规范

### 请求规范

- Content-Type: `application/json`
- 认证方式：请求头携带 `Authorization: Bearer <token>`
- 字符编码：UTF-8
- 时间格式：日期 `yyyy-MM-dd`，时间戳毫秒级 Unix timestamp

### 统一响应格式

所有接口均返回 `ApiResponse<T>` 结构：

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `code` | int | 200=成功，400=参数错误，401=未授权，403=无权限，404=资源不存在，500=服务端错误 |
| `message` | String | 描述信息，失败时为错误原因 |
| `data` | T | 业务数据，失败时为 null |

### 分页响应格式

```json
{
  "code": 200,
  "data": {
    "list": [],
    "total": 100,
    "page": 1,
    "pageSize": 20
  }
}
```

### 错误码说明

| HTTP 状态码 | code | 场景 |
|---|---|---|
| 200 | 200 | 成功 |
| 400 | 400 | 参数缺失或格式错误 |
| 401 | 401 | Token 缺失或已过期 |
| 403 | 403 | 权限不足（如护士访问运维接口） |
| 404 | 404 | 资源不存在 |
| 409 | 409 | 业务冲突（如重复打卡、非法阶段流转） |
| 500 | 500 | 服务端内部错误 |

---

## 数据字典

### ClinicalStage（临床阶段枚举）

| 枚举值 | 中文名 | 顺序 | 说明 |
|---|---|---|---|
| `ADMISSION` | 入仓期 | 1 | 患者初次进入隔离病房 |
| `PRETREATMENT` | 预处理期 | 2 | 化疗/放疗预处理阶段 |
| `TRANSPLANT` | 移植期 | 3 | 造血干细胞回输阶段 |
| `REBUILD` | 重建期 | 4 | 造血重建与免疫恢复 |
| `DISCHARGE` | 出仓期 | 5 | 达标出院阶段 |

> 阶段只能向相邻阶段流转（前进或后退一步），跨级流转返回 409。

### UserRole（用户角色枚举）

| 枚举值 | 中文名 | 说明 |
|---|---|---|
| `PATIENT` | 患者 | 机器人端，只能访问自己的数据 |
| `NURSE` | 护士 | 医护端，管理所在病区患者 |
| `DOCTOR` | 医生 | 医护端，查看所有数据 |
| `ADMIN` | 管理员 | 运维Web端，全量权限 |

### AlertLevel（预警级别枚举）

| 枚举值 | 中文名 | 触发条件 |
|---|---|---|
| `info` | 普通提示 | 心理能量低于 40 |
| `warning` | 警告 | 心理能量低于 30 / 危机关键词首次触发 |
| `critical` | 严重 | 30 分钟内未响应的 warning |

### GrowthSource（希望之树成长来源枚举）

| 枚举值 | 说明 | 默认经验值 |
|---|---|---|
| `check_in` | PRO 每日打卡 | +20 |
| `education` | 完成护理宣教 | +15 |
| `conversation` | 完成情绪对话 | +10 |
| `stage_advance` | 阶段流转奖励 | +50 |
| `meditation` | 冥想/呼吸练习 | +10 |

### PatientDto（患者基础信息）

```json
{
  "id": "String (UUID)",
  "name": "String",
  "stage": "ClinicalStage",
  "psychEnergy": "Integer (0-100)",
  "treeLevel": "Integer (1-7)",
  "admissionDate": "LocalDate (yyyy-MM-dd)",
  "roomNumber": "String | null",
  "createdAt": "String (ISO DateTime)",
  "updatedAt": "String (ISO DateTime)"
}
```

### 希望之树升级经验表

| 等级 | 升至下一级所需经验 |
|---|---|
| 1 → 2 | 100 |
| 2 → 3 | 250 |
| 3 → 4 | 450 |
| 4 → 5 | 700 |
| 5 → 6 | 1000 |
| 6 → 7 | 1400 |
| 7（满级）| nextLevelExp = 0 |

---

## 模块一：认证与权限

**Base URL：** `/api/auth`  **Tag：** `认证`

> 所有接口（除登录外）均需在请求头携带 `Authorization: Bearer <token>`。  
> Token 有效期 24 小时，可通过 `/api/auth/refresh` 延期。

---

### 1.0 创建医护/运维用户（注册）

```
POST /api/auth/register
```

**适用端：** Web运维端  
**权限：** `ADMIN`

> 用于为护士、医生、管理员创建账号；手机号用于登录和找回账号，需唯一。

**请求体：**

```json
{
  "username": "nurse_01",
  "password": "Xinya@2026",
  "displayName": "李护士",
  "role": "NURSE",
  "phone": "13800000001"
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `username` | String | ✅ | 登录用户名，系统内唯一 |
| `password` | String | ✅ | 登录密码（明文传输，HTTPS 保障） |
| `displayName` | String | ✅ | 展示姓名 |
| `role` | String | ✅ | `NURSE` / `DOCTOR` / `ADMIN` |
| `phone` | String | ✅ | 手机号，医护/运维登录用，系统内唯一 |

**响应：** `ApiResponse<UserDto>`

---

### 1.1 用户登录

```
POST /api/auth/login
```

**适用端：** 医护端、Web运维端

**请求体：**

```json
{
  "username": "nurse_01",
  "password": "Xinya@2026"
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `username` | String | ✅ | 登录用户名 |
| `password` | String | ✅ | 密码（明文传输，HTTPS保障） |

**响应：** `ApiResponse<LoginResponse>`

```json
{
  "code": 200,
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
    "expiresIn": 86400,
    "userId": "u-001",
    "username": "nurse_01",
    "phone": "13800000001",
    "role": "NURSE",
    "displayName": "李护士"
  }
}
```

---

### 1.2 手机号登录

```
POST /api/auth/login/phone
```

**适用端：** 医护端、Web运维端

> 与用户名登录等价，只是将登录标识从 `username` 换为 `phone`。  
> 当同一个人同时配置了用户名和手机号时，两种方式都可以登录。

**请求体：**

```json
{
  "phone": "13800000001",
  "password": "Xinya@2026"
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `phone` | String | ✅ | 手机号（医护/运维唯一） |
| `password` | String | ✅ | 密码 |

**响应：** `ApiResponse<LoginResponse>`（结构同用户名登录）

> 登录成功后，前端不需要关心是通过用户名还是手机号登录，只关心返回的 `token`、`role` 等信息。

---

### 1.3 机器人端鉴权（患者绑定）

```
POST /api/auth/robot/bind
```

**适用端：** 机器人端（设备首次绑定）

**请求体：**

```json
{
  "deviceId": "ROBOT-DEVICE-SN-001",
  "patientId": "p-001",
  "bindCode": "123456"
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `deviceId` | String | ✅ | 机器人设备序列号 |
| `patientId` | String | ✅ | 要绑定的患者 ID |
| `bindCode` | String | ✅ | 护士在 医护 端生成的 6 位绑定码 |

**响应：** `ApiResponse<RobotAuthResponse>`

```json
{
  "code": 200,
  "data": {
    "deviceToken": "device-jwt-token...",
    "expiresIn": 2592000,
    "patientId": "p-001",
    "patientName": "张小明"
  }
}
```

> `deviceToken` 有效期 30 天，机器人端后续请求均使用此 token。

---

### 1.4 生成机器人绑定码

```
POST /api/auth/robot/bind-code
```

**适用端：** 医护端（护士操作）

**请求体：**

```json
{
  "patientId": "p-001"
}
```

**响应：**

```json
{
  "code": 200,
  "data": {
    "bindCode": "739251",
    "expiresIn": 300
  }
}
```

> 绑定码 5 分钟有效，护士将此码告知患者/在机器人屏幕输入。

---

### 1.5 机器人解绑患者

```
POST /api/auth/robot/unbind
```

**适用端：** 医护端  

> 用于在患者出仓、更换病房或设备迁移时，将机器人与当前患者解绑。  
> 解绑后：  
> - 机器人不再持有关联 `patientId`；  
> - 原有 `deviceToken` 立即失效，后续请求需要重新绑定。

**请求体：**

```json
{
  "deviceId": "ROBOT-DEVICE-SN-001",
  "patientId": "p-001"
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `deviceId` | String | ✅ | 机器人设备序列号 |
| `patientId` | String | ❌ | 当前绑定的患者 ID，用于安全校验，医护 端可不传 |

**响应：**

```json
{
  "code": 200,
  "message": "解绑成功",
  "data": {
    "deviceId": "ROBOT-DEVICE-SN-001",
    "unbound": true
  }
}
```

> 若设备当前未绑定任何患者，返回 `unbound: false`，但仍视为成功（幂等）。

---

### 1.6 刷新 Token

```
POST /api/auth/refresh
```

**请求体：**

```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
}
```

**响应：** `ApiResponse<LoginResponse>`（结构同登录响应）

---

### 1.7 退出登录

```
POST /api/auth/logout
```

**请求头：** `Authorization: Bearer <token>`  
**响应：** `ApiResponse<null>`，`message: "已退出登录"`

---

### 1.8 用户自助注销账号（逻辑停用）

```
POST /api/auth/deactivate
```

**适用端：** 医护端、Web运维端  
**说明：** 当前登录用户将自己的账号标记为“已注销”，之后无法再登录，历史业务数据仍然保留。  

**请求头：** `Authorization: Bearer <token>`  

**响应：**

```json
{
  "code": 200,
  "data": null,
  "message": "账号已注销"
}
```

> 注销后，如果再次尝试登录，将返回“账号已禁用”提示。

---

### 1.9 用户自助删除账号（物理删除）

> 是否真正开放给正式用户由产品和合规决定，这里提供接口形状，默认仅测试环境使用。

```
DELETE /api/auth/account
```

**适用端：** 医护端、Web运维端  
**说明：** 当前登录用户彻底删除账号记录。  

**请求头：** `Authorization: Bearer <token>`  

**响应：**

```json
{
  "code": 200,
  "data": null,
  "message": "账号已删除"
}
```

> 若服务端判定该账号已有关键业务数据，可返回 `code=400`，提示“账号已有业务记录，请联系管理员注销账号而非删除”。

---

## 模块二：患者管理

**Base URL：** `/api/patients`  **Tag：** `患者管理`

---

### 2.1 创建患者档案

```
POST /api/patients
```

**适用端：** 医护端（护士/医生）  
**权限：** `NURSE` / `DOCTOR` / `ADMIN`

**请求体：**

```json
{
  "name": "张小明",
  "roomNumber": "A101",
  "admissionDate": "2026-03-01",
  "diagnosis": "急性髓系白血病",
  "age": 35,
  "gender": "MALE"
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `name` | String | ✅ | 患者姓名 |
| `roomNumber` | String | ❌ | 病房号 |
| `admissionDate` | LocalDate | ✅ | 入院日期 |
| `diagnosis` | String | ❌ | 诊断信息 |
| `age` | Integer | ❌ | 年龄 |
| `gender` | String | ❌ | `MALE` / `FEMALE` |

**响应：** `ApiResponse<PatientDto>`

> 创建成功后自动初始化：希望之树（Lv.1, exp=0）、心理能量（50）、临床阶段（ADMISSION）

---

### 2.2 查询单个患者

```
GET /api/patients/{id}
```

**适用端：** 机器人端、医护端、Web端  
**权限：** 患者只能查自己；护士/医生/管理员可查所有

| 参数 | 位置 | 类型 | 说明 |
|---|---|---|---|
| `id` | path | String | 患者 UUID |

**响应：** `ApiResponse<PatientDto>`，患者不存在返回 404

---

### 2.3 获取所有患者列表

```
GET /api/patients?page=1&pageSize=20&stage=TRANSPLANT&keyword=张
```

**适用端：** 医护端、Web端  
**权限：** `NURSE` / `DOCTOR` / `ADMIN`

| 参数 | 位置 | 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|---|
| `page` | query | int | ❌ | 1 | 页码 |
| `pageSize` | query | int | ❌ | 20 | 每页条数 |
| `stage` | query | String | ❌ | — | 按临床阶段筛选 |
| `keyword` | query | String | ❌ | — | 按姓名模糊搜索 |

**响应：** `ApiResponse<PageResult<PatientDto>>`

---

### 2.4 更新患者信息

```
PUT /api/patients/{id}
```

**适用端：** 医护端  
**权限：** `NURSE` / `DOCTOR` / `ADMIN`

**请求体：**

```json
{
  "name": "张小明",
  "roomNumber": "A102",
  "diagnosis": "更新诊断",
  "age": 35,
  "gender": "MALE"
}
```

> 所有字段均为可选，仅传入需修改的字段（PATCH语义，但使用PUT）

**响应：** `ApiResponse<PatientDto>`

---

### 2.5 删除患者档案

```
DELETE /api/patients/{id}
```

**适用端：** Web运维端  
**权限：** `ADMIN`

**响应：** `ApiResponse<null>`，`message: "患者档案已删除"`

---

### 2.6 获取患者心理能量趋势

```
GET /api/patients/{id}/energy-trend?days=14
```

**适用端：** 医护端、Web端

| 参数 | 位置 | 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|---|
| `id` | path | String | ✅ | — | 患者 UUID |
| `days` | query | int | ❌ | 7 | 查询最近N天 |

**响应：**

```json
{
  "code": 200,
  "data": {
    "patientId": "p-001",
    "trend": [
      { "date": "2026-02-24", "psychEnergy": 55 },
      { "date": "2026-02-25", "psychEnergy": 60 },
      { "date": "2026-02-26", "psychEnergy": 58 }
    ],
    "avgEnergy": 57.7,
    "minEnergy": 50,
    "maxEnergy": 68
  }
}
```

---

### 2.7 获取患者完整详情（聚合接口）

```
GET /api/patients/{id}/detail
```

**适用端：** 医护端（患者详情页一次性加载）

**响应：**

```json
{
  "code": 200,
  "data": {
    "patient": { "...PatientDto..." },
    "hopeTree": { "...HopeTreeDto..." },
    "latestProRecord": { "...最近一次打卡摘要..." },
    "todayCheckedIn": true,
    "pendingAlerts": 1
  }
}
```

---

## 模块三：临床路径

**Base URL：** `/api/clinical`  **Tag：** `临床路径`

---

### 3.1 获取患者当前临床阶段

```
GET /api/clinical/stage/{patientId}
```

**适用端：** 机器人端、医护端

| 参数 | 位置 | 类型 | 说明 |
|---|---|---|---|
| `patientId` | path | String | 患者 ID |

**响应：**

```json
{
  "code": 200,
  "data": {
    "stage": "PRETREATMENT",
    "stageName": "预处理期",
    "stageOrder": 2,
    "stageStartDate": "2026-02-20",
    "daysInStage": 10
  }
}
```

---

### 3.2 执行临床阶段流转

```
POST /api/clinical/transition
```

**适用端：** 医护端  
**权限：** `NURSE` / `DOCTOR`

**请求体：**

```json
{
  "patientId": "p-001",
  "targetStage": "TRANSPLANT",
  "operatorNote": "预处理方案顺利完成，今日回输"
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `patientId` | String | ✅ | 患者 ID |
| `targetStage` | String | ✅ | 目标阶段 |
| `operatorNote` | String | ❌ | 操作备注 |

**响应：** `ApiResponse<PatientDto>`，流转非法（跨级）时返回 409

> 阶段流转成功后自动触发希望之树阶段奖励（+50 经验值）

---

### 3.3 获取患者阶段流转历史

```
GET /api/clinical/history/{patientId}
```

**适用端：** 医护端、Web端

**响应：**

```json
{
  "code": 200,
  "data": [
    {
      "id": 1,
      "fromStage": "ADMISSION",
      "toStage": "PRETREATMENT",
      "transitionDate": "2026-02-20",
      "operatorId": "u-001",
      "operatorNote": "入仓适应完成",
      "daysInStage": 5
    }
  ]
}
```

---

## 模块四：智能体对话

**Base URL：** `/api/agent`  **Tag：** `智能体对话`

> 后端为对话代理层，实际 AI 推理由独立 Python 智能体处理（专人对接）。  
> `crisisAlert=true` 时后端自动创建预警记录并推送至医护端。

---

### 4.1 发送对话消息

```
POST /api/agent/chat
```

**适用端：** 机器人端

**请求体：**

```json
{
  "patientId": "p-001",
  "agentType": "psych",
  "message": "今天感觉很难受，什么都不想做",
  "sessionId": "可选，不传则自动生成",
  "clientTimestamp": 1709366400000
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `patientId` | String | ✅ | 患者 ID |
| `agentType` | String | ✅ | `psych`（小芽-心理）或 `nurse`（小护士-护理） |
| `message` | String | ✅ | 用户消息内容 |
| `sessionId` | String | ❌ | 会话 ID，不传则自动生成 UUID |
| `clientTimestamp` | Long | ❌ | 客户端消息时间戳（离线补传用） |

**响应：** `ApiResponse<AgentChatResponse>`

```json
{
  "code": 200,
  "data": {
    "reply": "我听到你说感觉很难受，能告诉我今天发生了什么吗？",
    "sessionId": "session-uuid-xxx",
    "psychEnergyDelta": 5,
    "recommendedQuestions": [
      "今天身体上有什么不舒服吗？",
      "想做一个放松呼吸练习吗？"
    ],
    "crisisAlert": false,
    "hopeTreeExpDelta": 10
  }
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `reply` | String | AI 回复内容 |
| `sessionId` | String | 本次会话 ID |
| `psychEnergyDelta` | Integer | 本次对话心理能量变化值（正/负/0） |
| `recommendedQuestions` | String[] | 推荐的后续提问 |
| `crisisAlert` | Boolean | 是否检测到危机信号 |
| `hopeTreeExpDelta` | Integer | 本次对话获得的希望之树经验值 |

---

### 4.2 获取推荐问题列表

```
GET /api/agent/recommendations?patientId=p-001&agentType=psych
```

**适用端：** 机器人端（快捷提问按钮）

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| `patientId` | query | String | ✅ | 患者 ID |
| `agentType` | query | String | ✅ | `psych` 或 `nurse` |

**响应：**

```json
{
  "code": 200,
  "data": {
    "questions": [
      "今天心情怎么样？",
      "身体有什么不舒服吗？",
      "想聊聊对移植的担心吗？"
    ]
  }
}
```

---

### 4.3 获取对话历史记录

```
GET /api/agent/history?patientId=p-001&agentType=psych&page=1&pageSize=20
```

**适用端：** 机器人端（历史记录页）、医护端（查看患者对话）

| 参数 | 位置 | 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|---|
| `patientId` | query | String | ✅ | — | 患者 ID |
| `agentType` | query | String | ❌ | — | `psych` 或 `nurse`，不传返回全部 |
| `sessionId` | query | String | ❌ | — | 按会话筛选 |
| `page` | query | int | ❌ | 1 | 页码 |
| `pageSize` | query | int | ❌ | 20 | 每页条数 |

**响应：** `ApiResponse<PageResult<ConversationItemDto>>`

```json
{
  "code": 200,
  "data": {
    "list": [
      {
        "id": 1,
        "sessionId": "session-uuid-xxx",
        "agentType": "psych",
        "message": "今天感觉很难受",
        "isFromUser": true,
        "psychEnergyDelta": 0,
        "crisisAlert": false,
        "createdAt": "2026-03-02T09:30:00"
      },
      {
        "id": 2,
        "sessionId": "session-uuid-xxx",
        "agentType": "psych",
        "message": "我听到你说感觉很难受...",
        "isFromUser": false,
        "psychEnergyDelta": 5,
        "crisisAlert": false,
        "createdAt": "2026-03-02T09:30:02"
      }
    ],
    "total": 48,
    "page": 1,
    "pageSize": 20
  }
}
```

---

### 4.4 主动推送护理宣教内容（智能体触发）

```
POST /api/agent/nurse/push
```

**适用端：** 机器人端 / AI智能体对接层（根据阶段和症状主动推送）

**请求体：**

```json
{
  "patientId": "p-001",
  "triggerType": "symptom",
  "symptomKeyword": "恶心",
  "currentStage": "PRETREATMENT"
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `patientId` | String | ✅ | 患者 ID |
| `triggerType` | String | ✅ | `symptom`（症状触发）/ `stage`（阶段触发）/ `scheduled`（定时触发） |
| `symptomKeyword` | String | ❌ | 触发的症状关键词 |
| `currentStage` | String | ❌ | 当前临床阶段 |

**响应：**

```json
{
  "code": 200,
  "data": {
    "recommendedContents": [
      {
        "contentId": "ec-003",
        "title": "预处理期恶心呕吐应对指南",
        "contentType": "video",
        "thumbnailUrl": "https://...",
        "durationSeconds": 360,
        "relevanceScore": 0.95
      }
    ]
  }
}
```

---

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

---

#### H. 接口汇总速查

| 接口 | 方法 | 路径 | 触发方式 |
|---|---|---|---|
| 小芽·对话推理 | POST | `/v1/psych/chat` | 用户发消息 |
| 小芽·推荐提问 | POST | `/v1/psych/recommendations` | 客户端请求推荐按钮 |
| 小护士·对话推理 | POST | `/v1/nurse/chat` | 用户发消息 |
| 小护士·推荐提问 | POST | `/v1/nurse/recommendations` | 客户端请求推荐按钮 |
| 小护士·症状触发推荐 | POST | `/v1/nurse/symptom-trigger` | PRO打卡完成 / 对话检测到症状 |
| 小护士·血象提醒计划 | POST | `/v1/nurse/reminder-plan` | 血象录入 / 每日定时 |

---

#### I. 本地联调配置

Agent 同事在本地启动服务后，后端修改 `application.yml`：

```yaml
xinya:
  ai:
    enabled: true
    base-url: http://localhost:8000   # 改为你们的服务地址
    api-key: your-api-key             # 双方约定的接口密钥
    timeout-seconds: 30
    psych-path: /v1/psych/chat
    nurse-path: /v1/nurse/chat
    symptom-trigger-path: /v1/nurse/symptom-trigger
    reminder-plan-path: /v1/nurse/reminder-plan
```

---

## 模块五：PRO 每日打卡

**Base URL：** `/api/pro`  **Tag：** `PRO数据采集`

---

### 5.1 获取今日打卡问卷

```
GET /api/pro/questions?patientId=p-001
```

**适用端：** 机器人端（打卡前获取当天问卷）

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| `patientId` | query | String | ✅ | 患者 ID（根据阶段返回对应问卷） |

**响应：**

```json
{
  "code": 200,
  "data": {
    "checkedInToday": false,
    "questions": [
      {
        "id": "q_nausea",
        "title": "今天有没有恶心感？",
        "type": "single_choice",
        "options": [
          { "value": "none", "label": "没有", "score": 0 },
          { "value": "mild", "label": "轻度", "score": 1 },
          { "value": "moderate", "label": "中度", "score": 2 },
          { "value": "severe", "label": "重度", "score": 3 }
        ]
      },
      {
        "id": "q_mood",
        "title": "今天整体心情如何？",
        "type": "scale",
        "min": 1,
        "max": 10,
        "minLabel": "非常糟糕",
        "maxLabel": "非常好"
      }
    ]
  }
}
```

---

### 5.2 提交每日打卡

```
POST /api/pro/submit
```

**适用端：** 机器人端

**请求体：**

```json
{
  "patientId": "p-001",
  "recordDate": "2026-03-02",
  "answers": [
    { "questionId": "q_nausea", "answer": "轻度", "score": 1 },
    { "questionId": "q_mood", "answer": "7", "score": 7 }
  ],
  "clientTimestamp": 1709366400000
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `patientId` | String | ✅ | 患者 ID |
| `recordDate` | LocalDate | ❌ | 打卡日期，不传默认今天 |
| `answers` | ProAnswer[] | ❌ | 答题列表 |
| `clientTimestamp` | Long | ❌ | 离线补传时的实际时间戳 |

**打卡成功后自动触发：**
- 患者心理能量 **+10**
- 希望之树经验 **+20**（`growthSource = check_in`）
- 症状异常时（总分 > 阈值）创建 `info` 级预警

**响应：**

```json
{
  "code": 200,
  "message": "打卡提交成功",
  "data": {
    "success": true,
    "psychEnergyDelta": 10,
    "hopeTreeExpDelta": 20,
    "totalScore": 8,
    "alertCreated": false,
    "message": "打卡成功！您的希望之树获得了成长能量。"
  }
}
```

> 重复打卡（同一天已打卡）返回 409，`message: "今日已打卡"`

---

### 5.3 查询打卡历史记录

```
GET /api/pro/history?patientId=p-001&startDate=2026-02-01&endDate=2026-03-02&page=1&pageSize=30
```

**适用端：** 医护端、机器人端（历史记录）

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| `patientId` | query | String | ✅ | 患者 ID |
| `startDate` | query | LocalDate | ❌ | 开始日期 |
| `endDate` | query | LocalDate | ❌ | 结束日期 |
| `page` | query | int | ❌ | 页码，默认 1 |
| `pageSize` | query | int | ❌ | 每页条数，默认 30 |

**响应：**

```json
{
  "code": 200,
  "data": {
    "list": [
      {
        "recordDate": "2026-03-02",
        "totalScore": 8,
        "answers": [
          { "questionId": "q_nausea", "questionTitle": "今天有没有恶心感？", "answer": "轻度", "score": 1 }
        ],
        "createdAt": "2026-03-02T09:00:00"
      }
    ],
    "total": 20,
    "continuousCheckInDays": 7
  }
}
```

---

### 5.4 查询患者症状趋势（聚合）

```
GET /api/pro/symptom-trend?patientId=p-001&questionId=q_nausea&days=14
```

**适用端：** 医护端（症状走势图）

**响应：**

```json
{
  "code": 200,
  "data": {
    "questionTitle": "恶心感",
    "trend": [
      { "date": "2026-02-17", "score": 2 },
      { "date": "2026-02-18", "score": 3 },
      { "date": "2026-02-19", "score": 1 }
    ],
    "avgScore": 1.8,
    "peakScore": 3,
    "peakDate": "2026-02-18"
  }
}
```

---

## 模块六：希望之树

**Base URL：** `/api/hopetree`  **Tag：** `希望之树`

> 共 7 个等级，升级所需经验：100 / 250 / 450 / 700 / 1000 / 1400。  
> 满级（等级 7）时 `nextLevelExp=0`。

---

### 6.1 获取希望之树状态

```
GET /api/hopetree/{patientId}
```

**适用端：** 机器人端、医护端

| 参数 | 位置 | 类型 | 说明 |
|---|---|---|---|
| `patientId` | path | String | 患者 ID |

**响应：** `ApiResponse<HopeTreeDto>`

```json
{
  "code": 200,
  "data": {
    "currentLevel": 3,
    "currentExp": 120,
    "nextLevelExp": 450,
    "totalGrowthDays": 28,
    "levelName": "茁壮成长",
    "levelImageUrl": "https://.../tree_level3.png",
    "todayExpGained": 40,
    "lastGrowthTime": "2026-03-02T18:00:00"
  }
}
```

---

### 6.2 触发希望之树成长

```
POST /api/hopetree/grow
```

**适用端：** 机器人端（由其他行为自动触发，也可手动调用）

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
| `growthSource` | String | ✅ | 来源枚举（见 GrowthSource） |
| `expAmount` | Integer | ✅ | 增加的经验值 |

**响应：**

```json
{
  "code": 200,
  "data": {
    "success": true,
    "newLevel": 3,
    "newExp": 140,
    "levelUp": false,
    "levelUpAnimation": null
  }
}
```

> `levelUp=true` 时 `levelUpAnimation` 返回动画触发指令（如 `"LEVEL_UP_TO_4"`）

---

### 6.3 获取成长历史记录

```
GET /api/hopetree/{patientId}/history?page=1&pageSize=20
```

**适用端：** 机器人端（成长日记）、医护端

**响应：** `ApiResponse<PageResult<GrowthHistoryItem>>`

```json
{
  "code": 200,
  "data": {
    "list": [
      {
        "id": 1,
        "growthSource": "check_in",
        "growthSourceName": "每日打卡",
        "expAmount": 20,
        "levelBefore": 3,
        "levelAfter": 3,
        "levelUp": false,
        "createdAt": "2026-03-02T09:05:00"
      }
    ],
    "total": 45
  }
}
```

---

## 模块七：护理宣教

**Base URL：** `/api/education`  **Tag：** `护理宣教`

---

### 7.1 获取宣教内容列表

```
GET /api/education/contents?stage=PRETREATMENT&contentType=video&page=1&pageSize=20
```

**适用端：** 机器人端、医护端、Web端

| 参数 | 位置 | 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|---|
| `stage` | query | String | ❌ | — | 按临床阶段筛选 |
| `category` | query | String | ❌ | — | 按分类筛选 |
| `contentType` | query | String | ❌ | — | `video` / `article` |
| `keyword` | query | String | ❌ | — | 标题关键词搜索 |
| `page` | query | int | ❌ | 1 | 页码 |
| `pageSize` | query | int | ❌ | 20 | 每页条数 |

**响应：**

```json
{
  "code": 200,
  "data": {
    "list": [
      {
        "id": "ec-001",
        "title": "移植前你需要了解的事",
        "stage": "PRETREATMENT",
        "category": "预处理期",
        "description": "详细介绍造血干细胞移植前的准备工作",
        "contentType": "video",
        "durationSeconds": 480,
        "thumbnailUrl": "https://...",
        "mediaUrl": "https://...",
        "tags": ["移植", "准备"],
        "sortOrder": 1,
        "isActive": true
      }
    ],
    "total": 3
  }
}
```

---

### 7.2 获取宣教内容详情

```
GET /api/education/contents/{id}
```

**适用端：** 机器人端、医护端

**响应：** `ApiResponse<EducationContentDetailDto>`（含完整正文）

---

### 7.3 记录宣教内容观看进度

```
POST /api/education/progress
```

**适用端：** 机器人端

**请求体：**

```json
{
  "patientId": "p-001",
  "contentId": "ec-001",
  "watchedSeconds": 300,
  "completed": false,
  "clientTimestamp": 1709366400000
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `patientId` | String | ✅ | 患者 ID |
| `contentId` | String | ✅ | 内容 ID |
| `watchedSeconds` | Integer | ✅ | 已观看秒数 |
| `completed` | Boolean | ❌ | 是否看完，默认 false |

**响应：**

```json
{
  "code": 200,
  "data": {
    "hopeTreeExpDelta": 15,
    "completionRate": 0.625
  }
}
```

> 首次标记 `completed=true` 时自动触发希望之树 +15 经验（`growthSource=education`）

---

### 7.4 获取患者宣教学习进度

```
GET /api/education/progress/{patientId}
```

**适用端：** 医护端、机器人端

**响应：**

```json
{
  "code": 200,
  "data": {
    "totalContents": 15,
    "completedContents": 8,
    "completionRate": 0.53,
    "totalWatchedSeconds": 3600,
    "progressList": [
      {
        "contentId": "ec-001",
        "contentTitle": "移植前你需要了解的事",
        "watchedSeconds": 480,
        "completed": true,
        "lastWatchedAt": "2026-03-01T15:30:00"
      }
    ]
  }
}
```

---

### 7.5 新增宣教内容（运维端）

```
POST /api/education/contents
```

**适用端：** Web运维端  
**权限：** `ADMIN`

**请求体：**

```json
{
  "title": "移植后饮食注意事项",
  "stage": "REBUILD",
  "category": "重建期",
  "description": "介绍免疫重建期的饮食管理要点",
  "contentType": "video",
  "durationSeconds": 360,
  "thumbnailUrl": "https://...",
  "mediaUrl": "https://...",
  "tags": ["饮食", "重建"],
  "sortOrder": 5
}
```

**响应：** `ApiResponse<EducationContentDto>`

---

### 7.6 更新宣教内容（运维端）

```
PUT /api/education/contents/{id}
```

**适用端：** Web运维端  
**权限：** `ADMIN`

---

### 7.7 删除/下架宣教内容（运维端）

```
DELETE /api/education/contents/{id}
```

> 逻辑删除，将 `isActive` 置为 false。  
**适用端：** Web运维端  
**权限：** `ADMIN`

---

## 模块八：数据驾驶舱（医护端）

**Base URL：** `/api/dashboard`  **Tag：** `数据驾驶舱`

---

### 8.1 获取驾驶舱概览数据

```
GET /api/dashboard/overview
```

**适用端：** 医护端、Web端  
**权限：** `NURSE` / `DOCTOR` / `ADMIN`

**响应：** `ApiResponse<DashboardDto>`

```json
{
  "code": 200,
  "data": {
    "totalPatients": 12,
    "patientsByStage": {
      "ADMISSION": 2,
      "PRETREATMENT": 3,
      "TRANSPLANT": 4,
      "REBUILD": 2,
      "DISCHARGE": 1
    },
    "avgPsychEnergy": 62.5,
    "lowEnergyCount": 2,
    "todayCheckInCount": 8,
    "checkInRate": 0.67,
    "symptomTrends": [
      {
        "symptom": "恶心",
        "lastWeek": 6,
        "thisWeek": 4,
        "changePercent": -33.3,
        "trend": "DOWN"
      }
    ],
    "alerts": [
      {
        "id": "alert-001",
        "level": "warning",
        "message": "患者 张小明 心理能量低于30",
        "patientId": "p-003",
        "patientName": "张小明",
        "createdAt": "2026-03-02T08:30:00",
        "resolved": false
      }
    ],
    "learningStats": {
      "avgCompletionRate": 0.72,
      "avgWatchTimeMinutes": 8.5,
      "engagementRate": 0.68
    }
  }
}
```

---

### 8.2 获取患者心理状态分布

```
GET /api/dashboard/psych-distribution
```

**适用端：** 医护端、Web端

**响应：**

```json
{
  "code": 200,
  "data": {
    "healthy": { "count": 7, "range": "60-100", "percent": 0.58 },
    "mild": { "count": 3, "range": "40-59", "percent": 0.25 },
    "warning": { "count": 2, "range": "0-39", "percent": 0.17 }
  }
}
```

---

### 8.3 获取病区症状热力图数据

```
GET /api/dashboard/symptom-heatmap?days=7
```

**适用端：** Web端（运营分析）

**响应：**

```json
{
  "code": 200,
  "data": {
    "dates": ["2026-02-24", "2026-02-25", "..."],
    "symptoms": [
      {
        "name": "恶心",
        "scores": [6, 5, 4, 7, 3, 4, 4]
      },
      {
        "name": "乏力",
        "scores": [8, 7, 7, 9, 6, 6, 5]
      }
    ]
  }
}
```

---

### 8.4 生成患者康复报告

```
GET /api/dashboard/patient-report/{patientId}
```

**适用端：** Web端、医护端  
**权限：** `DOCTOR` / `ADMIN`

**响应：**

```json
{
  "code": 200,
  "data": {
    "patientId": "p-001",
    "patientName": "张小明",
    "admissionDate": "2026-02-15",
    "currentStage": "REBUILD",
    "totalDays": 15,
    "checkInDays": 13,
    "checkInRate": 0.87,
    "psychEnergyProgress": {
      "initial": 50,
      "current": 72,
      "peak": 80,
      "avg": 64.3
    },
    "hopeTreeProgress": {
      "level": 4,
      "totalExpGained": 650
    },
    "educationProgress": {
      "completionRate": 0.75,
      "totalWatchMinutes": 120
    },
    "symptomSummary": [
      { "symptom": "恶心", "avgScore": 1.8, "trend": "IMPROVING" }
    ],
    "generatedAt": "2026-03-02T10:00:00"
  }
}
```

---

## 模块九：预警与通知

**Base URL：** `/api/alerts`  **Tag：** `预警通知`

---

### 9.1 获取预警列表

```
GET /api/alerts?resolved=false&level=warning&page=1&pageSize=20
```

**适用端：** 医护端、Web端  
**权限：** `NURSE` / `DOCTOR` / `ADMIN`

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| `resolved` | query | Boolean | ❌ | `false`=仅未处理，`true`=仅已处理，不传=全部 |
| `level` | query | String | ❌ | `info` / `warning` / `critical` |
| `patientId` | query | String | ❌ | 按患者筛选 |
| `page` | query | int | ❌ | 页码，默认 1 |
| `pageSize` | query | int | ❌ | 每页条数，默认 20 |

**响应：**

```json
{
  "code": 200,
  "data": {
    "list": [
      {
        "id": "alert-001",
        "patientId": "p-003",
        "patientName": "李小华",
        "alertType": "crisis",
        "level": "warning",
        "message": "患者发送了含危机信号的消息：「不想坚持了」",
        "triggerMessage": "不想坚持了",
        "resolved": false,
        "resolvedBy": null,
        "resolvedNote": null,
        "resolvedAt": null,
        "createdAt": "2026-03-02T10:30:00"
      }
    ],
    "total": 3,
    "unresolvedCount": 3
  }
}
```

---

### 9.2 处理/关闭预警

```
PUT /api/alerts/{id}/resolve
```

**适用端：** 医护端  
**权限：** `NURSE` / `DOCTOR`

**请求体：**

```json
{
  "resolvedNote": "已与患者进行面谈，情绪稳定"
}
```

**响应：** `ApiResponse<AlertDto>`

---

### 9.3 手动创建预警（医护）

```
POST /api/alerts
```

**适用端：** 医护端  
**权限：** `NURSE` / `DOCTOR`

**请求体：**

```json
{
  "patientId": "p-001",
  "alertType": "manual",
  "level": "info",
  "message": "患者今日情绪较低落，建议关注"
}
```

**响应：** `ApiResponse<AlertDto>`

---

## 模块十：机器人接入

**Base URL：** `/api/robot`  **Tag：** `机器人对接`

---

### 10.1 接收机器人推送数据

```
POST /api/robot/data
```

**适用端：** 机器人端（传感器/环境数据上报）

**请求体：**

```json
{
  "patientId": "p-001",
  "deviceId": "ROBOT-DEVICE-SN-001",
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
| `deviceId` | String | ✅ | 机器人设备序列号 |
| `dataType` | String | ✅ | `vital_signs` / `activity` / `voice` / `environment` |
| `payload` | Map | ✅ | 数据内容（自由结构） |
| `timestamp` | Long | ✅ | 数据采集时间戳（毫秒） |

**响应：**

```json
{
  "code": 200,
  "data": { "received": true, "timestamp": 1709366400000 }
}
```

---

### 10.2 机器人心跳检测

```
POST /api/robot/heartbeat
```

**适用端：** 机器人端（每60秒上报一次）

**请求体：**

```json
{
  "deviceId": "ROBOT-DEVICE-SN-001",
  "patientId": "p-001",
  "networkStatus": "WIFI",
  "batteryLevel": 85,
  "appVersion": "1.2.0"
}
```

**响应：**

```json
{
  "code": 200,
  "data": {
    "serverTime": 1709366400000,
    "pendingPushMessages": 0
  }
}
```

---

### 10.3 查询机器人设备状态

```
GET /api/robot/devices?patientId=p-001
```

**适用端：** 医护端、Web端

**响应：**

```json
{
  "code": 200,
  "data": {
    "deviceId": "ROBOT-DEVICE-SN-001",
    "patientId": "p-001",
    "onlineStatus": "ONLINE",
    "lastHeartbeatAt": "2026-03-02T10:55:00",
    "networkStatus": "WIFI",
    "batteryLevel": 85,
    "appVersion": "1.2.0"
  }
}
```

---

## 模块十一：离线数据同步

**Base URL：** `/api/sync`  **Tag：** `离线同步`

---

### 11.1 批量同步上传

```
POST /api/sync/batch
```

**适用端：** 机器人端（Android SyncManager 调用）  
**优先级：** ⭐⭐⭐ 高优先级，Android 端已对接，后端待实现

**请求体：**

```json
{
  "deviceId": "ROBOT-DEVICE-SN-001",
  "patientId": "p-001",
  "items": [
    {
      "clientId": "local-uuid-001",
      "type": "pro_submit",
      "payload": {
        "patientId": "p-001",
        "recordDate": "2026-03-01",
        "answers": [
          { "questionId": "q_nausea", "answer": "轻度", "score": 1 }
        ],
        "clientTimestamp": 1709280000000
      },
      "createdAt": 1709280000000,
      "retryCount": 0
    },
    {
      "clientId": "local-uuid-002",
      "type": "agent_chat",
      "payload": {
        "patientId": "p-001",
        "agentType": "psych",
        "message": "今天感觉好多了",
        "sessionId": "session-offline-xxx",
        "clientTimestamp": 1709283600000
      },
      "createdAt": 1709283600000,
      "retryCount": 0
    }
  ]
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `deviceId` | String | ✅ | 机器人设备序列号 |
| `patientId` | String | ✅ | 患者 ID |
| `items` | SyncItem[] | ✅ | 同步数据列表 |
| `items[].clientId` | String | ✅ | 客户端唯一ID（用于幂等去重） |
| `items[].type` | String | ✅ | 数据类型（见下表） |
| `items[].payload` | Object | ✅ | 对应接口的完整请求体 |
| `items[].createdAt` | Long | ✅ | 数据在客户端生成的时间戳 |
| `items[].retryCount` | Integer | ❌ | 本次重试次数，默认 0 |

**支持的同步数据类型：**

| type | 对应接口 | 说明 |
|---|---|---|
| `pro_submit` | POST /api/pro/submit | PRO每日打卡 |
| `agent_chat` | POST /api/agent/chat | 对话消息 |
| `hopetree_grow` | POST /api/hopetree/grow | 希望之树成长 |
| `education_progress` | POST /api/education/progress | 宣教观看进度 |
| `robot_data` | POST /api/robot/data | 传感器数据 |

**响应：**

```json
{
  "code": 200,
  "data": {
    "totalItems": 2,
    "succeeded": [
      { "clientId": "local-uuid-001", "serverId": "server-pro-123" }
    ],
    "failed": [
      {
        "clientId": "local-uuid-002",
        "errorCode": 409,
        "errorMessage": "今日已打卡"
      }
    ],
    "syncedAt": 1709366400000
  }
}
```

> **幂等保障：** 相同 `clientId` 重复提交，后端返回首次成功结果，不重复处理。

---

### 11.2 查询同步状态

```
GET /api/sync/status?patientId=p-001&deviceId=ROBOT-DEVICE-SN-001
```

**适用端：** 机器人端（判断是否有未同步数据）

**响应：**

```json
{
  "code": 200,
  "data": {
    "lastSyncAt": "2026-03-02T08:00:00",
    "pendingItemsOnServer": 0
  }
}
```

---

## 模块十二：内容管理（运维端）

**Base URL：** `/api/admin`  **Tag：** `运维管理`

**权限：** 以下所有接口均需 `ADMIN` 角色

---

### 12.1 用户管理 - 获取用户列表

```
GET /api/admin/users?role=NURSE&page=1&pageSize=20
```

**响应：** `ApiResponse<PageResult<UserDto>>`

```json
{
  "code": 200,
  "data": {
    "list": [
      {
        "id": "u-001",
        "username": "nurse_01",
        "displayName": "李护士",
        "role": "NURSE",
        "enabled": true,
        "lastLoginAt": "2026-03-02T08:00:00",
        "createdAt": "2026-01-01T00:00:00"
      }
    ],
    "total": 5
  }
}
```

---

### 12.2 用户管理 - 创建用户

```
POST /api/admin/users
```

**请求体：**

```json
{
  "username": "doctor_02",
  "password": "Xinya@2026",
  "displayName": "王医生",
  "role": "DOCTOR"
}
```

**响应：** `ApiResponse<UserDto>`

---

### 12.3 用户管理 - 修改 / 注销 / 删除用户

#### 12.3.1 修改用户

```
PUT /api/admin/users/{id}
```

**说明：** 修改医护 / 运维账号的基本信息（展示名、角色、手机号、启用状态）。  

**请求体：**

```json
{
  "displayName": "王医生（夜班）",
  "role": "DOCTOR",
  "phone": "13800000002",
  "enabled": true
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `displayName` | String | ✅ | 展示名 |
| `role` | String | ✅ | `NURSE` / `DOCTOR` / `ADMIN` |
| `phone` | String | ❌ | 手机号，若不为空需在全局唯一 |
| `enabled` | Boolean | ❌ | 是否启用；为空则不修改 |

**响应：** `ApiResponse<UserDto>`

---

#### 12.3.2 注销用户（逻辑停用）

```
POST /api/admin/users/{id}/deactivate
```

**说明：** 管理员将指定账号标记为“已注销”，后续该用户无法再登录，但历史业务数据和审计日志仍然保留。  

**响应：**

```json
{
  "code": 200,
  "data": null,
  "message": "用户已注销"
}
```

---

#### 12.3.3 删除用户（物理删除）

```
DELETE /api/admin/users/{id}
```

**说明：** 物理删除用户记录，一般只用于测试账号或尚无关键业务数据的账号。  

**响应：**

```json
{
  "code": 200,
  "data": null,
  "message": "用户已删除"
}
```

> 若后端判定该用户已有重要业务数据，可返回 `code=400` 并提示“该用户已有业务记录，请先选择注销而非删除”。

---

### 12.4 系统配置 - 获取 PRO 问卷列表

```
GET /api/admin/pro-questions?stage=PRETREATMENT
```

**响应：** 返回按阶段分组的问卷题目配置

---

### 12.5 系统配置 - 危机关键词管理

```
GET /api/admin/crisis-keywords
POST /api/admin/crisis-keywords
DELETE /api/admin/crisis-keywords/{id}
```

> 管理系统内置的危机检测关键词列表

---

### 12.6 审计日志查询

```
GET /api/admin/audit-logs?userId=u-001&action=STAGE_TRANSITION&startDate=2026-03-01&page=1&pageSize=20
```

**响应：** 返回操作人、操作类型、操作对象、时间等日志记录

---

## 三端接口速查表

### 机器人端（患者端）

| 功能 | 方法 | 路径 | 状态 |
|---|---|---|---|
| 设备绑定认证 | POST | `/api/auth/robot/bind` | ✅ |
| 获取患者信息 | GET | `/api/patients/{id}` | ✅ |
| 发送心理对话 | POST | `/api/agent/chat` | ✅ |
| 获取推荐问题 | GET | `/api/agent/recommendations` | ✅ |
| 查看对话历史 | GET | `/api/agent/history` | ✅ |
| 获取今日打卡问卷 | GET | `/api/pro/questions` | ✅ |
| 提交每日打卡 | POST | `/api/pro/submit` | ✅ |
| 查看打卡历史 | GET | `/api/pro/history` | ✅ |
| 获取希望之树状态 | GET | `/api/hopetree/{patientId}` | ✅ |
| 触发树成长 | POST | `/api/hopetree/grow` | ✅ |
| 查看成长历史 | GET | `/api/hopetree/{patientId}/history` | ✅ |
| 获取宣教内容列表 | GET | `/api/education/contents` | ✅ |
| 记录观看进度 | POST | `/api/education/progress` | ✅ |
| 获取当前临床阶段 | GET | `/api/clinical/stage/{patientId}` | ✅ |
| 机器人心跳 | POST | `/api/robot/heartbeat` | ✅ |
| 上报传感器数据 | POST | `/api/robot/data` | ✅ |
| **离线批量同步** | **POST** | **`/api/sync/batch`** | ❌ **后端待实现** |

### 医护端（医护端）

| 功能 | 方法 | 路径 | 状态 |
|---|---|---|---|
| 用户登录 | POST | `/api/auth/login` | ✅ |
| 生成设备绑定码 | POST | `/api/auth/robot/bind-code` | ✅ |
| 创建患者档案 | POST | `/api/patients` | ✅ |
| 获取患者列表 | GET | `/api/patients` | ✅ |
| 获取患者详情（聚合） | GET | `/api/patients/{id}/detail` | ✅ |
| 更新患者信息 | PUT | `/api/patients/{id}` | ✅ |
| 查看能量趋势 | GET | `/api/patients/{id}/energy-trend` | ✅ |
| 执行阶段流转 | POST | `/api/clinical/transition` | ✅ |
| 查看阶段历史 | GET | `/api/clinical/history/{patientId}` | ✅ |
| 查看打卡历史 | GET | `/api/pro/history` | ✅ |
| 查看症状趋势 | GET | `/api/pro/symptom-trend` | ✅ |
| 数据驾驶舱概览 | GET | `/api/dashboard/overview` | ✅ |
| 心理状态分布 | GET | `/api/dashboard/psych-distribution` | ✅ |
| 生成患者报告 | GET | `/api/dashboard/patient-report/{id}` | ✅ |
| 获取预警列表 | GET | `/api/alerts` | ✅ |
| 处理预警 | PUT | `/api/alerts/{id}/resolve` | ✅ |
| 手动创建预警 | POST | `/api/alerts` | ✅ |
| 查看机器人状态 | GET | `/api/robot/devices` | ✅ |
| 查看宣教进度 | GET | `/api/education/progress/{patientId}` | ✅ |

### Web运维端

| 功能 | 方法 | 路径 | 状态 |
|---|---|---|---|
| 管理员登录 | POST | `/api/auth/login` | ✅ |
| 患者批量查询 | GET | `/api/patients` | ✅ |
| 删除患者档案 | DELETE | `/api/patients/{id}` | ✅ |
| 症状热力图 | GET | `/api/dashboard/symptom-heatmap` | ✅ |
| 生成患者报告 | GET | `/api/dashboard/patient-report/{id}` | ✅ |
| 新增宣教内容 | POST | `/api/education/contents` | ✅ |
| 更新宣教内容 | PUT | `/api/education/contents/{id}` | ✅ |
| 下架宣教内容 | DELETE | `/api/education/contents/{id}` | ✅ |
| 获取用户列表 | GET | `/api/admin/users` | ✅ |
| 创建用户 | POST | `/api/admin/users` | ✅ |
| 重置密码 | PUT | `/api/admin/users/{id}/reset-password` | ✅ |
| 危机关键词管理 | GET/POST/DELETE | `/api/admin/crisis-keywords` | ✅ |
| 审计日志查询 | GET | `/api/admin/audit-logs` | ✅ |

---

> **⚠️ 待实现接口（高优先级）**
>
> | 方法 | 路径 | 说明 |
> |---|---|---|
> | POST | `/api/sync/batch` | 离线批量同步（Android 端已接入，后端核心缺口） |
>
> **待暴露接口（功能已有，需补充 Controller）**
>
> | 方法 | 路径 | 说明 |
> |---|---|---|
> | GET | `/api/pro/history` | PRO 打卡历史查询 |
> | GET | `/api/agent/history` | 对话历史查询 |
> | PUT | `/api/patients/{id}` | 患者信息更新 |
> | DELETE | `/api/patients/{id}` | 患者删除 |
> | GET | `/api/patients/{id}/energy-trend` | 心理能量趋势 |
> | GET | `/api/patients/{id}/detail` | 患者聚合详情 |
