# xinya-ops 后端设计与接口文档

> **用途**：运维管理平台（`xinya-ops`）独立后端，供管理员管理用户、宣教内容、AI配置及查看临床统计。
> **测试地址**：`http://localhost:8082`
> **Swagger UI**：`http://localhost:8082/swagger-ui.html`

---

## 一、系统架构

```
┌─────────────────────────────────────────────────────┐
│               运维人员浏览器                           │
│           xinya-ops 前端 (Vue3, :3002)               │
└──────────────────────┬──────────────────────────────┘
                       │ HTTP/REST  Bearer JWT
                       ▼
┌─────────────────────────────────────────────────────┐
│              xinya-ops 后端 (:8082)                   │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌────────┐ │
│  │   认证    │ │ 用户管理  │ │ 宣教内容  │ │ 配置   │ │
│  │  /auth   │ │  /users  │ │/education│ │/config │ │
│  └──────────┘ └──────────┘ └──────────┘ └────────┘ │
│  ┌──────────┐ ┌──────────┐                          │
│  │ 审计日志  │ │ 统计数据  │                          │
│  │  /audit  │ │  /stats  │                          │
│  └──────────┘ └──────┬───┘                          │
│       DB: xinya_ops  │                              │
└─────────────────────┬┴─────────────────────────────┘
                      │ X-Internal-Key（内部密钥）
                      ▼
┌─────────────────────────────────────────────────────┐
│            xinya-backend 后端 (:8081)                 │
│   /internal/stats/*    /internal/users/sync          │
│   /internal/education/sync  /internal/config/*       │
│       DB: xinya_dtx（临床数据）                        │
└─────────────────────────────────────────────────────┘
```

**数据流说明：**
- 运维端**推送**：用户同步、宣教内容同步、危机关键词同步 → `xinya-backend /internal/*`
- 运维端**拉取**：统计数据从 `xinya-backend /internal/stats/*` 透传给前端
- 两个服务**独立数据库**，`xinya_ops` 仅存运维侧主控数据

---

## 二、认证方式

所有 `/api/**`（除登录/刷新接口）均需携带 JWT：

```
Authorization: Bearer <access_token>
```

- Access Token 有效期：**15分钟**
- Refresh Token 有效期：**7天**
- 刷新方式：`POST /api/auth/refresh`

---

## 三、统一响应格式

```json
{
  "code": 200,
  "message": "success",
  "data": { ... }
}
```

| code | 含义 |
|------|------|
| 200 | 成功 |
| 400 | 请求参数错误 |
| 401 | 未认证 / Token失效 |
| 404 | 资源不存在 |
| 502 | 内部服务调用失败（临床端不可达） |

分页响应 `data` 结构：
```json
{
  "list": [...],
  "total": 100,
  "page": 1,
  "pageSize": 20
}
```

---

## 四、接口详情

### 4.1 认证模块 `/api/auth`

#### 登录
```
POST /api/auth/login
```
请求体：
```json
{ "username": "admin", "password": "Xinya@2024" }
```
响应：
```json
{
  "code": 200,
  "data": {
    "token": "eyJ...",
    "refreshToken": "eyJ...",
    "expiresIn": 900,
    "userId": "ops-admin-00000001",
    "username": "admin",
    "displayName": "超级管理员",
    "role": "ADMIN"
  }
}
```

#### 刷新 Token
```
POST /api/auth/refresh
```
请求体：`{ "refreshToken": "eyJ..." }`

#### 退出登录
```
POST /api/auth/logout
Authorization: Bearer <token>
```

---

### 4.2 用户管理 `/api/admin/users`

#### 用户列表
```
GET /api/admin/users?role=ADMIN&page=1&pageSize=20
```

| 参数 | 说明 | 可选值 |
|------|------|--------|
| role | 角色筛选 | ADMIN / NURSE / DOCTOR |
| page | 页码（从1开始） | 默认1 |
| pageSize | 每页条数 | 默认20 |

响应 `data.list` 元素：
```json
{
  "id": "ops-admin-00000001",
  "username": "admin",
  "displayName": "超级管理员",
  "role": "ADMIN",
  "phone": null,
  "enabled": true,
  "lastLoginAt": "2026-03-02T10:00:00",
  "createdAt": "2026-03-01T00:00:00"
}
```

#### 创建用户
```
POST /api/admin/users
```
请求体：
```json
{
  "username": "nurse_wang",
  "password": "Xinya@2024",
  "displayName": "王护士",
  "role": "NURSE",
  "phone": "13800000010"
}
```
> NURSE/DOCTOR 用户创建后会自动同步到 `xinya-backend`

#### 修改用户
```
PUT /api/admin/users/{id}
```
请求体（均可选）：
```json
{
  "displayName": "新姓名",
  "role": "DOCTOR",
  "phone": "13900000001",
  "password": "新密码（可选）"
}
```

#### 停用用户
```
POST /api/admin/users/{id}/deactivate
```

#### 删除用户
```
DELETE /api/admin/users/{id}
```

