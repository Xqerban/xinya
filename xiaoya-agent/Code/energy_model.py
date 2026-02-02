"""
心理能量评估模型
将对话质量转化为生长积分
"""
from typing import Dict, List, Tuple, Optional
import json
import time
from datetime import datetime, timedelta
import math

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

    def __init__(self):
        """初始化能量模型"""
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

        # 加载成就定义
        self.achievements_definitions = self._load_achievements()

    def _load_achievements(self) -> Dict[str, Dict]:
        """加载成就定义"""
        return {
            "first_step": {
                "name": "第一步",
                "description": "完成第一次对话",
                "threshold": 1,
                "reward": 10
            },
            "mindful_moment": {
                "name": "正念时刻",
                "description": "进行5次正念练习",
                "threshold": 5,
                "reward": 25
            },
            "cognitive_breaker": {
                "name": "思维突破者",
                "description": "成功挑战10个负面思维",
                "threshold": 10,
                "reward": 50
            },
            "emotion_master": {
                "name": "情绪大师",
                "description": "情绪调节得分超过200",
                "threshold": 200,
                "reward": 100
            },
            "behavior_champion": {
                "name": "行为冠军",
                "description": "坚持行为改变30天",
                "threshold": 30,
                "reward": 150
            },
            "growth_explorer": {
                "name": "成长探索者",
                "description": "累计获得1000点能量",
                "threshold": 1000,
                "reward": 200
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
        cognitive_indicators = [
            "我觉得", "我意识到", "我发现", "更平衡的看法",
            "替代想法", "不同角度", "重新思考"
        ]

        for indicator in cognitive_indicators:
            if indicator in user_message:
                base_score += 5

        # 检查是否识别了认知扭曲
        distortions = analysis.get("cognitive_distortions", [])
        base_score += len(distortions) * 3

        # 检查反思深度
        if any(word in user_message for word in ["为什么", "怎么", "如果", "可能"]):
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
        emotional_words = ["感受", "情绪", "心情", "感觉", "平静", "焦虑"]
        for word in emotional_words:
            if word in str(analysis):
                base_score += 1

        return min(base_score, 25)  # 最大25分

    def _assess_behavioral_growth(self, user_message: str) -> int:
        """评估行为改变"""
        base_score = 0

        # 检查行为意图
        behavior_indicators = [
            "我要", "我打算", "我计划", "我会试着",
            "开始做", "继续做", "改变", "行动"
        ]

        for indicator in behavior_indicators:
            if indicator in user_message:
                base_score += 4

        # 检查具体计划
        if any(word in user_message for word in ["明天", "今天", "这个星期", "下次"]):
            base_score += 3

        return min(base_score, 20)  # 最大20分

    def _assess_social_growth(self, user_message: str) -> int:
        """评估社交连接"""
        base_score = 0

        # 检查社交相关内容
        social_indicators = [
            "朋友", "家人", "同事", "老师", "同学",
            "联系", "沟通", "分享", "倾诉", "支持"
        ]

        for indicator in social_indicators:
            if indicator in user_message:
                base_score += 3

        # 检查寻求帮助
        if any(word in user_message for word in ["寻求", "需要", "希望", "想要"]):
            base_score += 5

        return min(base_score, 15)  # 最大15分

    def _assess_self_efficacy_growth(self, user_message: str, analysis: Dict) -> int:
        """评估自我效能"""
        base_score = 0

        # 检查自信表达
        efficacy_indicators = [
            "我能", "我会", "我可以", "我相信",
            "我有能力", "我能处理", "我能应对"
        ]

        for indicator in efficacy_indicators:
            if indicator in user_message:
                base_score += 4

        # 检查问题解决能力
        if any(word in user_message for word in ["解决方案", "办法", "策略", "步骤"]):
            base_score += 5

        # 负面情绪下仍保持积极
        emotional_state = analysis.get("emotional_state", {})
        if emotional_state.get("severity", 0) >= 6:
            if any(indicator in efficacy_indicators for indicator in user_message.split()):
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

    def _check_achievements(self) -> List[Dict]:
        """检查新解锁的成就"""
        new_achievements = []

        # 检查各种成就条件
        session_count = len(self.session_history)

        # 第一步成就
        if session_count == 1 and "first_step" not in [a["id"] for a in self.achievements]:
            achievement = self.achievements_definitions["first_step"].copy()
            achievement["id"] = "first_step"
            achievement["unlocked_at"] = datetime.now().isoformat()
            self.achievements.append(achievement)
            self.total_energy += achievement["reward"]
            new_achievements.append(achievement)

        # 成长探索者成就
        if (self.total_energy >= 1000 and
            "growth_explorer" not in [a["id"] for a in self.achievements]):
            achievement = self.achievements_definitions["growth_explorer"].copy()
            achievement["id"] = "growth_explorer"
            achievement["unlocked_at"] = datetime.now().isoformat()
            self.achievements.append(achievement)
            self.total_energy += achievement["reward"]
            new_achievements.append(achievement)

        # 其他成就检查可以继续添加...

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
            "session_count": len(self.session_history),
            "recent_trend": self.energy_trends[-5:] if self.energy_trends else []
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
            "last_updated": datetime.now().isoformat()
        }

        # 序列化数据以确保所有对象都能被JSON序列化
        serialized_data = self._serialize_data(data)

        with open(filename, 'w', encoding='utf-8') as f:
            json.dump(serialized_data, f, ensure_ascii=False, indent=2)

    def load_progress(self, filename: str = "energy_progress.json"):
        """加载能量进度"""
        try:
            with open(filename, 'r', encoding='utf-8') as f:
                data = json.load(f)

            self.total_energy = data.get("total_energy", 0)
            self.dimension_scores = data.get("dimension_scores", self.dimension_scores)
            self.session_history = data.get("session_history", [])
            self.achievements = data.get("achievements", [])
            self.energy_trends = data.get("energy_trends", [])

        except FileNotFoundError:
            print(f"进度文件 {filename} 不存在，将创建新进度")
        except Exception as e:
            print(f"加载进度文件失败: {str(e)}")