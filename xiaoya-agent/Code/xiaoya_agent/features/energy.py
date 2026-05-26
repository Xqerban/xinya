"""
心理能量评估模型
将对话质量转化为生长积分
"""
from typing import Dict, List, Optional
import json
import os
from datetime import datetime
from xiaoya_agent.config import Config
from xiaoya_agent.database import database_storage_enabled, get_database_repository
from xiaoya_agent.keywords.library import (
    ENERGY_COGNITIVE_INDICATORS,
    ENERGY_COGNITIVE_DEEPENING_KEYWORDS,
    ENERGY_EMOTIONAL_WORDS,
    ENERGY_BEHAVIOR_INDICATORS,
    ENERGY_BEHAVIOR_PLAN_KEYWORDS,
    ENERGY_SOCIAL_INDICATORS,
    ENERGY_HELP_SEEKING_KEYWORDS,
    ENERGY_EFFICACY_INDICATORS,
    ENERGY_PROBLEM_SOLVING_KEYWORDS,
    ENERGY_MINDFULNESS_INDICATORS,
    ENERGY_COGNITIVE_RESTRUCTURE_INDICATORS,
    ENERGY_BEHAVIORAL_ACTIVATION_INDICATORS,
    POSITIVE_EMOTION_LABELS,
    contains_any,
)

class EnergyDimension:
    """能量维度枚举"""
    COGNITIVE = "认知成长"      # 认知重构和思维改变
    EMOTIONAL = "情绪调节"      # 情绪管理能力
    BEHAVIORAL = "行为改变"     # 实际行动和改变
    SOCIAL = "社交连接"        # 人际关系改善
    SELF_EFFICACY = "自我效能"  # 自信心和掌控感

class EnergyLevel:
    """能量等级"""
    def __init__(self, name: str, min_score: int, max_score: int, description: str):
        self.name = name
        self.min_score = min_score
        self.max_score = max_score
        self.description = description

    def contains(self, score: int) -> bool:
        return self.min_score <= score <= self.max_score

# 定义能量等级
ENERGY_LEVELS = [
    EnergyLevel("萌芽", 0, 100, "刚刚开始心理成长之旅"),
    EnergyLevel("生长", 101, 300, "开始展现积极变化"),
    EnergyLevel("茁壮", 301, 600, "心理能量稳步提升"),
    EnergyLevel("旺盛", 601, 1000, "心理状态健康积极"),
    EnergyLevel("绽放", 1001, 1500, "心理能量全面绽放"),
    EnergyLevel("和谐", 1501, 2000, "达到心理和谐状态")
]

