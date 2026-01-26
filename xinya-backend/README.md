# 心芽DTx - 后端服务

骨髓移植隔离病房数字疗法系统 - Spring Boot 后端服务

## 项目架构

```
src/main/java/com/xinya/dtx/
├── config/                 # 配置类
├── controller/             # REST API控制器
├── service/                # 业务逻辑层
├── repository/             # 数据访问层
├── entity/                 # JPA实体
├── dto/                    # 数据传输对象
├── ai/                     # AI智能体网关
└── statemachine/           # 临床路径状态机
```

## 技术栈

- **框架**: Spring Boot 3.2
- **语言**: Java 17
- **数据库**: MySQL 8.0 (生产) / H2 (开发)
- **ORM**: Spring Data JPA
- **迁移**: Flyway
- **文档**: SpringDoc OpenAPI 3.0

## API 文档

启动服务后访问：
- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs

## 核心接口

| 模块 | 端点 | 描述 |
|------|------|------|
| 患者 | `POST /api/patients` | 创建患者档案 |
| 患者 | `GET /api/patients/{id}` | 获取患者信息 |
| Agent | `POST /api/agent/chat` | 智能体对话 |
| 临床路径 | `POST /api/clinical/transition` | 阶段流转 |
| PRO | `POST /api/pro/submit` | 提交打卡数据 |
| 希望之树 | `GET /api/hopetree/{patientId}` | 获取生长状态 |
| 驾驶舱 | `GET /api/dashboard/overview` | 概览数据 |

## 开发环境

```bash
# 开发模式运行 (使用H2内存数据库)
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# 访问H2控制台
http://localhost:8080/h2-console
# JDBC URL: jdbc:h2:mem:xinyadt
```

## 生产部署

配置环境变量：

```bash
export DB_HOST=your-mysql-host
export DB_PORT=3306
export DB_NAME=xinya_dtx
export DB_USERNAME=your-username
export DB_PASSWORD=your-password
export AI_API_KEY=your-openai-api-key

mvn spring-boot:run -Dspring-boot.run.profiles=prod
```

## AI 配置

默认AI功能关闭，返回Mock回复。启用AI需要在 `application.yml` 中配置：

```yaml
xinya:
  ai:
    enabled: true
    base-url: https://api.openai.com/v1
    api-key: your-api-key
    model: gpt-4
```
