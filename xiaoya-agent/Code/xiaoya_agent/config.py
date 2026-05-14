"""
配置管理模块
"""
import os
from pathlib import Path
from dotenv import load_dotenv

# 加载环境变量
PROJECT_ROOT = Path(__file__).resolve().parents[2]
CONFIG_ENV_PATH = PROJECT_ROOT / 'config.env'
load_dotenv(CONFIG_ENV_PATH)


class Config:
    """配置类"""

    # API 配置
    API_BASE_URL = os.getenv('API_BASE_URL', 'https://api.deepseek.com')
    API_KEY = os.getenv('API_KEY', '')
    MODEL_NAME = os.getenv('MODEL_NAME', 'deepseek-chat')
    _DATA_DIR = os.getenv('DATA_DIR', str(PROJECT_ROOT / 'data'))
    DATA_DIR = str(Path(_DATA_DIR) if Path(_DATA_DIR).is_absolute() else PROJECT_ROOT / _DATA_DIR)

    # 对话配置
    TEMPERATURE = float(os.getenv('TEMPERATURE', '0.7'))
    MAX_TOKENS = int(os.getenv('MAX_TOKENS', '1000'))
    AGENT_GRAPH_ENABLED = os.getenv('AGENT_GRAPH_ENABLED', 'true').lower() == 'true'
    AGENT_TOOLS_ENABLED = os.getenv('AGENT_TOOLS_ENABLED', 'true').lower() == 'true'
    AGENT_MODEL_TOOL_CALLING_ENABLED = os.getenv('AGENT_MODEL_TOOL_CALLING_ENABLED', 'true').lower() == 'true'
    AGENT_MODEL_TOOL_CALL_MAX_CALLS = int(os.getenv('AGENT_MODEL_TOOL_CALL_MAX_CALLS', '2'))
    BACKGROUND_ANALYSIS_START_MODE = os.getenv('BACKGROUND_ANALYSIS_START_MODE', 'after_stream').lower()
    BACKGROUND_CRISIS_FIRST_ENABLED = os.getenv('BACKGROUND_CRISIS_FIRST_ENABLED', 'true').lower() == 'true'
    BACKGROUND_ANALYSIS_TIMEOUT_SECONDS = float(os.getenv('BACKGROUND_ANALYSIS_TIMEOUT_SECONDS', '8'))
    RESPONSE_MAX_TOKENS_NORMAL = int(os.getenv('RESPONSE_MAX_TOKENS_NORMAL', '240'))
    RESPONSE_MAX_TOKENS_CBT = int(os.getenv('RESPONSE_MAX_TOKENS_CBT', '280'))
    PROMPT_PROFILE = os.getenv('PROMPT_PROFILE', 'warm_cbt')
    OUTPUT_MODE = os.getenv('OUTPUT_MODE', 'brief_support')

    # MCP 风格确定性服务：提供当前时间等实时事实上下文，
    # 避免聊天模型依靠猜测回答。
    MCP_SERVICES_ENABLED = os.getenv('MCP_SERVICES_ENABLED', 'true').lower() == 'true'
    MCP_TIMEZONE = os.getenv('MCP_TIMEZONE', 'Asia/Shanghai')

    # RAG 检索配置：运行时只使用 Dify 知识库；File/ 不作为 RAG 语料。
    RAG_ENABLED = os.getenv('RAG_ENABLED', 'true').lower() == 'true'
    _RAG_SOURCE_DIR = os.getenv('RAG_SOURCE_DIR', str(PROJECT_ROOT / 'File'))
    RAG_SOURCE_DIR = str(Path(_RAG_SOURCE_DIR) if Path(_RAG_SOURCE_DIR).is_absolute() else PROJECT_ROOT / _RAG_SOURCE_DIR)
    RAG_TOP_K = int(os.getenv('RAG_TOP_K', '3'))
    RAG_CHUNK_SIZE = int(os.getenv('RAG_CHUNK_SIZE', '450'))
    RAG_MAX_CONTEXT_CHARS = int(os.getenv('RAG_MAX_CONTEXT_CHARS', '900'))
    RAG_MIN_SCORE = float(os.getenv('RAG_MIN_SCORE', '0.08'))
    RAG_SCORING_MODE = os.getenv('RAG_SCORING_MODE', 'tfidf')
    RAG_AUTO_TRIGGER_ENABLED = os.getenv('RAG_AUTO_TRIGGER_ENABLED', 'true').lower() == 'true'
    RAG_WARMUP_ON_START = os.getenv('RAG_WARMUP_ON_START', 'true').lower() == 'true'
    RAG_BACKEND = os.getenv('RAG_BACKEND', 'dify').lower()
    RAG_EMBEDDING_MODEL = os.getenv('RAG_EMBEDDING_MODEL', '')
    RAG_EMBEDDING_BASE_URL = os.getenv('RAG_EMBEDDING_BASE_URL', API_BASE_URL)
    RAG_EMBEDDING_API_KEY = os.getenv('RAG_EMBEDDING_API_KEY', API_KEY)
    RAG_EMBEDDING_BATCH_SIZE = int(os.getenv('RAG_EMBEDDING_BATCH_SIZE', '16'))
    RAG_SEMANTIC_WEIGHT = float(os.getenv('RAG_SEMANTIC_WEIGHT', '0.72'))

    # Dify 集成：Dify 适合作为外层 Chatflow/Workflow 编排，
    # 配置完成后也作为托管知识库检索器使用。
    DIFY_API_BASE_URL = os.getenv('DIFY_API_BASE_URL', 'https://api.dify.ai/v1').rstrip('/')
    DIFY_API_KEY = os.getenv('DIFY_API_KEY', '')
    DIFY_KNOWLEDGE_API_KEY = os.getenv('DIFY_KNOWLEDGE_API_KEY', DIFY_API_KEY)
    DIFY_KNOWLEDGE_BASE_ID = os.getenv(
        'DIFY_KNOWLEDGE_BASE_ID',
        os.getenv('DIFY_DATASET_ID', ''),
    )
    DIFY_KNOWLEDGE_ENABLED = os.getenv('DIFY_KNOWLEDGE_ENABLED', 'true').lower() == 'true'
    DIFY_KNOWLEDGE_FALLBACK_TO_LOCAL = False
    DIFY_KNOWLEDGE_TIMEOUT_SECONDS = float(os.getenv('DIFY_KNOWLEDGE_TIMEOUT_SECONDS', '8'))
    DIFY_KNOWLEDGE_SEARCH_METHOD = os.getenv('DIFY_KNOWLEDGE_SEARCH_METHOD', 'keyword_search')

    # 结构化输出：json_object 兼容多数 OpenAI 兼容服务商；
    # 当服务商支持 json_schema 时可切换启用。
    STRUCTURED_OUTPUT_ENABLED = os.getenv('STRUCTURED_OUTPUT_ENABLED', 'true').lower() == 'true'
    STRUCTURED_OUTPUT_MODE = os.getenv('STRUCTURED_OUTPUT_MODE', 'json_object')
    STRUCTURED_OUTPUT_STRICT = os.getenv('STRUCTURED_OUTPUT_STRICT', 'false').lower() == 'true'

    # 会话状态快照：补充现有的按会话拆分文件，
    # 让完整智能体状态可以从单个 JSON 文档恢复。
    SESSION_STATE_ENABLED = os.getenv('SESSION_STATE_ENABLED', 'true').lower() == 'true'

    # 系统提示
    SYSTEM_PROMPT = os.getenv(
        'SYSTEM_PROMPT',
        '你是“ 小芽 ”，一名温暖、专业、稳重的数字心理伙伴，长期陪伴骨髓移植患者（含移植前准备期/移植中关键期/移植后恢复期）。'
        '你会用简洁清晰的中文回应，优先共情与安抚；在需要时使用CBT（认知重构、行为激活、放松训练等）帮助用户缓解压力。'
        '当用户表达强烈痛苦或危机信号时，你要优先进行安全评估与求助引导。'
    )

    # 骨髓移植分期支持开关
    TRANSPLANT_SUPPORT_ENABLED = os.getenv('TRANSPLANT_SUPPORT_ENABLED', 'true').lower() == 'true'

    # CBT 配置
    CBT_ENABLED = os.getenv('CBT_ENABLED', 'true').lower() == 'true'
    AUTO_CBT_INTERVENTION = os.getenv('AUTO_CBT_INTERVENTION', 'true').lower() == 'true'
    # CBT 分析是否优先用大模型（关键词规则作为兜底）
    CBT_LLM_ENABLED = os.getenv('CBT_LLM_ENABLED', 'true').lower() == 'true'

    # CBT 触发阈值：仅在“需要时”追加 CBT 建议，避免打扰日常闲聊
    CBT_INTERVENTION_SEVERITY_THRESHOLD = int(os.getenv('CBT_INTERVENTION_SEVERITY_THRESHOLD', '6'))
    CBT_DISTORTION_TRIGGER_ENABLED = os.getenv('CBT_DISTORTION_TRIGGER_ENABLED', 'true').lower() == 'true'

    # 危机干预配置
    CRISIS_DETECTION_ENABLED = os.getenv('CRISIS_DETECTION_ENABLED', 'true').lower() == 'true'
    CRISIS_ALERT_THRESHOLD = int(os.getenv('CRISIS_ALERT_THRESHOLD', '10'))

    # 危机与情境 LLM 判定；默认主回复不等待危机 LLM，关键词仅作可选兜底。
    CRISIS_LLM_DETECTION_ENABLED = os.getenv('CRISIS_LLM_DETECTION_ENABLED', 'true').lower() == 'true'
    CRISIS_LLM_STREAM_BLOCKING_ENABLED = os.getenv('CRISIS_LLM_STREAM_BLOCKING_ENABLED', 'false').lower() == 'true'
    MEDICAL_RED_FLAG_RULE_ENABLED = os.getenv('MEDICAL_RED_FLAG_RULE_ENABLED', 'false').lower() == 'true'
    TRANSPLANT_LLM_SCENARIO_ENABLED = os.getenv('TRANSPLANT_LLM_SCENARIO_ENABLED', 'true').lower() == 'true'
    LLM_DETECTION_MODEL = os.getenv('LLM_DETECTION_MODEL', MODEL_NAME)
    LLM_DETECTION_TEMPERATURE = float(os.getenv('LLM_DETECTION_TEMPERATURE', '0.4'))
    LLM_DETECTION_MAX_TOKENS = int(os.getenv('LLM_DETECTION_MAX_TOKENS', '256'))
    CRISIS_PRECHECK_MODEL = os.getenv('CRISIS_PRECHECK_MODEL', LLM_DETECTION_MODEL)
    CRISIS_PRECHECK_TEMPERATURE = float(os.getenv('CRISIS_PRECHECK_TEMPERATURE', '0.0'))
    CRISIS_PRECHECK_MAX_TOKENS = int(os.getenv('CRISIS_PRECHECK_MAX_TOKENS', '96'))
    CRISIS_PRECHECK_TIMEOUT_SECONDS = float(os.getenv('CRISIS_PRECHECK_TIMEOUT_SECONDS', '1.8'))

    # 能量评估配置
    ENERGY_MODEL_ENABLED = os.getenv('ENERGY_MODEL_ENABLED', 'true').lower() == 'true'
    ENERGY_FEEDBACK_ENABLED = os.getenv('ENERGY_FEEDBACK_ENABLED', 'true').lower() == 'true'

    # 自动保存配置
    AUTO_SAVE_PROGRESS = os.getenv('AUTO_SAVE_PROGRESS', 'true').lower() == 'true'
    POST_STREAM_ANALYSIS_WAIT_SECONDS = float(os.getenv('POST_STREAM_ANALYSIS_WAIT_SECONDS', '8'))

    # 对话历史压缩配置（增量摘要/记忆中枢）
    HISTORY_COMPRESSION_ENABLED = os.getenv('HISTORY_COMPRESSION_ENABLED', 'true').lower() == 'true'
    INCREMENTAL_SUMMARY_MAX_WORDS = int(os.getenv('INCREMENTAL_SUMMARY_MAX_WORDS', '300'))  # 增量摘要最大字数

    @classmethod
    def validate_config(cls):
        """验证配置是否完整"""
        if not cls.API_KEY:
            raise ValueError("API_KEY不能为空，请在config.env中设置")
        return True