class PsychologicalEnergyModel:
    """心理能量评估模型"""

    def __init__(self, data_dir: Optional[str] = None):
        """初始化能量模型"""
        self.data_dir = data_dir or Config.DATA_DIR
        self.total_energy = 0
        self.dimension_scores = {
            EnergyDimension.COGNITIVE: 0,
            EnergyDimension.EMOTIONAL: 0,
            EnergyDimension.BEHAVIORAL: 0,
            EnergyDimension.SOCIAL: 0,
            EnergyDimension.SELF_EFFICACY: 0
        }

        self.session_history = []  # 会话历史记录
        self.achievements = []     # 成就系统
        self.energy_trends = []    # 能量趋势

        # 成就追踪计数器
        self.achievement_counters = {
            "mindfulness_count": 0,        # 正念练习次数
            "cognitive_restructure_count": 0,  # 认知重构次数
            "behavioral_activation_count": 0,  # 行为激活次数
            "consecutive_days": 0,         # 连续对话天数
            "last_session_date": None,     # 上次对话日期
            "positive_emotion_count": 0,   # 积极情绪次数
            "crisis_overcome_count": 0,    # 克服危机次数
            "high_quality_session_count": 0,  # 高质量对话次数
            "dimension_milestones": {      # 各维度里程碑
                EnergyDimension.COGNITIVE: 0,
                EnergyDimension.EMOTIONAL: 0,
                EnergyDimension.BEHAVIORAL: 0,
                EnergyDimension.SOCIAL: 0,
                EnergyDimension.SELF_EFFICACY: 0
            }
        }

        # 加载成就定义
        self.achievements_definitions = self._load_achievements()

    def _load_achievements(self) -> Dict[str, Dict]:
        """加载成就定义"""
        return {
            # === 基础成就 ===
            "first_step": {
                "name": "第一步",
                "description": "完成第一次对话",
                "category": "基础",
                "threshold": 1,
                "reward": 10,
                "check_type": "session_count"
            },
            "early_bird": {
                "name": "早起的鸟儿",
                "description": "完成5次对话",
                "category": "基础",
                "threshold": 5,
                "reward": 20,
                "check_type": "session_count"
            },
            "persistent_soul": {
                "name": "坚持者",
                "description": "完成20次对话",
                "category": "基础",
                "threshold": 20,
                "reward": 50,
                "check_type": "session_count"
            },
            "veteran": {
                "name": "老兵",
                "description": "完成50次对话",
                "category": "基础",
                "threshold": 50,
                "reward": 100,
                "check_type": "session_count"
            },
            "centurion": {
                "name": "百战勇士",
                "description": "完成100次对话",
                "category": "基础",
                "threshold": 100,
                "reward": 200,
                "check_type": "session_count"
            },

            # === 认知成长成就 ===
            "thought_challenger": {
                "name": "思维挑战者",
                "description": "成功进行5次认知重构",
                "category": "认知",
                "threshold": 5,
                "reward": 30,
                "check_type": "cognitive_restructure"
            },
            "cognitive_breaker": {
                "name": "思维突破者",
                "description": "成功进行15次认知重构",
                "category": "认知",
                "threshold": 15,
                "reward": 80,
                "check_type": "cognitive_restructure"
            },
            "wisdom_seeker": {
                "name": "智慧探索者",
                "description": "认知成长维度达到100分",
                "category": "认知",
                "threshold": 100,
                "reward": 50,
                "check_type": "dimension_cognitive"
            },
            "master_thinker": {
                "name": "思维大师",
                "description": "认知成长维度达到300分",
                "category": "认知",
                "threshold": 300,
                "reward": 150,
                "check_type": "dimension_cognitive"
            },

            # === 情绪调节成就 ===
            "emotion_aware": {
                "name": "情绪觉察者",
                "description": "情绪调节维度达到50分",
                "category": "情绪",
                "threshold": 50,
                "reward": 30,
                "check_type": "dimension_emotional"
            },
            "emotion_master": {
                "name": "情绪大师",
                "description": "情绪调节维度达到200分",
                "category": "情绪",
                "threshold": 200,
                "reward": 100,
                "check_type": "dimension_emotional"
            },
            "calm_heart": {
                "name": "平静之心",
                "description": "连续5次对话保持积极情绪",
                "category": "情绪",
                "threshold": 5,
                "reward": 60,
                "check_type": "positive_emotion"
            },
            "emotion_sage": {
                "name": "情绪智者",
                "description": "情绪调节维度达到500分",
                "category": "情绪",
                "threshold": 500,
                "reward": 200,
                "check_type": "dimension_emotional"
            },

            # === 行为改变成就 ===
            "action_starter": {
                "name": "行动派",
                "description": "完成5次行为激活练习",
                "category": "行为",
                "threshold": 5,
                "reward": 30,
                "check_type": "behavioral_activation"
            },
            "behavior_champion": {
                "name": "行为冠军",
                "description": "完成20次行为激活练习",
                "category": "行为",
                "threshold": 20,
                "reward": 100,
                "check_type": "behavioral_activation"
            },
            "habit_builder": {
                "name": "习惯建造者",
                "description": "连续7天进行对话",
                "category": "行为",
                "threshold": 7,
                "reward": 80,
                "check_type": "consecutive_days"
            },
            "consistency_king": {
                "name": "坚持之王",
                "description": "连续30天进行对话",
                "category": "行为",
                "threshold": 30,
                "reward": 250,
                "check_type": "consecutive_days"
            },

            # === 正念练习成就 ===
            "mindful_moment": {
                "name": "正念时刻",
                "description": "完成5次正念练习",
                "category": "正念",
                "threshold": 5,
                "reward": 25,
                "check_type": "mindfulness"
            },
            "zen_master": {
                "name": "禅修大师",
                "description": "完成20次正念练习",
                "category": "正念",
                "threshold": 20,
                "reward": 100,
                "check_type": "mindfulness"
            },
            "mindfulness_guru": {
                "name": "正念导师",
                "description": "完成50次正念练习",
                "category": "正念",
                "threshold": 50,
                "reward": 200,
                "check_type": "mindfulness"
            },

            # === 社交连接成就 ===
            "social_butterfly": {
                "name": "社交蝴蝶",
                "description": "社交连接维度达到50分",
                "category": "社交",
                "threshold": 50,
                "reward": 40,
                "check_type": "dimension_social"
            },
            "connection_builder": {
                "name": "连接建造者",
                "description": "社交连接维度达到150分",
                "category": "社交",
                "threshold": 150,
                "reward": 100,
                "check_type": "dimension_social"
            },

            # === 自我效能成就 ===
            "confidence_boost": {
                "name": "自信提升",
                "description": "自我效能维度达到50分",
                "category": "效能",
                "threshold": 50,
                "reward": 40,
                "check_type": "dimension_self_efficacy"
            },
            "self_belief": {
                "name": "自我信念",
                "description": "自我效能维度达到200分",
                "category": "效能",
                "threshold": 200,
                "reward": 120,
                "check_type": "dimension_self_efficacy"
            },

            # === 危机克服成就 ===
            "crisis_survivor": {
                "name": "危机幸存者",
                "description": "成功度过1次危机时刻",
                "category": "韧性",
                "threshold": 1,
                "reward": 50,
                "check_type": "crisis_overcome"
            },
            "resilience_warrior": {
                "name": "韧性战士",
                "description": "成功度过5次危机时刻",
                "category": "韧性",
                "threshold": 5,
                "reward": 150,
                "check_type": "crisis_overcome"
            },
            "phoenix_rising": {
                "name": "浴火重生",
                "description": "成功度过10次危机时刻",
                "category": "韧性",
                "threshold": 10,
                "reward": 300,
                "check_type": "crisis_overcome"
            },

            # === 能量里程碑成就 ===
            "energy_awakening": {
                "name": "能量觉醒",
                "description": "累计获得500点能量",
                "category": "里程碑",
                "threshold": 500,
                "reward": 100,
                "check_type": "total_energy"
            },
            "growth_explorer": {
                "name": "成长探索者",
                "description": "累计获得1000点能量",
                "category": "里程碑",
                "threshold": 1000,
                "reward": 200,
                "check_type": "total_energy"
            },
            "energy_titan": {
                "name": "能量巨人",
                "description": "累计获得2000点能量",
                "category": "里程碑",
                "threshold": 2000,
                "reward": 400,
                "check_type": "total_energy"
            },

            # === 质量成就 ===
            "quality_seeker": {
                "name": "质量追求者",
                "description": "完成10次高质量对话",
                "category": "质量",
                "threshold": 10,
                "reward": 60,
                "check_type": "high_quality_session"
            },
            "excellence_master": {
                "name": "卓越大师",
                "description": "完成30次高质量对话",
                "category": "质量",
                "threshold": 30,
                "reward": 150,
                "check_type": "high_quality_session"
            },

            # === 特殊成就 ===
            "all_rounder": {
                "name": "全能发展",
                "description": "所有维度均达到100分",
                "category": "特殊",
                "threshold": 100,
                "reward": 300,
                "check_type": "all_dimensions"
            },
            "harmony_achiever": {
                "name": "和谐达成者",
                "description": "达到和谐等级（1501+能量）",
                "category": "特殊",
                "threshold": 1501,
                "reward": 500,
                "check_type": "total_energy"
            }
        }

    def assess_conversation_quality(self, conversation_data: Dict) -> Dict[str, any]:
        """
        评估对话质量并计算能量积分

        Args:
            conversation_data: 对话数据，包含用户输入、分析结果等

        Returns:
            评估结果字典
        """
        user_message = conversation_data.get("user_message", "")
        analysis = conversation_data.get("analysis", {})
        cbt_response = conversation_data.get("cbt_response", "")

        # 计算各维度得分
        dimension_gains = self._calculate_dimension_gains(user_message, analysis, cbt_response)

        # 计算总能量增益
        total_gain = sum(dimension_gains.values())

        # 应用能量倍数器（基于连续性等因素）
        multiplier = self._calculate_multiplier(conversation_data)
        final_gain = int(total_gain * multiplier)

        # 更新各维度得分
        for dimension, gain in dimension_gains.items():
            self.dimension_scores[dimension] += gain

        # 更新总能量
        self.total_energy += final_gain

        # 记录会话历史
        session_record = {
            "timestamp": datetime.now().isoformat(),
            "user_message": user_message,
            "dimension_gains": dimension_gains,
            "total_gain": final_gain,
            "multiplier": multiplier,
            "analysis": analysis
        }
        self.session_history.append(session_record)

        # 更新成就计数器
        self._update_achievement_counters(user_message, analysis, final_gain)

        # 检查成就解锁
        new_achievements = self._check_achievements()

        # 更新能量趋势
        self._update_trends(final_gain)

        return {
            "dimension_gains": dimension_gains,
            "total_gain": final_gain,
            "multiplier": multiplier,
            "new_achievements": new_achievements,
            "current_level": self.get_current_level(),
            "level_progress": self.get_level_progress()
        }

    def apply_llm_assessment(self, conversation_data: Dict, llm_energy: Dict) -> Dict[str, any]:
        """
        应用后台大模型给出的结构化心理能量评估。

        这里不再根据用户原话做关键词判断；模型负责判断本轮在哪些维度有成长，
        代码只做数值裁剪、连续性倍数、成就计数和持久化状态更新。
        """
        user_message = conversation_data.get("user_message", "")
        analysis = conversation_data.get("analysis", {})
        llm_energy = llm_energy or {}

        dimension_gains = {
            EnergyDimension.COGNITIVE: self._clamp_gain(llm_energy.get("cognitive_growth"), 0, 20),
            EnergyDimension.EMOTIONAL: self._clamp_gain(llm_energy.get("emotion_regulation"), 0, 25),
            EnergyDimension.BEHAVIORAL: self._clamp_gain(llm_energy.get("behavior_change"), 0, 20),
            EnergyDimension.SOCIAL: self._clamp_gain(llm_energy.get("social_connection"), 0, 15),
            EnergyDimension.SELF_EFFICACY: self._clamp_gain(llm_energy.get("self_efficacy"), 0, 20),
        }

        total_gain = sum(dimension_gains.values())
        multiplier = self._calculate_multiplier(conversation_data)
        final_gain = int(total_gain * multiplier)

        for dimension, gain in dimension_gains.items():
            self.dimension_scores[dimension] += gain

        self.total_energy += final_gain

        session_record = {
            "timestamp": datetime.now().isoformat(),
            "user_message": user_message,
            "dimension_gains": dimension_gains,
            "total_gain": final_gain,
            "multiplier": multiplier,
            "analysis": analysis,
            "assessment_note": str(llm_energy.get("assessment_note") or ""),
            "source": "llm_semantic_background",
        }
        self.session_history.append(session_record)

        self._update_achievement_counters_from_semantic(
            analysis=analysis,
            final_gain=final_gain,
            achievement_signals=llm_energy.get("achievement_signals") or {},
        )
        new_achievements = self._check_achievements()
        self._update_trends(final_gain)

        return {
            "dimension_gains": dimension_gains,
            "total_gain": final_gain,
            "multiplier": multiplier,
            "new_achievements": new_achievements,
            "current_level": self.get_current_level(),
            "level_progress": self.get_level_progress(),
            "assessment_note": str(llm_energy.get("assessment_note") or ""),
            "source": "llm_semantic_background",
        }

    def _clamp_gain(self, value, low: int, high: int) -> int:
        try:
            number = int(value or 0)
        except Exception:
            number = 0
        return max(low, min(high, number))

    def _calculate_dimension_gains(self, user_message: str, analysis: Dict, cbt_response: str) -> Dict[str, int]:
        """计算各维度的能量增益"""
        gains = {}

        # 认知成长维度
        cognitive_gain = self._assess_cognitive_growth(user_message, analysis)
        gains[EnergyDimension.COGNITIVE] = cognitive_gain

        # 情绪调节维度
        emotional_gain = self._assess_emotional_growth(analysis)
        gains[EnergyDimension.EMOTIONAL] = emotional_gain

        # 行为改变维度
        behavioral_gain = self._assess_behavioral_growth(user_message)
        gains[EnergyDimension.BEHAVIORAL] = behavioral_gain

        # 社交连接维度
        social_gain = self._assess_social_growth(user_message)
        gains[EnergyDimension.SOCIAL] = social_gain

        # 自我效能维度
        efficacy_gain = self._assess_self_efficacy_growth(user_message, analysis)
        gains[EnergyDimension.SELF_EFFICACY] = efficacy_gain

        return gains

    def _assess_cognitive_growth(self, user_message: str, analysis: Dict) -> int:
        """评估认知成长"""
        base_score = 0

        # 检查是否有认知重构迹象
        for indicator in ENERGY_COGNITIVE_INDICATORS:
            if indicator in user_message:
                base_score += 5

        # 检查是否识别了认知扭曲
        distortions = analysis.get("cognitive_distortions", [])
        base_score += len(distortions) * 3

        # 检查反思深度
        if contains_any(user_message, ENERGY_COGNITIVE_DEEPENING_KEYWORDS):
            base_score += 2

        return min(base_score, 20)  # 最大20分

    def _assess_emotional_growth(self, analysis: Dict) -> int:
        """评估情绪调节"""
        base_score = 0

        emotional_state = analysis.get("emotional_state", {})
        severity = emotional_state.get("severity", 5)

        # 负面情绪减轻得分
        if severity <= 3:
            base_score += 15
        elif severity <= 5:
            base_score += 10
        elif severity <= 7:
            base_score += 5

        # 情绪词汇丰富度
        for word in ENERGY_EMOTIONAL_WORDS:
            if word in str(analysis):
                base_score += 1

        return min(base_score, 25)  # 最大25分

    def _assess_behavioral_growth(self, user_message: str) -> int:
        """评估行为改变"""
        base_score = 0

        # 检查行为意图
        for indicator in ENERGY_BEHAVIOR_INDICATORS:
            if indicator in user_message:
                base_score += 4

        # 检查具体计划
        if contains_any(user_message, ENERGY_BEHAVIOR_PLAN_KEYWORDS):
            base_score += 3

        return min(base_score, 20)  # 最大20分

    def _assess_social_growth(self, user_message: str) -> int:
        """评估社交连接"""
        base_score = 0

        # 检查社交相关内容
        for indicator in ENERGY_SOCIAL_INDICATORS:
            if indicator in user_message:
                base_score += 3

        # 检查寻求帮助
        if contains_any(user_message, ENERGY_HELP_SEEKING_KEYWORDS):
            base_score += 5

        return min(base_score, 15)  # 最大15分

    def _assess_self_efficacy_growth(self, user_message: str, analysis: Dict) -> int:
        """评估自我效能"""
        base_score = 0

        # 检查自信表达
        for indicator in ENERGY_EFFICACY_INDICATORS:
            if indicator in user_message:
                base_score += 4

        # 检查问题解决能力
        if contains_any(user_message, ENERGY_PROBLEM_SOLVING_KEYWORDS):
            base_score += 5

        # 负面情绪下仍保持积极
        emotional_state = analysis.get("emotional_state", {})
        if emotional_state.get("severity", 0) >= 6:
            if contains_any(user_message, ENERGY_EFFICACY_INDICATORS):
                base_score += 8  # 额外奖励

        return min(base_score, 20)  # 最大20分

    def _calculate_multiplier(self, conversation_data: Dict) -> float:
        """计算能量倍数器"""
        multiplier = 1.0

        # 连续对话奖励
        recent_sessions = self.session_history[-5:]  # 最近5次会话
        if len(recent_sessions) >= 3:
            # 检查是否在短时间内连续对话
            timestamps = [datetime.fromisoformat(s["timestamp"]) for s in recent_sessions]
            time_diffs = [(timestamps[i] - timestamps[i-1]).total_seconds() / 3600
                         for i in range(1, len(timestamps))]

            if all(diff < 24 for diff in time_diffs):  # 24小时内
                multiplier *= 1.2

        # 进步加速奖励
        if len(self.energy_trends) >= 3:
            recent_trends = self.energy_trends[-3:]
            if all(trend > 0 for trend in recent_trends):
                multiplier *= 1.1

        # 深度对话奖励
        user_message = conversation_data.get("user_message", "")
        if len(user_message) > 100:  # 长对话
            multiplier *= 1.05

        return min(multiplier, 2.0)  # 最大2倍

    def _update_achievement_counters(self, user_message: str, analysis: Dict, final_gain: int):
        """更新成就计数器"""
        # 1. 更新连续对话天数
        current_date = datetime.now().date()
        if self.achievement_counters["last_session_date"]:
            last_date = datetime.fromisoformat(self.achievement_counters["last_session_date"]).date()
            days_diff = (current_date - last_date).days
            
            if days_diff == 1:
                # 连续的一天
                self.achievement_counters["consecutive_days"] += 1
            elif days_diff == 0:
                # 同一天，不增加
                pass
            else:
                # 中断了，重置为1
                self.achievement_counters["consecutive_days"] = 1
        else:
            # 第一次对话
            self.achievement_counters["consecutive_days"] = 1
        
        self.achievement_counters["last_session_date"] = current_date.isoformat()

        # 2. 检测认知重构
        if contains_any(user_message, ENERGY_COGNITIVE_RESTRUCTURE_INDICATORS):
            distortions = analysis.get("cognitive_distortions", [])
            if distortions:  # 有认知扭曲且进行了重构
                self.achievement_counters["cognitive_restructure_count"] += 1

        # 3. 检测正念练习
        if contains_any(user_message, ENERGY_MINDFULNESS_INDICATORS):
            # 检查是否推荐了正念技术
            recommended = analysis.get("recommended_technique")
            if recommended and "MINDFULNESS" in str(recommended):
                self.achievement_counters["mindfulness_count"] += 1

        # 4. 检测行为激活
        if contains_any(user_message, ENERGY_BEHAVIORAL_ACTIVATION_INDICATORS):
            recommended = analysis.get("recommended_technique")
            if recommended and "BEHAVIORAL_ACTIVATION" in str(recommended):
                self.achievement_counters["behavioral_activation_count"] += 1

        # 5. 检测积极情绪
        emotional_state = analysis.get("emotional_state", {})
        primary_emotion = emotional_state.get("primary", "")
        severity = emotional_state.get("severity", 5)
        
        if primary_emotion in POSITIVE_EMOTION_LABELS or severity <= 3:
            self.achievement_counters["positive_emotion_count"] += 1
        else:
            # 重置连续积极情绪计数
            self.achievement_counters["positive_emotion_count"] = 0

        # 6. 检测危机克服（从高危机状态恢复）
        if len(self.session_history) >= 2:
            # 检查上一次是否是危机状态
            prev_session = self.session_history[-2]
            prev_analysis = prev_session.get("analysis", {})
            prev_emotional = prev_analysis.get("emotional_state", {})
            prev_severity = prev_emotional.get("severity", 0)
            
            # 如果上次严重度>=8，这次<=5，认为克服了危机
            if prev_severity >= 8 and severity <= 5:
                self.achievement_counters["crisis_overcome_count"] += 1

        # 7. 检测高质量对话（能量增益高）
        if final_gain >= 50:  # 单次获得50+能量视为高质量对话
            self.achievement_counters["high_quality_session_count"] += 1

        # 8. 更新维度里程碑
        for dimension, score in self.dimension_scores.items():
            milestones = [50, 100, 200, 300, 500]
            current_milestone = self.achievement_counters["dimension_milestones"].get(dimension, 0)
            
            for milestone in milestones:
                if score >= milestone and current_milestone < milestone:
                    self.achievement_counters["dimension_milestones"][dimension] = milestone
                    break

    def _update_achievement_counters_from_semantic(
        self,
        analysis: Dict,
        final_gain: int,
        achievement_signals: Dict,
    ):
        """根据 LLM 结构化信号更新成就计数器，不读取用户原话关键词。"""
        current_date = datetime.now().date()
        if self.achievement_counters["last_session_date"]:
            last_date = datetime.fromisoformat(self.achievement_counters["last_session_date"]).date()
            days_diff = (current_date - last_date).days

            if days_diff == 1:
                self.achievement_counters["consecutive_days"] += 1
            elif days_diff == 0:
                pass
            else:
                self.achievement_counters["consecutive_days"] = 1
        else:
            self.achievement_counters["consecutive_days"] = 1

        self.achievement_counters["last_session_date"] = current_date.isoformat()

        signals = achievement_signals or {}
        if signals.get("cognitive_restructure"):
            self.achievement_counters["cognitive_restructure_count"] += 1
        if signals.get("mindfulness_practice"):
            self.achievement_counters["mindfulness_count"] += 1
        if signals.get("behavioral_activation"):
            self.achievement_counters["behavioral_activation_count"] += 1

        emotional_state = analysis.get("emotional_state", {}) or {}
        primary_emotion = emotional_state.get("primary", "")
        severity = emotional_state.get("severity", 5)
        if signals.get("positive_emotion") or primary_emotion in POSITIVE_EMOTION_LABELS or severity <= 3:
            self.achievement_counters["positive_emotion_count"] += 1
        else:
            self.achievement_counters["positive_emotion_count"] = 0

        if len(self.session_history) >= 2:
            prev_session = self.session_history[-2]
            prev_analysis = prev_session.get("analysis", {})
            prev_emotional = prev_analysis.get("emotional_state", {})
            prev_severity = prev_emotional.get("severity", 0)
            if prev_severity >= 8 and severity <= 5:
                self.achievement_counters["crisis_overcome_count"] += 1

        if final_gain >= 50 or signals.get("high_quality_session"):
            self.achievement_counters["high_quality_session_count"] += 1

        for dimension, score in self.dimension_scores.items():
            milestones = [50, 100, 200, 300, 500]
            current_milestone = self.achievement_counters["dimension_milestones"].get(dimension, 0)

            for milestone in milestones:
                if score >= milestone and current_milestone < milestone:
                    self.achievement_counters["dimension_milestones"][dimension] = milestone
                    break

    def _check_achievements(self) -> List[Dict]:
        """检查新解锁的成就"""
        new_achievements = []
        unlocked_ids = [a["id"] for a in self.achievements]

        # 遍历所有成就定义
        for achievement_id, achievement_def in self.achievements_definitions.items():
            # 跳过已解锁的成就
            if achievement_id in unlocked_ids:
                continue

            check_type = achievement_def.get("check_type", "")
            threshold = achievement_def.get("threshold", 0)
            should_unlock = False

            # 根据检查类型判断是否解锁
            if check_type == "session_count":
                should_unlock = len(self.session_history) >= threshold

            elif check_type == "total_energy":
                should_unlock = self.total_energy >= threshold

            elif check_type == "cognitive_restructure":
                should_unlock = self.achievement_counters["cognitive_restructure_count"] >= threshold

            elif check_type == "mindfulness":
                should_unlock = self.achievement_counters["mindfulness_count"] >= threshold

            elif check_type == "behavioral_activation":
                should_unlock = self.achievement_counters["behavioral_activation_count"] >= threshold

            elif check_type == "consecutive_days":
                should_unlock = self.achievement_counters["consecutive_days"] >= threshold

            elif check_type == "positive_emotion":
                should_unlock = self.achievement_counters["positive_emotion_count"] >= threshold

            elif check_type == "crisis_overcome":
                should_unlock = self.achievement_counters["crisis_overcome_count"] >= threshold

            elif check_type == "high_quality_session":
                should_unlock = self.achievement_counters["high_quality_session_count"] >= threshold

            elif check_type == "dimension_cognitive":
                should_unlock = self.dimension_scores.get(EnergyDimension.COGNITIVE, 0) >= threshold

            elif check_type == "dimension_emotional":
                should_unlock = self.dimension_scores.get(EnergyDimension.EMOTIONAL, 0) >= threshold

            elif check_type == "dimension_behavioral":
                should_unlock = self.dimension_scores.get(EnergyDimension.BEHAVIORAL, 0) >= threshold

            elif check_type == "dimension_social":
                should_unlock = self.dimension_scores.get(EnergyDimension.SOCIAL, 0) >= threshold

            elif check_type == "dimension_self_efficacy":
                should_unlock = self.dimension_scores.get(EnergyDimension.SELF_EFFICACY, 0) >= threshold

            elif check_type == "all_dimensions":
                # 所有维度都达到阈值
                should_unlock = all(
                    score >= threshold 
                    for score in self.dimension_scores.values()
                )

            # 解锁成就
            if should_unlock:
                achievement = achievement_def.copy()
                achievement["id"] = achievement_id
                achievement["unlocked_at"] = datetime.now().isoformat()
                self.achievements.append(achievement)
                self.total_energy += achievement["reward"]
                new_achievements.append(achievement)

        return new_achievements

    def _update_trends(self, recent_gain: int):
        """更新能量趋势"""
        self.energy_trends.append(recent_gain)

        # 保持最近10次的趋势
        if len(self.energy_trends) > 10:
            self.energy_trends = self.energy_trends[-10:]

    def get_current_level(self) -> EnergyLevel:
        """获取当前能量等级"""
        for level in reversed(ENERGY_LEVELS):
            if level.contains(self.total_energy):
                return level
        return ENERGY_LEVELS[0]

    def get_level_progress(self) -> float:
        """获取等级进度百分比"""
        current_level = self.get_current_level()
        level_range = current_level.max_score - current_level.min_score
        if level_range == 0:
            return 100.0

        progress_in_level = self.total_energy - current_level.min_score
        return min(100.0, (progress_in_level / level_range) * 100)

    def get_energy_report(self) -> Dict[str, any]:
        """获取完整的能量报告"""
        current_level = self.get_current_level()

        return {
            "total_energy": self.total_energy,
            "current_level": {
                "name": current_level.name,
                "description": current_level.description
            },
            "level_progress": self.get_level_progress(),
            "dimension_scores": self.dimension_scores,
            "achievements": self.achievements,
            "achievement_stats": self.get_achievement_stats(),
            "session_count": len(self.session_history),
            "recent_trend": self.energy_trends[-5:] if self.energy_trends else [],
            "consecutive_days": self.achievement_counters.get("consecutive_days", 0)
        }

    def get_achievement_stats(self) -> Dict[str, any]:
        """获取成就统计信息"""
        total_achievements = len(self.achievements_definitions)
        unlocked_achievements = len(self.achievements)
        
        # 按类别统计
        category_stats = {}
        for achievement_id, achievement_def in self.achievements_definitions.items():
            category = achievement_def.get("category", "其他")
            if category not in category_stats:
                category_stats[category] = {"total": 0, "unlocked": 0}
            category_stats[category]["total"] += 1
            
            if achievement_id in [a["id"] for a in self.achievements]:
                category_stats[category]["unlocked"] += 1
        
        # 计算完成度
        completion_rate = (unlocked_achievements / total_achievements * 100) if total_achievements > 0 else 0
        
        # 最近解锁的成就
        recent_achievements = sorted(
            self.achievements,
            key=lambda x: x.get("unlocked_at", ""),
            reverse=True
        )[:5]
        
        return {
            "total_achievements": total_achievements,
            "unlocked_achievements": unlocked_achievements,
            "completion_rate": round(completion_rate, 1),
            "category_stats": category_stats,
            "recent_achievements": recent_achievements,
            "counters": {
                "mindfulness_count": self.achievement_counters.get("mindfulness_count", 0),
                "cognitive_restructure_count": self.achievement_counters.get("cognitive_restructure_count", 0),
                "behavioral_activation_count": self.achievement_counters.get("behavioral_activation_count", 0),
                "consecutive_days": self.achievement_counters.get("consecutive_days", 0),
                "positive_emotion_count": self.achievement_counters.get("positive_emotion_count", 0),
                "crisis_overcome_count": self.achievement_counters.get("crisis_overcome_count", 0),
                "high_quality_session_count": self.achievement_counters.get("high_quality_session_count", 0)
            }
        }

    def get_achievements_by_category(self) -> Dict[str, List[Dict]]:
        """按类别获取成就列表"""
        categorized = {}
        unlocked_ids = [a["id"] for a in self.achievements]
        
        for achievement_id, achievement_def in self.achievements_definitions.items():
            category = achievement_def.get("category", "其他")
            if category not in categorized:
                categorized[category] = []
            
            achievement_info = achievement_def.copy()
            achievement_info["id"] = achievement_id
            achievement_info["unlocked"] = achievement_id in unlocked_ids
            
            # 如果已解锁，添加解锁时间
            if achievement_info["unlocked"]:
                for unlocked_achievement in self.achievements:
                    if unlocked_achievement["id"] == achievement_id:
                        achievement_info["unlocked_at"] = unlocked_achievement.get("unlocked_at")
                        break
            
            # 添加进度信息
            achievement_info["progress"] = self._get_achievement_progress(achievement_id, achievement_def)
            
            categorized[category].append(achievement_info)
        
        return categorized

    def _get_achievement_progress(self, achievement_id: str, achievement_def: Dict) -> Dict[str, any]:
        """获取成就的当前进度"""
        check_type = achievement_def.get("check_type", "")
        threshold = achievement_def.get("threshold", 0)
        current = 0
        
        if check_type == "session_count":
            current = len(self.session_history)
        elif check_type == "total_energy":
            current = self.total_energy
        elif check_type == "cognitive_restructure":
            current = self.achievement_counters.get("cognitive_restructure_count", 0)
        elif check_type == "mindfulness":
            current = self.achievement_counters.get("mindfulness_count", 0)
        elif check_type == "behavioral_activation":
            current = self.achievement_counters.get("behavioral_activation_count", 0)
        elif check_type == "consecutive_days":
            current = self.achievement_counters.get("consecutive_days", 0)
        elif check_type == "positive_emotion":
            current = self.achievement_counters.get("positive_emotion_count", 0)
        elif check_type == "crisis_overcome":
            current = self.achievement_counters.get("crisis_overcome_count", 0)
        elif check_type == "high_quality_session":
            current = self.achievement_counters.get("high_quality_session_count", 0)
        elif check_type.startswith("dimension_"):
            dimension_map = {
                "dimension_cognitive": EnergyDimension.COGNITIVE,
                "dimension_emotional": EnergyDimension.EMOTIONAL,
                "dimension_behavioral": EnergyDimension.BEHAVIORAL,
                "dimension_social": EnergyDimension.SOCIAL,
                "dimension_self_efficacy": EnergyDimension.SELF_EFFICACY
            }
            dimension = dimension_map.get(check_type)
            if dimension:
                current = self.dimension_scores.get(dimension, 0)
        elif check_type == "all_dimensions":
            current = min(self.dimension_scores.values()) if self.dimension_scores else 0
        
        percentage = min(100, (current / threshold * 100)) if threshold > 0 else 0
        
        return {
            "current": current,
            "threshold": threshold,
            "percentage": round(percentage, 1)
        }

    def _serialize_data(self, data):
        """序列化数据，将枚举值转换为字符串"""
        if isinstance(data, dict):
            return {key: self._serialize_data(value) for key, value in data.items()}
        elif isinstance(data, list):
            return [self._serialize_data(item) for item in data]
        elif hasattr(data, 'value'):  # 枚举对象
            return data.value
        else:
            # 对于基本类型（str, int, float, bool, None）和已序列化的数据，直接返回
            return data

    def save_progress(self, filename: str = "energy_progress.json"):
        """保存能量进度"""
        data = {
            "total_energy": self.total_energy,
            "dimension_scores": self.dimension_scores,
            "session_history": self.session_history,
            "achievements": self.achievements,
            "energy_trends": self.energy_trends,
            "achievement_counters": self.achievement_counters,
            "last_updated": datetime.now().isoformat()
        }

        # 序列化数据以确保所有对象都能被 JSON 序列化
        serialized_data = self._serialize_data(data)

        user_id = getattr(self, "user_id", None)
        if database_storage_enabled():
            if user_id:
                get_database_repository().save_energy_progress(
                    user_id=str(user_id),
                    safe_user_id=str(getattr(self, "safe_user_id", None) or user_id),
                    data=serialized_data,
                    psych_model_dir=str(getattr(self, "psych_model_dir", "") or self.data_dir),
                )
            return

        filepath = self._get_filepath(filename)
        with open(filepath, 'w', encoding='utf-8') as f:
            json.dump(serialized_data, f, ensure_ascii=False, indent=2)

    def load_progress(self, filename: str = "energy_progress.json"):
        """加载能量进度"""
        try:
            user_id = getattr(self, "user_id", None)
            if database_storage_enabled():
                if user_id:
                    data = get_database_repository().load_energy_progress(str(user_id))
                    if isinstance(data, dict):
                        self.total_energy = data.get("total_energy", 0)
                        self.dimension_scores = data.get("dimension_scores", self.dimension_scores)
                        self.session_history = data.get("session_history", [])
                        self.achievements = data.get("achievements", [])
                        self.energy_trends = data.get("energy_trends", [])
                        loaded_counters = data.get("achievement_counters", {})
                        if loaded_counters:
                            self.achievement_counters.update(loaded_counters)
                            if "dimension_milestones" not in self.achievement_counters:
                                self.achievement_counters["dimension_milestones"] = {
                                    EnergyDimension.COGNITIVE: 0,
                                    EnergyDimension.EMOTIONAL: 0,
                                    EnergyDimension.BEHAVIORAL: 0,
                                    EnergyDimension.SOCIAL: 0,
                                    EnergyDimension.SELF_EFFICACY: 0
                                }
                return
            filepath = self._get_filepath(filename)
            with open(filepath, 'r', encoding='utf-8') as f:
                data = json.load(f)

            self.total_energy = data.get("total_energy", 0)
            self.dimension_scores = data.get("dimension_scores", self.dimension_scores)
            self.session_history = data.get("session_history", [])
            self.achievements = data.get("achievements", [])
            self.energy_trends = data.get("energy_trends", [])
            
            # 加载成就计数器
            loaded_counters = data.get("achievement_counters", {})
            if loaded_counters:
                self.achievement_counters.update(loaded_counters)
                # 确保维度里程碑字典存在
                if "dimension_milestones" not in self.achievement_counters:
                    self.achievement_counters["dimension_milestones"] = {
                        EnergyDimension.COGNITIVE: 0,
                        EnergyDimension.EMOTIONAL: 0,
                        EnergyDimension.BEHAVIORAL: 0,
                        EnergyDimension.SOCIAL: 0,
                        EnergyDimension.SELF_EFFICACY: 0
                    }

        except FileNotFoundError:
            print(f"进度文件 {filename} 不存在，将创建新进度")
        except Exception as e:
            print(f"加载进度文件失败: {str(e)}")
    def _get_filepath(self, filename: str) -> str:
        """获取文件的完整路径（统一放在配置的数据目录下）"""
        if os.path.isabs(filename):
            return filename
        if not database_storage_enabled():
            os.makedirs(self.data_dir, exist_ok=True)
        return os.path.join(self.data_dir, filename)
