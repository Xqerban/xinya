"""
配置管理模块
"""
import os
from pathlib import Path
from dotenv import load_dotenv

# 加载环境变量
PROJECT_ROOT = Path(__file__).resolve().parent.parent
CONFIG_ENV_PATH = PROJECT_ROOT / 'config.env'
load_dotenv(CONFIG_ENV_PATH)


class Config:
    """配置类"""

    # API配置
    API_BASE_URL = os.getenv('API_BASE_URL', 'https://api.deepseek.com')
    API_KEY = os.getenv('API_KEY', '')
    MODEL_NAME = os.getenv('MODEL_NAME', 'deepseek-chat')
    _DATA_DIR = os.getenv('DATA_DIR', str(PROJECT_ROOT / 'data'))
    DATA_DIR = str(Path(_DATA_DIR) if Path(_DATA_DIR).is_absolute() else PROJECT_ROOT / _DATA_DIR)

    # 对话配置
    TEMPERATURE = float(os.getenv('TEMPERATURE', '0.7'))
    MAX_TOKENS = int(os.getenv('MAX_TOKENS', '1000'))

    # 系统提示
    SYSTEM_PROMPT = os.getenv(
        'SYSTEM_PROMPT',
        '你是“ 小芽 ”，一名温暖、专业、稳重的数字心理伙伴，长期陪伴骨髓移植患者（含移植前准备期/移植中关键期/移植后恢复期）。'
        '你会用简洁清晰的中文回应，优先共情与安抚；在需要时使用CBT（认知重构、行为激活、放松训练等）帮助用户缓解压力。'
        '当用户表达强烈痛苦或危机信号时，你要优先进行安全评估与求助引导。'
    )

    # 骨髓移植分期支持开关
    TRANSPLANT_SUPPORT_ENABLED = os.getenv('TRANSPLANT_SUPPORT_ENABLED', 'true').lower() == 'true'

    # CBT配置
    CBT_ENABLED = os.getenv('CBT_ENABLED', 'true').lower() == 'true'
    AUTO_CBT_INTERVENTION = os.getenv('AUTO_CBT_INTERVENTION', 'true').lower() == 'true'
    # CBT分析是否优先用大模型（关键词规则作为兜底）
    CBT_LLM_ENABLED = os.getenv('CBT_LLM_ENABLED', 'true').lower() == 'true'

    # CBT触发阈值：仅在“需要时”追加CBT建议，避免打扰日常闲聊
    CBT_INTERVENTION_SEVERITY_THRESHOLD = int(os.getenv('CBT_INTERVENTION_SEVERITY_THRESHOLD', '6'))
    CBT_DISTORTION_TRIGGER_ENABLED = os.getenv('CBT_DISTORTION_TRIGGER_ENABLED', 'true').lower() == 'true'

    # 危机干预配置
    CRISIS_DETECTION_ENABLED = os.getenv('CRISIS_DETECTION_ENABLED', 'true').lower() == 'true'
    CRISIS_ALERT_THRESHOLD = int(os.getenv('CRISIS_ALERT_THRESHOLD', '10'))

    # 危机与情境 LLM 判定（关键词作为兜底）
    CRISIS_LLM_DETECTION_ENABLED = os.getenv('CRISIS_LLM_DETECTION_ENABLED', 'true').lower() == 'true'
    CRISIS_LLM_STREAM_BLOCKING_ENABLED = os.getenv('CRISIS_LLM_STREAM_BLOCKING_ENABLED', 'false').lower() == 'true'
    TRANSPLANT_LLM_SCENARIO_ENABLED = os.getenv('TRANSPLANT_LLM_SCENARIO_ENABLED', 'true').lower() == 'true'
    LLM_DETECTION_MODEL = os.getenv('LLM_DETECTION_MODEL', MODEL_NAME)
    LLM_DETECTION_TEMPERATURE = float(os.getenv('LLM_DETECTION_TEMPERATURE', '0.4'))
    LLM_DETECTION_MAX_TOKENS = int(os.getenv('LLM_DETECTION_MAX_TOKENS', '256'))

    # 能量评估配置
    ENERGY_MODEL_ENABLED = os.getenv('ENERGY_MODEL_ENABLED', 'true').lower() == 'true'
    ENERGY_FEEDBACK_ENABLED = os.getenv('ENERGY_FEEDBACK_ENABLED', 'true').lower() == 'true'

    # 自动保存配置
    AUTO_SAVE_PROGRESS = os.getenv('AUTO_SAVE_PROGRESS', 'true').lower() == 'true'
    POST_STREAM_ANALYSIS_WAIT_SECONDS = float(os.getenv('POST_STREAM_ANALYSIS_WAIT_SECONDS', '0.2'))

    # 对话历史压缩配置（增量摘要/记忆中枢）
    HISTORY_COMPRESSION_ENABLED = os.getenv('HISTORY_COMPRESSION_ENABLED', 'true').lower() == 'true'
    INCREMENTAL_SUMMARY_MAX_WORDS = int(os.getenv('INCREMENTAL_SUMMARY_MAX_WORDS', '300'))  # 增量摘要最大字数

    @classmethod
    def validate_config(cls):
        """验证配置是否完整"""
        if not cls.API_KEY:
            raise ValueError("API_KEY不能为空，请在config.env中设置")
        return True
