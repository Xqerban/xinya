"""
小芽 Agent API 服务
"""
from flask import Flask, request, jsonify
from functools import wraps
import traceback
from typing import Dict, Any, Optional
from simple_agent import EnhancedChatAgent
from config import Config
from transplant_support import TransplantPhase

app = Flask(__name__)

# 全局 Agent 实例管理（按 sessionId 管理）
agent_sessions: Dict[str, EnhancedChatAgent] = {}

# API 密钥验证
def require_api_key(f):
    @wraps(f)
    def decorated_function(*args, **kwargs):
        api_key = request.headers.get('X-Api-Key')
        expected_key = Config.API_KEY  # 可以单独配置一个 API_KEY
        
        if not api_key or api_key != expected_key:
            return jsonify({"error": "unauthorized", "message": "无效的 API 密钥"}), 401
        
        return f(*args, **kwargs)
    return decorated_function


def get_or_create_agent(session_id: str) -> EnhancedChatAgent:
    """获取或创建 Agent 实例"""
    if session_id not in agent_sessions:
        agent_sessions[session_id] = EnhancedChatAgent()
    return agent_sessions[session_id]


def map_stage_to_phase(stage: str) -> TransplantPhase:
    """将后端的 stage 枚举映射到 TransplantPhase"""
    stage_mapping = {
        "PRETREATMENT": TransplantPhase.PREP,
        "TRANSPLANT": TransplantPhase.KEY,
        "RECOVERY": TransplantPhase.RECOVERY,
    }
    return stage_mapping.get(stage, TransplantPhase.PREP)


def build_energy_assessment(energy_result: Optional[Dict]) -> Dict[str, Any]:
    """构建符合 API 规范的心理能量评估对象"""
    if not energy_result:
        return {
            "cognitiveGrowth": 0,
            "emotionRegulation": 0,
            "behaviorChange": 0,
            "socialConnection": 0,
            "selfEfficacy": 0,
            "totalDelta": 0,
            "hopeTreeExpDelta": 0,
            "assessmentNote": "未进行能量评估"
        }
    
    dimension_gains = energy_result.get("dimension_gains", {})
    
    # 映射维度名称到 API 字段
    cognitive = dimension_gains.get("认知成长", 0)
    emotional = dimension_gains.get("情绪调节", 0)
    behavioral = dimension_gains.get("行为改变", 0)
    social = dimension_gains.get("社交连接", 0)
    efficacy = dimension_gains.get("自我效能", 0)
    
    total_delta = energy_result.get("total_gain", 0)
    
    # 希望之树经验值：与总能量增益正相关，但不超过 15
    hope_tree_exp = min(15, int(total_delta * 1.2))
    
    return {
        "cognitiveGrowth": cognitive,
        "emotionRegulation": emotional,
        "behaviorChange": behavioral,
        "socialConnection": social,
        "selfEfficacy": efficacy,
        "totalDelta": total_delta,
        "hopeTreeExpDelta": hope_tree_exp,
        "assessmentNote": f"本轮对话获得 {total_delta} 点心理能量"
    }


def build_crisis_assessment(crisis_detection: Dict, cbt_analysis: Dict) -> Dict[str, Any]:
    """构建符合 API 规范的危机评估对象"""
    alert = crisis_detection.get("alert", False)
    
    # 提取情绪信号
    emotional_state = cbt_analysis.get("emotional_state", {})
    emotion = emotional_state.get("primary", "")
    severity = emotional_state.get("severity", 0)
    
    emotion_signals = []
    if emotion == "anxiety":
        if severity >= 7:
            emotion_signals.append("重度焦虑")
        elif severity >= 5:
            emotion_signals.append("中度焦虑")
        else:
            emotion_signals.append("轻度焦虑")
    elif emotion == "sadness":
        if severity >= 5:
            emotion_signals.append("中度抑郁")
        else:
            emotion_signals.append("轻度抑郁")
    elif emotion == "hopelessness":
        emotion_signals.append("绝望感")
    elif emotion == "anger":
        emotion_signals.append("愤怒")
    elif emotion in ["joy", "calm", "hope"]:
        emotion_signals.append("积极应对")
    
    # 危机等级和行动
    if alert:
        crisis_level = "critical"
        action = "alert_and_notify"
        crisis_keywords = ["危机信号"]
    elif severity >= 7:
        crisis_level = "warning"
        action = "mindfulness_guide"
        crisis_keywords = []
    elif severity >= 5:
        crisis_level = "watch"
        action = "log_only"
        crisis_keywords = []
    else:
        crisis_level = "none"
        action = "none"
        crisis_keywords = []
    
    # 正念引导（当 action = mindfulness_guide 时）
    mindfulness_guide = None
    if action == "mindfulness_guide":
        mindfulness_guide = {
            "type": "breathing",
            "title": "4-7-8 放松呼吸练习",
            "instruction": "我们一起来做个呼吸练习好吗？用鼻子吸气4秒，屏住呼吸7秒，再用嘴慢慢呼出8秒。让我们重复几次，感受身体慢慢放松下来...",
            "durationSeconds": 120,
            "mediaUrl": None
        }
    
    return {
        "crisisAlert": alert,
        "crisisLevel": crisis_level,
        "crisisKeywords": crisis_keywords,
        "emotionSignals": emotion_signals,
        "action": action,
        "mindfulnessGuide": mindfulness_guide
    }


