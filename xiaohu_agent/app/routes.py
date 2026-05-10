import json
import time
import logging
from fastapi import APIRouter
from fastapi.responses import StreamingResponse
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
from .llm import call_deepseek, stream_deepseek, build_sse_event

logger = logging.getLogger(__name__)

router = APIRouter()


@router.post("/v1/nurse/chat")
async def nurse_chat(req: NurseChatRequest):
    """Nurse chat endpoint with streaming output.
    
    Streams reply text as delta events, then sends complete response in done event.
    """
    async def generate_stream():
        stream_started_at = time.perf_counter()
        first_delta_at = None
        
        try:
            # Send start event
            yield build_sse_event("start", {
                "sessionId": req.sessionId,
                "message": "stream started"
            })
            
            # Collect full response while streaming
            full_response = ""
            async for chunk in stream_deepseek("nurse_chat", **req.dict()):
                full_response += chunk
                # Stream the text as delta events
                if chunk.strip():
                    if first_delta_at is None:
                        first_delta_at = time.perf_counter()
                    yield build_sse_event("delta", {
                        "content": chunk,
                        "stage": "response"
                    })
            
            # Parse the complete response
            try:
                response_data = json.loads(full_response)
            except json.JSONDecodeError:
                logger.error(f"Failed to parse LLM response as JSON: {full_response}")
                response_data = {
                    "reply": full_response,
                    "recommendedQuestions": [],
                    "recommendedContents": [],
                    "agentMeta": {
                        "model": "deepseek-chat",
                        "tokensUsed": 0,
                        "latencyMs": int((time.perf_counter() - stream_started_at) * 1000)
                    }
                }
            
            # Ensure required fields exist
            if "agentMeta" not in response_data:
                response_data["agentMeta"] = {
                    "model": "deepseek-chat",
                    "tokensUsed": 0,
                    "latencyMs": 0
                }
            
            latency_ms = int((time.perf_counter() - stream_started_at) * 1000)
            first_delta_ms = int((first_delta_at - stream_started_at) * 1000) if first_delta_at else None
            
            response_data["agentMeta"]["latencyMs"] = latency_ms
            if first_delta_ms is not None:
                response_data["agentMeta"]["firstDeltaMs"] = first_delta_ms
            
            # Send done event with complete response
            yield build_sse_event("done", response_data)
            
        except Exception as e:
            logger.exception("Error in nurse_chat streaming")
            yield build_sse_event("error", {
                "error": "internal_error",
                "message": "处理请求时发生错误，请稍后再试"
            })
    
    return StreamingResponse(
        generate_stream(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "X-Accel-Buffering": "no"
        }
    )


@router.post("/v1/nurse/symptom-trigger")
async def symptom_trigger(req: SymptomTriggerRequest):
    """情景触发式内容推荐接口（流式输出）。"""
    async def generate_stream():
        stream_started_at = time.perf_counter()
        first_delta_at = None
        
        try:
            yield build_sse_event("start", {
                "patientId": req.patientId,
                "message": "stream started"
            })
            
            full_response = ""
            async for chunk in stream_deepseek("nurse_symptom_trigger", **req.dict()):
                full_response += chunk
                if chunk.strip():
                    if first_delta_at is None:
                        first_delta_at = time.perf_counter()
                    yield build_sse_event("delta", {
                        "content": chunk,
                        "stage": "response"
                    })
            
            try:
                response_data = json.loads(full_response)
            except json.JSONDecodeError:
                logger.error(f"Failed to parse LLM response as JSON: {full_response}")
                response_data = {
                    "pushMessage": full_response,
                    "recommendedContents": [],
                    "hopeTreeExpDelta": 0,
                    "agentMeta": {
                        "model": "deepseek-chat",
                        "latencyMs": int((time.perf_counter() - stream_started_at) * 1000)
                    }
                }
            
            if "agentMeta" not in response_data:
                response_data["agentMeta"] = {"model": "deepseek-chat"}
            
            latency_ms = int((time.perf_counter() - stream_started_at) * 1000)
            first_delta_ms = int((first_delta_at - stream_started_at) * 1000) if first_delta_at else None
            response_data["agentMeta"]["latencyMs"] = latency_ms
            if first_delta_ms is not None:
                response_data["agentMeta"]["firstDeltaMs"] = first_delta_ms
            
            yield build_sse_event("done", response_data)
            
        except Exception as e:
            logger.exception("Error in symptom_trigger streaming")
            yield build_sse_event("error", {
                "error": "internal_error",
                "message": "处理请求时发生错误，请稍后再试"
            })
    
    return StreamingResponse(
        generate_stream(),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"}
    )


