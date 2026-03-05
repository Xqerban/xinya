from pydantic import BaseModel
from typing import List, Optional, Dict


class ChatMessage(BaseModel):
    role: str
    content: str


class NurseChatRequest(BaseModel):
    sessionId: str
    patientContext: dict
    history: List[ChatMessage]
    message: str


class RecommendedContent(BaseModel):
    contentId: str
    reason: Optional[str] = None


class AgentMeta(BaseModel):
    model: str
    tokensUsed: int
    latencyMs: int


class NurseChatResponse(BaseModel):
    reply: str
    recommendedQuestions: List[str]
    recommendedContents: Optional[List[RecommendedContent]] = None
    agentMeta: AgentMeta


# Psych response includes additional assessments (five-dim energy + crisis)
class EnergyAssessment(BaseModel):
    mood: int
    energy: int
    cognition: int
    social: int
    coping: int


class CrisisAssessment(BaseModel):
    level: str
    reason: Optional[str] = None


class PsychChatResponse(NurseChatResponse):
    energyAssessment: EnergyAssessment
    crisisAssessment: CrisisAssessment


# ----------------------------- additional API models -----------------------------

# D. 情景触发式内容推荐
class DetectedSymptom(BaseModel):
    symptomKey: str
    symptomName: str
    score: int
    maxScore: int


class SymptomTriggerRequest(BaseModel):
    patientId: str
    patientContext: dict
    triggerSource: str
    detectedSymptoms: List[DetectedSymptom]
    viewedContentIds: List[str] = []


class RecommendedContentDetail(BaseModel):
    contentId: str
    title: str
    matchedSymptom: str
    priority: int
    reason: Optional[str] = None


class SymptomTriggerResponse(BaseModel):
    pushMessage: str
    recommendedContents: List[RecommendedContentDetail]
    hopeTreeExpDelta: int
    agentMeta: AgentMeta


# E. 血象趋势个性化提醒
class BloodRecord(BaseModel):
    recordDate: str
    wbc: float
    neutrophil: float
    platelet: float
    hemoglobin: int


class BloodTrend(BaseModel):
    latestRecord: BloodRecord
    history: List[BloodRecord]
    trends: Dict[str, str]


class ReminderPlanItem(BaseModel):
    reminderId: str
    scheduledTime: str
    type: str
    contentId: Optional[str]
    pushMessage: str
    priority: int
    triggerReason: Optional[str] = None


class ImmediateAlert(BaseModel):
    level: str
    indicator: str
    value: float
    threshold: float
    message: str
    pushMessageToPatient: str


class ReminderPlanRequest(BaseModel):
    patientId: str
    patientContext: dict
    bloodTrend: BloodTrend
    planType: str
    viewedContentIds: List[str] = []


class ReminderPlanResponse(BaseModel):
    patientId: str
    planType: str
    trendInterpretation: str
    reminderPlan: List[ReminderPlanItem]
    immediateAlert: Optional[ImmediateAlert]
    hopeTreeExpDeltaPerCompletion: int


# F. 护理推荐提问
class RecommendationRequest(BaseModel):
    patientContext: dict
    recentSymptoms: List[str]
    recentHistory: List[ChatMessage]


class RecommendationResponse(BaseModel):
    questions: List[str]
