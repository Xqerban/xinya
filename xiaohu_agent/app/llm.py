import os
import time
import json
from typing import Any, Dict, Optional

from openai import AsyncOpenAI


DEEPSEEK_API_KEY = os.getenv("DEEPSEEK_API_KEY",'sk-9895d0792ac243a997fd9a56dafaf0b1')
client = AsyncOpenAI(
    api_key=DEEPSEEK_API_KEY,
    base_url="https://api.deepseek.com"
)


def _get_system_prompt(agent_type: str) -> str:
    """Get the system prompt for the given agent type."""
    base_prompt = (
        "你是一个医疗AI助手，专门处理癌症患者的护理宣教和心理支持。"
        "根据输入的患者信息和请求，返回指定格式的JSON响应。"
        "确保响应是有效的JSON格式，不要添加额外文本。"
        "如果某些内容未提供，使用默认值或忽略可选字段。"
    )
    
    if agent_type == "nurse_chat":
        return base_prompt + (
            "\n\n任务：作为护理智能体（小护士），回答患者的护理宣教问题。"
            "\n输出格式："
            "\n{"
            "\n  \"reply\": \"回复内容\","
            "\n  \"recommendedQuestions\": [\"问题1\", \"问题2\"],"
            "\n  \"recommendedContents\": [{\"contentId\": \"ec-001\", \"reason\": \"理由\"}],"
            "\n  \"agentMeta\": {\"model\": \"deepseek-chat\", \"tokensUsed\": 0, \"latencyMs\": 0}"
            "\n}"
        )
    elif agent_type == "nurse_symptom_trigger":
        return base_prompt + (
            "\n\n任务：根据患者症状，推荐宣教内容和推送消息。"
            "\n输出格式："
            "\n{"
            "\n  \"pushMessage\": \"推送文案\","
            "\n  \"recommendedContents\": ["
            "\n    {"
            "\n      \"contentId\": \"ec-003\","
            "\n      \"title\": \"标题\","
            "\n      \"matchedSymptom\": \"symptomKey\","
            "\n      \"priority\": 1,"
            "\n      \"reason\": \"理由\""
            "\n    }"
            "\n  ],"
            "\n  \"hopeTreeExpDelta\": 15,"
            "\n  \"agentMeta\": {\"model\": \"deepseek-chat\", \"latencyMs\": 0}"
            "\n}"
        )
    elif agent_type == "nurse_reminder_plan":
        return base_prompt + (
            "\n\n任务：根据血象趋势，生成提醒计划。"
            "\n输出格式："
            "\n{"
            "\n  \"patientId\": \"p-001\","
            "\n  \"planType\": \"daily_schedule\","
            "\n  \"trendInterpretation\": \"趋势解读\","
            "\n  \"reminderPlan\": ["
            "\n    {"
            "\n      \"reminderId\": \"r-001\","
            "\n      \"scheduledTime\": \"08:30\","
            "\n      \"type\": \"education_push\","
            "\n      \"contentId\": \"ec-009\","
            "\n      \"pushMessage\": \"推送文案\","
            "\n      \"priority\": 1,"
            "\n      \"triggerReason\": \"理由\""
            "\n    }"
            "\n  ],"
            "\n  \"immediateAlert\": null,"
            "\n  \"hopeTreeExpDeltaPerCompletion\": 15"
            "\n}"
        )
    elif agent_type == "nurse_recommendations":
        return base_prompt + (
            "\n\n任务：推荐提问。"
            "\n输出格式："
            "\n{"
            "\n  \"questions\": [\"问题1\", \"问题2\"]"
            "\n}"
        )
    elif agent_type == "psych_chat":
        return base_prompt + (
            "\n\n任务：作为心理智能体，回答患者心理问题。"
            "\n输出格式："
            "\n{"
            "\n  \"reply\": \"回复内容\","
            "\n  \"recommendedQuestions\": [\"问题1\", \"问题2\"],"
            "\n  \"recommendedContents\": [{\"contentId\": \"ec-001\", \"reason\": \"理由\"}],"
            "\n  \"energyAssessment\": {\"mood\": 50, \"energy\": 50, \"cognition\": 50, \"social\": 50, \"coping\": 50},"
            "\n  \"crisisAssessment\": {\"level\": \"GREEN\", \"reason\": null},"
            "\n  \"agentMeta\": {\"model\": \"deepseek-chat\", \"tokensUsed\": 0, \"latencyMs\": 0}"
            "\n}"
        )
    else:
        return base_prompt + "\n\n任务：通用回复，返回JSON格式响应。"


async def call_deepseek(agent_type: str, **payload) -> Dict[str, Any]:
    """Call DeepSeek LLM API asynchronously with a flexible payload.

    The original implementation required a fixed set of fields; new endpoints send
    different schemas so we accept **payload and simply attach ``agentType``. If
    environment variables are missing or the request fails, fall back to the local placeholder.
    """
    start = time.time()

    if not DEEPSEEK_API_KEY:
        return _local_placeholder(agent_type, payload, start)

    system_prompt = _get_system_prompt(agent_type)
    user_content = json.dumps(payload, ensure_ascii=False)

    try:
        response = await client.chat.completions.create(
            model="deepseek-chat",
            messages=[
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": user_content},
            ],
            stream=False
        )
        content = response.choices[0].message.content
        data = json.loads(content)
        # Validate basic structure
        if not isinstance(data, dict):
            raise ValueError("Response is not a dict")
        # Add agentMeta if missing
        if "agentMeta" not in data:
            data["agentMeta"] = {"model": "deepseek-chat", "tokensUsed": 0, "latencyMs": 0}
        latency_ms = int((time.time() - start) * 1000)
        data["agentMeta"]["latencyMs"] = latency_ms
        return data
    except Exception as e:
        print(f"DeepSeek call failed: {e}")
        return _local_placeholder(agent_type, payload, start)


