import os
import time
from typing import Any, Dict, Optional

import httpx


DEEPSEEK_URL = os.getenv("DEEPSEEK_BASE_URL")
DEEPSEEK_API_KEY = os.getenv("DEEPSEEK_API_KEY")


async def call_deepseek(agent_type: str, **payload) -> Dict[str, Any]:
    """Call DeepSeek LLM API asynchronously with a flexible payload.

    The original implementation required a fixed set of fields; new endpoints send
    different schemas so we accept **payload and simply attach ``agentType``. If
    environment variables are missing or the request fails, fall back to the local placeholder.
    """
    start = time.time()

    if not DEEPSEEK_URL or not DEEPSEEK_API_KEY:
        return _local_placeholder(agent_type, payload, start)

    # never mutate caller's dict
    body = dict(payload)
    body["agentType"] = agent_type

    headers = {
        "Authorization": f"Bearer {DEEPSEEK_API_KEY}",
        "Content-Type": "application/json",
    }

    async with httpx.AsyncClient(timeout=30.0) as client:
        try:
            r = await client.post(DEEPSEEK_URL.rstrip("/") + "/v1/generate", json=body, headers=headers)
            r.raise_for_status()
            data = r.json()
        except Exception:
            return _local_placeholder(agent_type, payload, start)

    latency_ms = int((time.time() - start) * 1000)
    # Ensure agentMeta exists and has latency
    if "agentMeta" not in data:
        data["agentMeta"] = {"model": "deepseek", "tokensUsed": 0, "latencyMs": latency_ms}
    else:
        data["agentMeta"].setdefault("latencyMs", latency_ms)

    return data


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

    # symptoms, reminders and questions are specialised and should be handled
    # before the generic "nurse chat" branch, otherwise they get swallowed.
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