---

### 4.3 宣教内容 `/api/education/contents`

#### 内容列表
```
GET /api/education/contents?stage=PRETREATMENT&contentType=video&keyword=恶心&page=1&pageSize=20
```

| 参数 | 说明 |
|------|------|
| stage | 临床阶段（ADMISSION/PRETREATMENT/TRANSPLANT/REBUILD/DISCHARGE） |
| contentType | 内容类型（video/article） |
| keyword | 标题关键词模糊搜索 |
| page / pageSize | 分页 |

响应元素：
```json
{
  "id": "ec-abc12345",
  "title": "认识预处理方案",
  "stage": "PRETREATMENT",
  "category": "移植护理",
  "description": "介绍预处理化疗的目的和常见反应",
  "contentType": "video",
  "durationSeconds": 180,
  "thumbnailUrl": "https://cdn.example.com/thumb.jpg",
  "mediaUrl": "https://cdn.example.com/video.mp4",
  "tags": "化疗,恶心,预处理",
  "sortOrder": 10,
  "isActive": true,
  "syncedToClinical": true,
  "createdBy": "ops-admin-00000001",
  "createdAt": "2026-03-01T00:00:00",
  "updatedAt": "2026-03-02T00:00:00"
}
```

#### 新建内容
```
POST /api/education/contents
```
请求体：
```json
{
  "title": "认识预处理方案",
  "stage": "PRETREATMENT",
  "category": "移植护理",
  "description": "简介（可选）",
  "contentType": "video",
  "durationSeconds": 180,
  "thumbnailUrl": "https://...",
  "mediaUrl": "https://...",
  "tags": "化疗,恶心",
  "sortOrder": 10
}
```
> 创建后自动同步到 `xinya-backend`（患者端可见）

#### 修改内容
```
PUT /api/education/contents/{id}
```
请求体同创建，字段均可选，额外支持 `"isActive": false` 下架。

#### 下架内容
```
DELETE /api/education/contents/{id}
```
> 软删除（isActive=false），并通知临床端

---

### 4.4 系统配置 `/api/admin`

#### 获取危机关键词列表
```
GET /api/admin/crisis-keywords
```
响应 `data` 为数组：
```json
[
  { "id": 1, "keyword": "想死", "crisisLevel": "critical", "isActive": true, "createdAt": "..." },
  { "id": 2, "keyword": "绝望", "crisisLevel": "warning",  "isActive": true, "createdAt": "..." }
]
```

#### 新增危机关键词
```
POST /api/admin/crisis-keywords
```
请求体：
```json
{ "keyword": "活不下去", "crisisLevel": "critical" }
```
> 新增后立即推送全量关键词到 `xinya-backend` AI引擎

#### 删除危机关键词
```
DELETE /api/admin/crisis-keywords/{id}
```
> 逻辑删除（isActive=false），并推送全量关键词同步

#### 获取 PRO 题目列表
```
GET /api/admin/pro-questions?stage=PRETREATMENT
```
| 参数 | 说明 |
|------|------|
| stage | 阶段筛选，不传返回全部 |

响应元素：
```json
{
  "id": "q_nausea",
  "stage": "PRETREATMENT",
  "title": "您今天有恶心或呕吐感吗？",
  "type": "scale",
  "scaleMin": 0, "scaleMax": 10,
  "minLabel": "完全没有", "maxLabel": "极度严重",
  "symptomKey": "nausea",
  "sortOrder": 10,
  "isActive": true
}
```

#### 修改 PRO 题目（仅排序和启用状态）
```
PUT /api/admin/pro-questions/{id}
```
请求体：
```json
{ "sortOrder": 5, "isActive": false }
```

---

### 4.5 统计数据 `/api/stats`

> 本模块为只读，数据来自 `xinya-backend` 内部接口透传

#### 总览数据
```
GET /api/stats/overview
```
响应 `data`：
```json
{
  "totalPatients": 32,
  "activePatients": 18,
  "avgPsychEnergy": 72,
  "checkinRate": 0.85,
  "stageDistribution": {
    "ADMISSION": 3, "PRETREATMENT": 5, "TRANSPLANT": 4,
    "REBUILD": 4, "DISCHARGE": 2
  },
  "recentAlerts": [
    {
      "patientId": "p001",
      "patientName": "张三",
      "level": "critical",
      "message": "检测到危机情绪：想死",
      "createdAt": "2026-03-02T09:30:00"
    }
  ]
}
```

#### 心理状态分布
```
GET /api/stats/psych-distribution
```
响应 `data`：
```json
{ "healthy": 14, "mild": 6, "warning": 3 }
```
> 按心理能量分段：healthy≥70，mild 40-70，warning<40

#### 症状热力图
```
GET /api/stats/symptom-heatmap?days=14
```
| 参数 | 说明 | 默认 |
|------|------|------|
| days | 统计天数 | 14 |

