# xinya-ruoyi-backend

骨髓移植隔离病房数字疗法系统 - 医护端后端（若依框架版）

## 技术栈

- **框架**: Spring Boot 3.2.2 + MyBatis-Plus 3.5.5
- **认证**: 自定义 JWT（auth0 java-jwt）+ Spring Security
- **数据库**: MySQL 8.x（数据库名 `xinya_dtx`）
- **迁移**: Flyway
- **文档**: SpringDoc OpenAPI 3 (`/swagger-ui.html`)
- **AI 服务**: WebFlux WebClient（流式调用 Python FastAPI）

## 模块说明

```
xinya-ruoyi-backend/
├── ruoyi-common/       公共组件：R<T>响应、PageResult分页、JWT工具、通用异常
├── ruoyi-framework/    框架层：Security配置、MyBatis-Plus配置、全局异常处理
├── xinya-business/     业务模块：所有 DTx 业务代码
│   └── user / patient / pro / education / hopetree /
│       clinical / alerts / dashboard / agent / robot / sync / internal
└── ruoyi-admin/        启动模块：main class + application.yml + Flyway脚本
```

## 认证说明

| 调用方 | 认证方式 |
|--------|---------|
| 医护/医生/管理员 | `Authorization: Bearer <JWT>` |
| 机器人/患者端 | 白名单路径，无需 Token |
| 运维 Internal API | `X-Internal-Key: <key>`（`/internal/**`） |

### JWT 白名单路径（无需登录）

- `/api/auth/login*`、`/api/auth/register`、`/api/auth/refresh`
- `/api/robot/**`、`/api/sync/**`
- `/api/agent/**`、`/api/pro/**`
- `/api/hopetree/**`
- `/api/education/contents/**`、`/api/education/progress/**`
- `/api/clinical/stage/**`
- `/api/patients/{id}`（读取患者基本信息）

## 快速启动

### 环境变量

```bash
DB_HOST=localhost
DB_PORT=3306
DB_NAME=xinya_dtx
DB_USER=root
DB_PASS=yourpassword
JWT_SECRET=your-secret-key
INTERNAL_API_KEY=dev-internal-key
AI_PSYCH_URL=http://localhost:8001
AI_NURSE_URL=http://localhost:8443
```

### 构建与运行

```bash
cd xinya-ruoyi-backend
mvn clean package -DskipTests
java -jar ruoyi-admin/target/ruoyi-admin-1.0.0-SNAPSHOT.jar
```

### API 文档

启动后访问：http://localhost:8080/swagger-ui.html

## 响应格式

```json
{
  "code": 200,
  "message": "success",
  "data": { ... }
}
```

> 注意：响应字段名为 `message`（非若依标准的 `msg`），与原 xinya-backend 保持一致，前端无需改动。

## 数据库

直接使用原 `xinya_dtx` 数据库，业务表结构保持不变。
Flyway 自动执行：
- `V1__init_system_tables.sql`：创建操作日志表
- `V2__check_business_tables.sql`：补充查询索引