@router.post("/v1/nurse/reminder-plan")
async def reminder_plan(req: ReminderPlanRequest):
    """血象趋势个性化提醒推荐接口（流式输出）。"""
    async def generate_stream():
        stream_started_at = time.perf_counter()
        first_delta_at = None
        
        try:
            yield build_sse_event("start", {
                "patientId": req.patientId,
                "message": "stream started"
            })
            
            full_response = ""
            async for chunk in stream_deepseek("nurse_reminder_plan", **req.dict()):
                full_response += chunk
                if chunk.strip():
                    if first_delta_at is None:
                        first_delta_at = time.perf_counter()
                    yield build_sse_event("delta", {
                        "content": chunk,
                        "stage": "response"
                    })
            
            try:
                response_data = json.loads(full_response)
            except json.JSONDecodeError:
                logger.error(f"Failed to parse LLM response as JSON: {full_response}")
                response_data = {
                    "patientId": req.patientId,
                    "planType": "daily_schedule",
                    "trendInterpretation": full_response,
                    "reminderPlan": [],
                    "immediateAlert": None,
                    "hopeTreeExpDeltaPerCompletion": 0,
                    "agentMeta": {
                        "model": "deepseek-chat",
                        "latencyMs": int((time.perf_counter() - stream_started_at) * 1000)
                    }
                }
            
            if "agentMeta" not in response_data:
                response_data["agentMeta"] = {"model": "deepseek-chat"}
            
            latency_ms = int((time.perf_counter() - stream_started_at) * 1000)
            first_delta_ms = int((first_delta_at - stream_started_at) * 1000) if first_delta_at else None
            response_data["agentMeta"]["latencyMs"] = latency_ms
            if first_delta_ms is not None:
                response_data["agentMeta"]["firstDeltaMs"] = first_delta_ms
            
            yield build_sse_event("done", response_data)
            
        except Exception as e:
            logger.exception("Error in reminder_plan streaming")
            yield build_sse_event("error", {
                "error": "internal_error",
                "message": "处理请求时发生错误，请稍后再试"
            })
    
    return StreamingResponse(
        generate_stream(),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"}
    )


@router.post("/v1/nurse/recommendations")
async def recommendations(req: RecommendationRequest):
    """护理推荐提问接口（流式输出）。"""
    async def generate_stream():
        stream_started_at = time.perf_counter()
        
        try:
            yield build_sse_event("start", {
                "message": "stream started"
            })
            
            full_response = ""
            async for chunk in stream_deepseek("nurse_recommendations", **req.dict()):
                full_response += chunk
                if chunk.strip():
                    yield build_sse_event("delta", {
                        "content": chunk,
                        "stage": "response"
                    })
            
            try:
                response_data = json.loads(full_response)
            except json.JSONDecodeError:
                logger.error(f"Failed to parse LLM response as JSON: {full_response}")
                response_data = {"questions": []}
            
            response_data["agentMeta"] = {
                "model": "deepseek-chat",
                "latencyMs": int((time.perf_counter() - stream_started_at) * 1000)
            }
            
            yield build_sse_event("done", response_data)
            
        except Exception as e:
            logger.exception("Error in recommendations streaming")
            yield build_sse_event("error", {
                "error": "internal_error",
                "message": "处理请求时发生错误，请稍后再试"
            })
    
    return StreamingResponse(
        generate_stream(),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"}
    )