响应 `data`：
```json
{
  "dates": ["2026-02-17", "2026-02-18", "..."],
  "symptoms": ["恶心", "疲乏", "疼痛", "失眠", "焦虑"],
  "matrix": [
    [3.2, 4.1, 2.8, 5.0, 3.3],
    [2.9, 3.8, 3.1, 4.5, 2.7]
  ]
}
```
> `matrix[symptomIndex][dateIndex]` = 当日该症状平均评分

---

### 4.6 审计日志 `/api/admin/audit-logs`

```
GET /api/admin/audit-logs?action=CREATE_USER&startDate=2026-03-01&endDate=2026-03-02&page=1&pageSize=20
```

| 参数 | 说明 |
|------|------|
| userId | 操作人ID |
| action | 操作类型（见下表） |
| targetType | 目标类型：user/content/keyword/question |
| targetId | 目标ID |
| startDate | 开始日期（YYYY-MM-DD） |
| endDate | 结束日期（YYYY-MM-DD） |

常见 action 值：

| action | 说明 |
|--------|------|
| CREATE_USER | 创建用户 |
| UPDATE_USER | 修改用户 |
| DEACTIVATE_USER | 停用用户 |
| DELETE_USER | 删除用户 |
| CREATE_CONTENT | 创建宣教内容 |
| UPDATE_CONTENT | 修改宣教内容 |
| DEACTIVATE_CONTENT | 下架宣教内容 |
| CREATE_KEYWORD | 添加危机关键词 |
| DELETE_KEYWORD | 删除危机关键词 |
| UPDATE_PRO_QUESTION | 修改PRO题目 |

---

## 五、curl 测试快速上手

### 第一步：登录获取 Token

```bash
curl -X POST http://localhost:8082/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Xinya@2024"}'
```

将响应中的 `data.token` 保存为环境变量：

```bash
TOKEN="eyJ..."
```

---

### 第二步：建议测试顺序

**1. 验证认证**
```bash
curl http://localhost:8082/api/admin/users \
  -H "Authorization: Bearer $TOKEN"
```

**2. 创建一个护士账号**
```bash
curl -X POST http://localhost:8082/api/admin/users \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "nurse_test",
    "password": "Test@1234",
    "displayName": "测试护士",
    "role": "NURSE",
    "phone": "13800000099"
  }'
```

**3. 新建宣教内容**
```bash
curl -X POST http://localhost:8082/api/education/contents \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "骨髓移植全流程介绍",
    "stage": "ADMISSION",
    "category": "入仓教育",
    "contentType": "video",
    "durationSeconds": 240,
    "mediaUrl": "https://example.com/video.mp4",
    "sortOrder": 1
  }'
```

**4. 查看临床统计数据**
```bash
# 总览
curl http://localhost:8082/api/stats/overview \
  -H "Authorization: Bearer $TOKEN"

# 心理分布
curl http://localhost:8082/api/stats/psych-distribution \
  -H "Authorization: Bearer $TOKEN"

# 症状热力图（近7天）
curl "http://localhost:8082/api/stats/symptom-heatmap?days=7" \
  -H "Authorization: Bearer $TOKEN"
```

**5. 添加危机关键词**
```bash
curl -X POST http://localhost:8082/api/admin/crisis-keywords \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"keyword":"活不下去","crisisLevel":"critical"}'
```

**6. 查看审计日志**
```bash
curl "http://localhost:8082/api/admin/audit-logs?page=1&pageSize=10" \
  -H "Authorization: Bearer $TOKEN"
```

**7. 刷新 Token**
```bash
curl -X POST http://localhost:8082/api/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"<your_refresh_token>"}'
```

---

## 六、内部 API（供参考，非前端直接调用）

`xinya-ops` 调用 `xinya-backend` 的内部接口时，请求头携带：

```
X-Internal-Key: dev-internal-key
```

| 内部接口 | 触发时机 |
|----------|----------|
| `POST /internal/users/sync` | ops 创建/修改 NURSE/DOCTOR 用户 |
| `POST /internal/education/sync` | ops 新建宣教内容 |
| `PUT /internal/education/sync/{id}` | ops 修改宣教内容 |
| `DELETE /internal/education/sync/{id}` | ops 下架宣教内容 |
| `POST /internal/config/crisis-keywords` | ops 增删危机关键词 |
| `POST /internal/config/pro-questions` | ops 修改 PRO 题目 |
| `GET /internal/stats/overview` | ops 拉取临床总览 |
| `GET /internal/stats/psych-distribution` | ops 拉取心理分布 |
| `GET /internal/stats/symptom-heatmap` | ops 拉取症状热力图 |

---

## 七、初始账号

| 用户名 | 密码 | 角色 | 说明 |
|--------|------|------|------|
| `admin` | `Xinya@2024` | ADMIN | 超级管理员 |
| `ops_zhang` | `Xinya@2024` | ADMIN | 运维专员 |
| `content_li` | `Xinya@2024` | ADMIN | 内容运营 |

> 初始数据由 `sql/init_ops.sql` 脚本写入，生产环境部署后请立即修改密码。