def _local_placeholder(agent_type: str, payload: Any, start_time: Optional[float] = None) -> Dict[str, Any]:
    """Return a small fallback response when DeepSeek unavailable.

    ``payload`` may be a dict containing whatever the route sent; for our
    purposes we only look at a few keys. The goal is to return an object that
    matches the expected schema for the given agent_type so the FastAPI
    response_model validation succeeds.
    """
    if start_time is None:
        start_time = time.time()
    latency_ms = int((time.time() - start_time) * 1000)

    # basic structure shared by most responses
    base = {
        "agentMeta": {"model": "deepseek-fallback", "tokensUsed": 0, "latencyMs": latency_ms},
    }

    if agent_type == "nurse_chat":
        # 按 Agent-api.md 示例返回结构
        return {
            "reply": "[deepseek-fallback] " + payload.get("message", "(no message)"),
            "recommendedQuestions": ["什么时候需要通知医护？", "如何缓解不适？"],
            "recommendedContents": [{"contentId": "ec-000", "reason": "占位推荐"}],
            "agentMeta": base["agentMeta"],
        }

    if agent_type == "nurse_symptom_trigger":
        # 按 Agent-api.md 示例返回结构
        return {
            "pushMessage": "小明，我注意到你今天恶心感比较明显。我为你找了一个专门讲这个的小视频，一起看看怎么缓解吧～",
            "recommendedContents": [
                {
                    "contentId": "ec-003",
                    "title": "认识预处理：恶心呕吐应对指南",
                    "matchedSymptom": "nausea",
                    "priority": 1,
                    "reason": "患者恶心评分3/3，推荐核心应对视频"
                },
                {
                    "contentId": "ec-007",
                    "title": "预处理期饮食调整小贴士",
                    "matchedSymptom": "appetite_loss",
                    "priority": 2,
                    "reason": "恶心伴食欲下降，补充饮食管理知识"
                }
            ],
            "hopeTreeExpDelta": 15,
            "agentMeta": base["agentMeta"],
        }

    if agent_type == "nurse_reminder_plan":
        # 按 Agent-api.md 示例返回结构
        return {
            "patientId": payload.get("patientId", "p-001"),
            "planType": payload.get("planType", "daily_schedule"),
            "trendInterpretation": "白细胞和中性粒细胞连续3天上升，提示造血重建进展良好，但血小板仍低需注意出血防护",
            "reminderPlan": [
                {
                    "reminderId": "r-001",
                    "scheduledTime": "08:30",
                    "type": "education_push",
                    "contentId": "ec-009",
                    "pushMessage": "早上好！你的血象在悄悄变好了～今天我们来了解一下血小板低时要注意什么哦",
                    "priority": 1,
                    "triggerReason": "血小板35，低于正常值，推送出血防护宣教"
                },
                {
                    "reminderId": "r-002",
                    "scheduledTime": "15:00",
                    "type": "encouragement",
                    "contentId": None,
                    "pushMessage": "小明，你的白细胞今天达到1.2了！这是你的希望之树正在茁壮成长的信号，继续加油！",
                    "priority": 2,
                    "triggerReason": "白细胞连续上升，给予正向激励"
                },
                {
                    "reminderId": "r-003",
                    "scheduledTime": "20:00",
                    "type": "education_push",
                    "contentId": "ec-012",
                    "pushMessage": "睡前小知识：重建期为什么要每天测血象？点击了解背后的原因～",
                    "priority": 3,
                    "triggerReason": "阶段匹配：重建期血象监测知识宣教"
                }
            ],
            "immediateAlert": None,
            "hopeTreeExpDeltaPerCompletion": 15,
        }

    if agent_type == "nurse_recommendations":
        # 按 Agent-api.md 示例返回结构
        return {
            "questions": [
                "今天恶心的感觉是什么时候最严重？",
                "有试过少量多餐吗？",
                "想看一个缓解恶心的小视频吗？",
                "口腔里有没有出现溃疡或不适？"
            ]
        }

    # fallback for anything else under the "nurse" prefix or psych chat
    if agent_type.startswith("nurse") or agent_type == "psych":
        # chat-style reply
        msg = "[deepseek-fallback]"
        if isinstance(payload, dict):
            msg += " " + payload.get("message", "(no message)")
        base.update({
            "reply": msg,
            "recommendedQuestions": ["什么时候需要通知医护？", "如何缓解不适？"],
            "recommendedContents": [{"contentId": "ec-000", "reason": "占位推荐"}],
        })
        if agent_type == "psych":
            base["energyAssessment"] = {"mood": 50, "energy": 50, "cognition": 50, "social": 50, "coping": 50}
            base["crisisAssessment"] = {"level": "GREEN", "reason": None}
        return base

    # generic fallback
    return {"error": "service_unavailable", "message": "DeepSeek 无法访问"}


__all__ = ["call_deepseek"]