@app.route('/v1/psych/chat', methods=['POST'])
@require_api_key
def psych_chat():
    """
    心理陪护智能体对话接口
    POST /v1/psych/chat
    """
    try:
        data = request.get_json()
        
        # 提取请求参数
        session_id = data.get("sessionId")
        patient_context = data.get("patientContext", {})
        history = data.get("history", [])
        message = data.get("message", "")
        
        if not session_id or not message:
            return jsonify({
                "error": "invalid_request",
                "message": "sessionId 和 message 不能为空"
            }), 400
        
        # 获取或创建 Agent
        agent = get_or_create_agent(session_id)
        
        # 更新患者上下文
        stage = patient_context.get("stage", "PRETREATMENT")
        phase = map_stage_to_phase(stage)
        agent.set_transplant_phase(phase)
        
        # 重建对话历史（如果需要）
        # 注意：这里简化处理，实际可能需要更复杂的历史管理
        if history and len(agent.conversation_history) <= 1:
            # 只有 system prompt，需要加载历史
            for msg in history:
                agent.conversation_history.append({
                    "role": msg["role"],
                    "content": msg["content"]
                })
        
        # 调用 Agent 进行对话
        result = agent.chat(message)
        
        # 构建响应
        response = {
            "reply": result["response"],
            "energyAssessment": build_energy_assessment(result.get("energy_assessment")),
            "crisisAssessment": build_crisis_assessment(
                result.get("crisis_detection", {}),
                result.get("cbt_analysis", {})
            ),
            "recommendedQuestions": [
                "现在最让你担心的是什么？",
                "想做一个让心情平静下来的呼吸练习吗？",
                "今天有什么让你感到温暖的事情吗？"
            ],
            "agentMeta": {
                "model": Config.MODEL_NAME,
                "tokensUsed": 0,  # 可以从 API 响应中获取
                "latencyMs": 0    # 可以计时
            }
        }
        
        # 自动保存进度
        if Config.AUTO_SAVE_PROGRESS:
            agent.save_all_progress()
        
        return jsonify(response), 200
        
    except Exception as e:
        print(f"处理请求时发生错误: {str(e)}")
        traceback.print_exc()
        return jsonify({
            "error": "internal_error",
            "message": f"服务器内部错误: {str(e)}"
        }), 500


@app.route('/v1/psych/recommendations', methods=['POST'])
@require_api_key
def psych_recommendations():
    """
    推荐提问接口
    POST /v1/psych/recommendations
    """
    try:
        data = request.get_json()
        
        patient_context = data.get("patientContext", {})
        recent_history = data.get("recentHistory", [])
        
        # 根据患者状态和历史生成推荐问题
        stage = patient_context.get("stage", "PRETREATMENT")
        psych_energy = patient_context.get("psychEnergy", 50)
        
        # 基础问题库
        questions = []
        
        # 根据心理能量调整问题
        if psych_energy < 30:
            questions.extend([
                "今天感觉怎么样？有什么特别难受的地方吗？",
                "想聊聊现在最困扰你的事情吗？",
                "要不要一起做个放松练习？"
            ])
        elif psych_energy < 60:
            questions.extend([
                "今天心情怎么样？",
                "身体上有什么新的不适感吗？",
                "想聊聊对下一步治疗的感受吗？"
            ])
        else:
            questions.extend([
                "今天有什么让你感到开心的事情吗？",
                "想分享一下最近的进步吗？",
                "有什么想和我聊的话题吗？"
            ])
        
        # 根据治疗阶段添加问题
        if stage == "PRETREATMENT":
            questions.append("对即将开始的治疗有什么想法或担心吗？")
        elif stage == "TRANSPLANT":
            questions.append("现在的治疗过程中，哪些方面让你感到最辛苦？")
        else:  # RECOVERY
            questions.append("恢复过程中有什么新的感受想分享吗？")
        
        return jsonify({
            "questions": questions[:4]  # 返回最多4个问题
        }), 200
        
    except Exception as e:
        print(f"处理推荐请求时发生错误: {str(e)}")
        traceback.print_exc()
        return jsonify({
            "error": "internal_error",
            "message": f"服务器内部错误: {str(e)}"
        }), 500


@app.route('/health', methods=['GET'])
def health_check():
    """健康检查接口"""
    return jsonify({
        "status": "healthy",
        "service": "xinya-psych-agent",
        "version": "1.0.0"
    }), 200


@app.errorhandler(404)
def not_found(error):
    return jsonify({
        "error": "not_found",
        "message": "请求的接口不存在"
    }), 404


@app.errorhandler(500)
def internal_error(error):
    return jsonify({
        "error": "internal_error",
        "message": "服务器内部错误"
    }), 500


def main():
    """启动 API 服务"""
    print("=" * 60)
    print(" 小芽 Agent API 服务")
    print("=" * 60)
    print(f" 模型: {Config.MODEL_NAME}")
    print(f" API Base URL: {Config.API_BASE_URL}")
    print(" 可用接口:")
    print("   POST /v1/psych/chat - 心理陪护对话")
    print("   POST /v1/psych/recommendations - 推荐提问")
    print("   GET  /health - 健康检查")
    print("=" * 60)
    print()
    
    # 启动服务
    app.run(
        host='0.0.0.0',
        port=8001,
        debug=False,
        threaded=True
    )


if __name__ == '__main__':
    main()
