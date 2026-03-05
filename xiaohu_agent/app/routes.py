from fastapi import APIRouter
from .schemas import (
    NurseChatRequest,
    NurseChatResponse,
    PsychChatResponse,
    SymptomTriggerRequest,
    SymptomTriggerResponse,
    ReminderPlanRequest,
    ReminderPlanResponse,
    RecommendationRequest,
    RecommendationResponse,
)
from .llm import call_deepseek

router = APIRouter()


@router.post("/v1/nurse/chat", response_model=NurseChatResponse)
async def nurse_chat(req: NurseChatRequest):
    """Nurse chat endpoint. Calls placeholder LLM function."""
    resp = await call_deepseek("nurse_chat", **req.dict())
    return resp


@router.post("/v1/nurse/symptom-trigger", response_model=SymptomTriggerResponse)
async def symptom_trigger(req: SymptomTriggerRequest):
    """情景触发式内容推荐接口。"""
    # forward request to deepseek with a distinct agent type
    resp = await call_deepseek("nurse_symptom_trigger", **req.dict())
    return resp


@router.post("/v1/nurse/reminder-plan", response_model=ReminderPlanResponse)
async def reminder_plan(req: ReminderPlanRequest):
    """血象趋势个性化提醒推荐接口。"""
    resp = await call_deepseek("nurse_reminder_plan", **req.dict())
    return resp


@router.post("/v1/nurse/recommendations", response_model=RecommendationResponse)
async def recommendations(req: RecommendationRequest):
    """护理推荐提问接口。"""
    resp = await call_deepseek("nurse_recommendations", **req.dict())
    return resp
