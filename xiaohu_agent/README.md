# 运行方式
uvicorn app.main:app --host localhost --port 8443

## 已实现的接口

- `POST /v1/nurse/chat` – 护理对话，接收历史消息并返回回复 + 推荐内容/问题
- `POST /v1/nurse/symptom-trigger` – 情景触发式内容推荐
- `POST /v1/nurse/reminder-plan` – 血象趋势个性化提醒计划
- `POST /v1/nurse/recommendations` – 护理推荐提问

以上接口均采用 `application/json`，并返回本项目定义的 Pydantic 模型。
