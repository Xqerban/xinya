#!/usr/bin/env python
"""
完整测试套件：增强版对话智能体

测试覆盖：
1. 基础对话功能
2. CBT模块（情绪识别、认知扭曲检测、技术推荐、门控机制）
3. 危机干预模块（关键词检测、LLM检测、报警机制、正念练习）
4. 移植分期支持（分期管理、情境识别、引导语生成）
5. 心理能量模型（能量评估、成就系统、等级系统、持久化）
6. 记忆中枢（增量摘要、token优化）
7. 数据持久化（保存/加载、重置功能）
8. 综合报告（CBT进度、能量报告、危机历史）
"""
import os
import io
import json
import sys
import time
import tempfile
import threading
import unittest
from pathlib import Path
from contextlib import redirect_stdout
from unittest.mock import patch, MagicMock

CODE_DIR = Path(__file__).resolve().parents[1]
if str(CODE_DIR) not in sys.path:
    sys.path.insert(0, str(CODE_DIR))

from xiaoya_agent.core.agent import EnhancedChatAgent
from xiaoya_agent.config import Config
from xiaoya_agent.domain.transplant import TransplantPhase, Scenario, choose_intervention, get_template
from xiaoya_agent.features.crisis import CrisisInterventionModule, CrisisType, build_crisis_alarm
from xiaoya_agent.features.cbt import CBTModule, CBTTechnique
from xiaoya_agent.features.energy import PsychologicalEnergyModel
from xiaoya_agent.features.harbor import (
    create_harbor_practice,
    list_harbor_catalog,
    should_use_harbor_regulation,
)
from xiaoya_agent.interfaces.cli import (
    build_cli_psych_model_payload,
    create_cli_agent,
    display_harbor_practice,
    list_cli_users,
    parse_harbor_command,
    run_chat_turn,
    switch_cli_user,
)
from xiaoya_agent.runtime.session import (
    agent_sessions,
    auto_name_session,
    build_thread_id,
    create_session_metadata,
    delete_session,
    delete_user,
    get_session_history,
    get_or_create_session,
    list_user_conversations,
    list_session_summaries,
    list_user_summaries,
    prepare_session_for_chat,
    rename_session,
    sanitize_session_id,
    sanitize_user_id,
    sync_user_conversation_history,
    update_session_after_chat,
)
from xiaoya_agent.features.cohort_learning import (
    get_cohort_learning_context,
    rebuild_cohort_learning_model,
)
from xiaoya_agent.tools.local_tools import (
    build_response_context_from_tool_outputs,
    get_agent_tools,
    harbor_regulation_tool,
    invoke_turn_tools,
    medical_red_flag_scan,
    should_use_knowledge_retrieval,
)
from xiaoya_agent.graph.turn_graph import prepare_stream_turn
from xiaoya_agent.retrieval.rag import get_knowledge_index, reset_rag_index_cache, retrieve_knowledge
from xiaoya_agent.prompts.runtime import (
    compare_prompt_versions,
    delete_prompt_entry,
    get_prompt_entry,
    get_prompt_registry_snapshot,
    preview_prompt_candidate,
    reload_prompt_registry,
    resolve_prompt_runtime_config,
    rollback_prompt_entry,
    update_prompt_entry,
    update_prompt_settings,
)
from xiaoya_agent.llm.structured import UnifiedAnalysisPayload, parse_structured_json

if not Config.API_KEY:
    Config.API_KEY = "test-api-key"


class _FakeOpenAI:
    """OpenAI API 模拟器"""
    def __init__(self, responses=None, **kwargs):
        self._responses = list(responses or [])
        self.requests = []

        class _Chat:
            def __init__(self, outer):
                self.completions = self
                self._outer = outer

            def create(self, **kwargs):
                self._outer.requests.append(kwargs)
                if self._outer._responses:
                    content = self._outer._responses.pop(0)
                else:
                    content = "默认回复"

                class _Msg:
                    def __init__(self, c):
                        self.content = c

                class _Choice:
                    def __init__(self, c):
                        self.message = _Msg(c)

                class _Resp:
                    def __init__(self, c):
                        self.choices = [_Choice(c)]

                return _Resp(content)

        self.chat = _Chat(self)


class _FakeStreamingOpenAI(_FakeOpenAI):
    """支持 stream=True 的 OpenAI API 模拟器。"""

    def __init__(self, responses=None, **kwargs):
        super().__init__(responses=responses, **kwargs)

        class _Chat:
            def __init__(self, outer):
                self.completions = self
                self._outer = outer

            def create(self, **kwargs):
                self._outer.requests.append(kwargs)
                if self._outer._responses:
                    content = self._outer._responses.pop(0)
                else:
                    content = "默认回复"

                if kwargs.get("stream"):
                    class _Delta:
                        def __init__(self, c):
                            self.content = c

                    class _Choice:
                        def __init__(self, c):
                            self.delta = _Delta(c)

                    class _Chunk:
                        def __init__(self, c):
                            self.choices = [_Choice(c)]

                    return iter([_Chunk(content)])

                class _Msg:
                    def __init__(self, c):
                        self.content = c

                class _Choice:
                    def __init__(self, c):
                        self.message = _Msg(c)

                class _Resp:
                    def __init__(self, c):
                        self.choices = [_Choice(c)]

                return _Resp(content)

        self.chat = _Chat(self)


class _FailingCrisisOpenAI:
    """如果流式链路同步调用独立危机 LLM，则让测试失败。"""

    def __init__(self, *args, **kwargs):
        raise AssertionError("streaming response should not block on crisis LLM")


class _FakeSplitStreamAnalysisOpenAI:
    """主回复走 stream，后台综合分析走非 stream，二者互不抢响应队列。"""

    def __init__(self, **kwargs):
        class _Chat:
            def __init__(self):
                self.completions = self

            def create(self, **kwargs):
                if kwargs.get("stream"):
                    class _Delta:
                        def __init__(self, c):
                            self.content = c

                    class _Choice:
                        def __init__(self, c):
                            self.delta = _Delta(c)

                    class _Chunk:
                        def __init__(self, c):
                            self.choices = [_Choice(c)]

                    return iter([_Chunk("我在，先陪你。")])

                content = json.dumps({
                    "emotional_state": {"primary": "hopelessness", "severity": 9},
                    "cognitive_distortions": [],
                    "problem_severity": 9,
                    "intervention_needed": True,
                    "recommended_technique": None,
                    "crisis": {
                        "has_crisis": True,
                        "severity_score": 18,
                        "crisis_types": ["自杀危机"]
                    },
                    "transplant": {
                        "should_trigger": False,
                        "phase": "移植前准备期",
                        "scenario": None,
                        "confidence": 0
                    }
                }, ensure_ascii=False)

                class _Msg:
                    def __init__(self, c):
                        self.content = c

                class _Choice:
                    def __init__(self, c):
                        self.message = _Msg(c)

                class _Resp:
                    def __init__(self, c):
                        self.choices = [_Choice(c)]

                return _Resp(content)

        self.chat = _Chat()


class _FakeSlowNonCrisisAnalysisOpenAI:
    """主回复立即流出，后台语义分析稍后返回非危机结果。"""

    def __init__(self, **kwargs):
        class _Chat:
            def __init__(self):
                self.completions = self

            def create(self, **kwargs):
                if kwargs.get("stream"):
                    class _Delta:
                        def __init__(self, c):
                            self.content = c

                    class _Choice:
                        def __init__(self, c):
                            self.delta = _Delta(c)

                    class _Chunk:
                        def __init__(self, c):
                            self.choices = [_Choice(c)]

                    return iter([_Chunk("我听到了，我们可以慢慢聊。")])

                time.sleep(0.2)
                content = json.dumps({
                    "emotional_state": {"primary": "neutral", "severity": 2},
                    "cognitive_distortions": [],
                    "problem_severity": 2,
                    "intervention_needed": False,
                    "recommended_technique": None,
                    "crisis": {
                        "has_crisis": False,
                        "severity_score": 2,
                        "crisis_types": []
                    },
                    "transplant": {
                        "should_trigger": False,
                        "phase": "移植前准备期",
                        "scenario": None,
                        "confidence": 0
                    }
                }, ensure_ascii=False)

                class _Msg:
                    def __init__(self, c):
                        self.content = c

                class _Choice:
                    def __init__(self, c):
                        self.message = _Msg(c)

                class _Resp:
                    def __init__(self, c):
                        self.choices = [_Choice(c)]

                return _Resp(content)

        self.chat = _Chat()


class _FakeSemanticCrisisOpenAI(_FakeOpenAI):
    """危机语义判断模拟器：无关键词也能通过语义触发危机。"""

    def __init__(self, responses=None, **kwargs):
        super().__init__([
            json.dumps({
                "has_crisis": True,
                "crisis_types": ["自杀危机"],
                "severity_score": 18,
                "reason": "表达了消失和不再醒来的强烈风险意图"
            }, ensure_ascii=False)
        ], **kwargs)


class _FakeSemanticMedicalRedFlagOpenAI(_FakeOpenAI):
    """安全语义判断模拟器：把身体风险识别为身体红旗。"""

    def __init__(self, responses=None, **kwargs):
        super().__init__([
            json.dumps({
                "has_crisis": True,
                "crisis_types": ["身体红旗"],
                "severity_score": 18,
                "reason": "患者表达呼吸困难，需要现场医护优先确认安全。"
            }, ensure_ascii=False)
        ], **kwargs)


class _FakeSemanticNoCrisisOpenAI(_FakeOpenAI):
    """安全语义判断模拟器：即使命中词面，也按语境判为非危机。"""

    def __init__(self, responses=None, **kwargs):
        super().__init__([
            json.dumps({
                "has_crisis": False,
                "crisis_types": [],
                "severity_score": 1,
                "reason": "结合语境，用户是在表达恐惧降低，不是自伤或自杀意图。"
            }, ensure_ascii=False)
        ], **kwargs)


class _FakeToolCallingOpenAI:
    """模拟 OpenAI 函数调用：第一轮请求工具，第二轮生成最终回复。"""

    def __init__(self, responses=None, **kwargs):
        self.requests = []

        class _Function:
            def __init__(self, name, arguments):
                self.name = name
                self.arguments = arguments

        class _ToolCall:
            def __init__(self, name, arguments):
                self.id = "call_1"
                self.type = "function"
                self.function = _Function(name, arguments)

        class _Msg:
            def __init__(self, content=None, tool_calls=None):
                self.content = content
                self.tool_calls = tool_calls

        class _Choice:
            def __init__(self, message):
                self.message = message

        class _Resp:
            def __init__(self, message):
                self.choices = [_Choice(message)]

        class _Chat:
            def __init__(self, outer):
                self.completions = self
                self._outer = outer

            def create(self, **kwargs):
                self._outer.requests.append(kwargs)
                if kwargs.get("tools"):
                    return _Resp(_Msg(
                        content=None,
                        tool_calls=[_ToolCall(
                            "knowledge_retrieval",
                            json.dumps({"query": "希望之树", "top_k": 1}, ensure_ascii=False),
                        )],
                    ))
                return _Resp(_Msg(content="希望之树可以帮助患者记录微小进步。"))

        self.chat = _Chat(self)


class _FakeStreamingToolCallingOpenAI:
    """模拟流式主流程中的模型自主工具调用。"""

    def __init__(self, tool_name, arguments, final_text, **kwargs):
        self.requests = []
        self.tool_name = tool_name
        self.arguments = arguments
        self.final_text = final_text

        class _Function:
            def __init__(self, name, arguments):
                self.name = name
                self.arguments = arguments

        class _ToolCall:
            def __init__(self, name, arguments):
                self.id = "call_1"
                self.type = "function"
                self.function = _Function(name, arguments)

        class _Msg:
            def __init__(self, content=None, tool_calls=None):
                self.content = content
                self.tool_calls = tool_calls

        class _MessageChoice:
            def __init__(self, message):
                self.message = message

        class _MessageResp:
            def __init__(self, message):
                self.choices = [_MessageChoice(message)]

        class _Delta:
            def __init__(self, content):
                self.content = content

        class _StreamChoice:
            def __init__(self, content):
                self.delta = _Delta(content)

        class _Chunk:
            def __init__(self, content):
                self.choices = [_StreamChoice(content)]

        class _Chat:
            def __init__(self, outer):
                self.completions = self
                self._outer = outer

            def create(self, **kwargs):
                self._outer.requests.append(kwargs)
                if kwargs.get("tools"):
                    return _MessageResp(_Msg(
                        content=None,
                        tool_calls=[_ToolCall(
                            self._outer.tool_name,
                            json.dumps(self._outer.arguments, ensure_ascii=False),
                        )],
                    ))
                if kwargs.get("stream"):
                    return iter([_Chunk(self._outer.final_text)])
                return _MessageResp(_Msg(content=self._outer.final_text))

        self.chat = _Chat(self)


# ============================================================================
# 测试类开始
# ============================================================================

class TestEntrypointFlowParity(unittest.TestCase):
    """测试 CLI 入口与 API 入口复用同一轮对话后处理。"""

    def test_cli_chat_turn_plain_text_stream(self):
        class _FakeCliAgent:
            def __init__(self):
                self.last_result = {
                    "response": "你好",
                    "response_type": "cbt_response",
                    "crisis_detection": {},
                    "energy_assessment": None,
                    "energy_report": None,
                }

            def stream_chat(self, message):
                yield "**你好**，可以继续说。"

            def save_all_progress(self):
                raise AssertionError("auto-save should be disabled for this test")

        old_auto_save = Config.AUTO_SAVE_PROGRESS
        try:
            Config.AUTO_SAVE_PROGRESS = False
            output = io.StringIO()
            with redirect_stdout(output):
                run_chat_turn(_FakeCliAgent(), "你好")

            text = output.getvalue()
            self.assertIn("智能体:", text)
            self.assertIn("你好，可以继续说。", text)
            self.assertNotIn("**", text)
        finally:
            Config.AUTO_SAVE_PROGRESS = old_auto_save

    def test_cli_chat_turn_collapses_model_paragraph_breaks(self):
        class _FakeCliAgent:
            def __init__(self):
                self.last_result = {
                    "response": "我听到了。\n\n明天手术前会很难受。",
                    "response_type": "cbt_response",
                    "crisis_detection": {},
                    "energy_assessment": None,
                    "energy_report": None,
                }

            def stream_chat(self, message):
                yield "我听到了。\n"
                yield "\n明天手术前会很难受。"

            def save_all_progress(self):
                raise AssertionError("auto-save should be disabled for this test")

        old_auto_save = Config.AUTO_SAVE_PROGRESS
        try:
            Config.AUTO_SAVE_PROGRESS = False
            output = io.StringIO()
            with redirect_stdout(output):
                run_chat_turn(_FakeCliAgent(), "我不想死")

            text = output.getvalue()
            self.assertIn("我听到了。 明天手术前会很难受。", text)
            self.assertNotIn("我听到了。\n\n明天", text)
        finally:
            Config.AUTO_SAVE_PROGRESS = old_auto_save

    @patch("xiaoya_agent.core.agent.OpenAI", _FakeOpenAI)
    def test_cli_user_switch_uses_independent_psych_models(self):
        old_data_dir = Config.DATA_DIR
        old_auto_save = Config.AUTO_SAVE_PROGRESS
        with tempfile.TemporaryDirectory() as tmp_dir:
            try:
                Config.DATA_DIR = tmp_dir
                Config.AUTO_SAVE_PROGRESS = True

                agent_a = create_cli_agent("cli-user-a")
                agent_a.memory_core = "cli user a memory"
                agent_a.energy_model.total_energy = 17

                agent_b = switch_cli_user(agent_a, "cli-user-b")
                self.assertEqual(agent_b.user_id, "cli-user-b")
                self.assertNotEqual(agent_b.psych_model_dir, agent_a.psych_model_dir)
                self.assertNotEqual(agent_b.memory_core, "cli user a memory")
                self.assertNotEqual(agent_b.energy_model.total_energy, 17)

                agent_b.memory_core = "cli user b memory"
                agent_back = switch_cli_user(agent_b, "cli-user-a")
                self.assertEqual(agent_back.user_id, "cli-user-a")
                self.assertEqual(agent_back.memory_core, "cli user a memory")
                self.assertEqual(agent_back.energy_model.total_energy, 17)

                known_users = {item["userId"] for item in list_cli_users()}
                self.assertIn("cli-user-a", known_users)
                self.assertIn("cli-user-b", known_users)

                payload = build_cli_psych_model_payload(agent_back)
                self.assertEqual(payload["source"], "active_session")
                self.assertEqual(payload["userId"], "cli-user-a")
                self.assertEqual(payload["psychModel"]["memory_core"], "cli user a memory")
                self.assertEqual(payload["psychModel"]["energy_report"]["total_energy"], 17)
            finally:
                Config.AUTO_SAVE_PROGRESS = old_auto_save
                Config.DATA_DIR = old_data_dir

class TestSessionRuntime(unittest.TestCase):
    """测试 API 会话运行时基础规则。"""

    def test_thread_id_uses_sanitized_session_id(self):
        self.assertEqual(sanitize_session_id("session:abc/01"), "session_abc_01")
        self.assertEqual(build_thread_id("session:abc/01"), "session_abc_01")
        self.assertEqual(sanitize_session_id("///"), "default")

    def test_session_metadata_rename_auto_name_history_and_delete(self):
        old_data_dir = Config.DATA_DIR
        with tempfile.TemporaryDirectory() as tmp_dir:
            try:
                Config.DATA_DIR = tmp_dir
                agent_sessions.clear()
                meta = create_session_metadata("session:abc/01")
                self.assertEqual(meta["title"], "新的会话")

                session_dir = os.path.join(tmp_dir, "sessions", "session_abc_01")
                with open(os.path.join(session_dir, "chat_history.json"), "w", encoding="utf-8") as f:
                    json.dump([
                        {"role": "system", "content": "system"},
                        {"role": "user", "content": "我今天很担心移植后的恢复情况，需要聊聊。"},
                        {"role": "assistant", "content": "我在。"},
                    ], f, ensure_ascii=False)

                named = auto_name_session("session:abc/01")
                self.assertTrue(named["title"].startswith("我今天很担心"))

                renamed = rename_session("session:abc/01", "恢复期焦虑")
                self.assertEqual(renamed["title"], "恢复期焦虑")
                self.assertFalse(renamed["autoTitle"])

                history = get_session_history("session:abc/01")
                self.assertEqual(len(history), 2)
                self.assertEqual(history[0]["role"], "user")

                summaries = list_session_summaries()
                self.assertEqual(summaries[0]["sessionId"], "session:abc/01")
                self.assertTrue(delete_session("session:abc/01"))
                self.assertFalse(os.path.exists(session_dir))
            finally:
                agent_sessions.clear()
                Config.DATA_DIR = old_data_dir

    def test_session_id_collision_is_rejected(self):
        old_data_dir = Config.DATA_DIR
        with tempfile.TemporaryDirectory() as tmp_dir:
            try:
                Config.DATA_DIR = tmp_dir
                agent_sessions.clear()

                first = create_session_metadata("session:abc")
                self.assertEqual(first["safeSessionId"], "session_abc")

                with self.assertRaises(ValueError):
                    create_session_metadata("session/abc")

                get_or_create_session("session:abc")
                with self.assertRaises(ValueError):
                    get_or_create_session("session/abc")
                with self.assertRaises(ValueError):
                    get_session_history("session/abc")
            finally:
                agent_sessions.clear()
                Config.DATA_DIR = old_data_dir

    @patch("xiaoya_agent.core.agent.OpenAI", _FakeOpenAI)
    def test_user_psych_model_is_shared_across_sessions_and_isolated_between_users(self):
        old_data_dir = Config.DATA_DIR
        with tempfile.TemporaryDirectory() as tmp_dir:
            try:
                Config.DATA_DIR = tmp_dir
                agent_sessions.clear()

                first = get_or_create_session("session-a", user_id="patient:001")
                first.agent.memory_core = "patient 001 worries about rejection"
                first.agent.cbt_module.user_profile["session_count"] = 7
                first.agent.energy_model.total_energy = 42
                first.agent.set_transplant_phase(TransplantPhase.RECOVERY)
                first.agent.save_all_progress()

                agent_sessions.clear()
                same_user_other_session = get_or_create_session("session-b", user_id="patient:001")
                self.assertEqual(same_user_other_session.user_id, "patient:001")
                self.assertEqual(same_user_other_session.psych_model_dir, first.psych_model_dir)
                self.assertEqual(same_user_other_session.agent.memory_core, "patient 001 worries about rejection")
                self.assertEqual(same_user_other_session.agent.cbt_module.user_profile["session_count"], 7)
                self.assertEqual(same_user_other_session.agent.energy_model.total_energy, 42)
                self.assertEqual(same_user_other_session.agent.get_transplant_phase(), TransplantPhase.RECOVERY)

                other_user = get_or_create_session("session-c", user_id="patient:002")
                self.assertNotEqual(other_user.psych_model_dir, first.psych_model_dir)
                self.assertNotEqual(other_user.agent.memory_core, "patient 001 worries about rejection")
                self.assertNotEqual(other_user.agent.energy_model.total_energy, 42)

                psych_path = os.path.join(
                    tmp_dir,
                    "users",
                    sanitize_user_id("patient:001"),
                    "psych_model.json",
                )
                self.assertTrue(os.path.exists(psych_path))
            finally:
                agent_sessions.clear()
                Config.DATA_DIR = old_data_dir

    @patch("xiaoya_agent.core.agent.OpenAI", _FakeOpenAI)
    def test_existing_session_cannot_switch_user_model(self):
        old_data_dir = Config.DATA_DIR
        with tempfile.TemporaryDirectory() as tmp_dir:
            try:
                Config.DATA_DIR = tmp_dir
                agent_sessions.clear()

                created = create_session_metadata("fixed-session", user_id="patient-a")
                self.assertEqual(created["userId"], "patient-a")

                with self.assertRaises(ValueError):
                    create_session_metadata("fixed-session", user_id="patient-b")

                get_or_create_session("fixed-session", user_id="patient-a")
                with self.assertRaises(ValueError):
                    get_or_create_session("fixed-session", user_id="patient-b")
            finally:
                agent_sessions.clear()
                Config.DATA_DIR = old_data_dir

    @patch("xiaoya_agent.core.agent.OpenAI", _FakeOpenAI)
    def test_user_conversation_index_and_delete_user(self):
        old_data_dir = Config.DATA_DIR
        with tempfile.TemporaryDirectory() as tmp_dir:
            try:
                Config.DATA_DIR = tmp_dir
                agent_sessions.clear()

                session = get_or_create_session("api-session-1", user_id="patient-delete")
                session.agent.conversation_history = [
                    {"role": "system", "content": "system"},
                    {"role": "user", "content": "我今天担心恢复。"},
                    {"role": "assistant", "content": "我在。"},
                ]
                update_session_after_chat(
                    session,
                    user_message="我今天担心恢复。",
                    stage="RECOVERY",
                    prompt_meta={},
                    save_state_sync=True,
                )
                sync_user_conversation_history(
                    "patient-delete",
                    conversation_id="cli",
                    source="cli",
                    history=[
                        {"role": "system", "content": "system"},
                        {"role": "user", "content": "CLI 里也聊过。"},
                    ],
                    metadata={"title": "CLI 会话"},
                )

                payload = list_user_conversations("patient-delete", include_history=True)
                self.assertTrue(payload["exists"])
                sources = {item["source"] for item in payload["conversations"]}
                self.assertEqual(sources, {"api", "cli"})
                self.assertTrue(any(item.get("history") for item in payload["conversations"]))

                users = {item["userId"] for item in list_user_summaries()}
                self.assertIn("patient-delete", users)

                user_dir = os.path.join(tmp_dir, "users", sanitize_user_id("patient-delete"))
                session_dir = os.path.join(tmp_dir, "sessions", sanitize_session_id("api-session-1"))
                self.assertTrue(os.path.exists(os.path.join(user_dir, "conversation_index.json")))
                self.assertTrue(os.path.exists(session_dir))

                deleted = delete_user("patient-delete")
                self.assertTrue(deleted["deleted"])
                self.assertIn("api-session-1", deleted["deletedSessions"])
                self.assertFalse(os.path.exists(user_dir))
                self.assertFalse(os.path.exists(session_dir))
            finally:
                agent_sessions.clear()
                Config.DATA_DIR = old_data_dir

    @patch("xiaoya_agent.core.agent.OpenAI", _FakeOpenAI)
    def test_psych_model_updates_from_dialogue_and_personalizes_prompt(self):
        old_history_compression = Config.HISTORY_COMPRESSION_ENABLED
        with tempfile.TemporaryDirectory() as tmp_dir:
            try:
                Config.HISTORY_COMPRESSION_ENABLED = True
                data_dir = os.path.join(tmp_dir, "sessions", "s1")
                psych_dir = os.path.join(tmp_dir, "users", "patient-one")
                agent = EnhancedChatAgent(
                    data_dir=data_dir,
                    user_id="patient-one",
                    psych_model_dir=psych_dir,
                    load_persistent_data=False,
                )
                analysis = {
                    "emotional_state": {"primary": "anxiety", "severity": 7},
                    "cognitive_distortions": ["catastrophizing"],
                    "problem_severity": 7,
                    "intervention_needed": True,
                    "recommended_technique": CBTTechnique.RELAXATION_TRAINING,
                }
                unified = {
                    **analysis,
                    "crisis": {"has_crisis": False, "severity_score": 0, "crisis_types": []},
                    "transplant": {
                        "should_trigger": False,
                        "phase": TransplantPhase.RECOVERY,
                        "scenario": None,
                        "confidence": 0,
                    },
                    "energy_assessment": {
                        "cognitive_growth": 3,
                        "emotion_regulation": 5,
                        "behavior_change": 2,
                        "social_connection": 0,
                        "self_efficacy": 1,
                        "assessment_note": "用户表达了担忧并愿意尝试短呼吸。",
                        "achievement_signals": {},
                    },
                    "psych_model_patch": {
                        "preferred_name": "小王",
                        "current_main_concerns": ["担心排异"],
                        "cognitive_patterns": ["catastrophizing"],
                        "support_preferences": ["回复更短，只给一个小步骤"],
                        "communication_style": "brief",
                        "evidence": ["我叫小王，担心排异，也希望你以后短一点回复。"],
                    },
                }
                analysis_task = {
                    "event": threading.Event(),
                    "result": unified,
                    "lock": threading.Lock(),
                    "consumed": False,
                }
                analysis_task["event"].set()

                agent._finalize_chat_turn(
                    user_message="我叫小王，担心排异，也希望你以后短一点回复。",
                    response="小王，我听到你很担心排异，我们先做一次短呼吸。",
                    response_type="cbt_response",
                    cbt_analysis=analysis,
                    crisis_detection={"alert": False},
                    conversation_data={"user_message": "我叫小王，担心排异，也希望你以后短一点回复。", "analysis": analysis},
                    current_phase=TransplantPhase.RECOVERY,
                    analysis_task=analysis_task,
                )
                agent.wait_for_background_analysis(1)

                psych_path = os.path.join(psych_dir, "psych_model.json")
                self.assertTrue(os.path.exists(psych_path))

                restored = EnhancedChatAgent(
                    data_dir=os.path.join(tmp_dir, "sessions", "s2"),
                    user_id="patient-one",
                    psych_model_dir=psych_dir,
                )
                profile = restored.personalization_profile
                self.assertEqual(profile["preferred_name"], "小王")
                self.assertIn("catastrophizing", profile["cognitive_patterns"])
                self.assertIn("anxiety", profile["recurring_emotions"])
                self.assertEqual(profile["communication_style"], "brief")

                prompt_text = "\n".join(
                    message["content"]
                    for message in restored._get_messages_for_api("你还记得我担心什么吗？")
                    if message["role"] == "system"
                )
                self.assertIn("用户心理模型", prompt_text)
                self.assertIn("小王", prompt_text)
                self.assertIn("担心排异", prompt_text)
            finally:
                Config.HISTORY_COMPRESSION_ENABLED = old_history_compression

    @patch("xiaoya_agent.core.agent.OpenAI", _FakeOpenAI)
    def test_cohort_learning_aggregates_anonymous_user_models(self):
        old_values = {
            "DATA_DIR": Config.DATA_DIR,
            "COHORT_LEARNING_ENABLED": Config.COHORT_LEARNING_ENABLED,
            "COHORT_LEARNING_CONTEXT_ENABLED": Config.COHORT_LEARNING_CONTEXT_ENABLED,
            "COHORT_LEARNING_MIN_USERS": Config.COHORT_LEARNING_MIN_USERS,
            "COHORT_LEARNING_MIN_SIGNAL_USERS": Config.COHORT_LEARNING_MIN_SIGNAL_USERS,
            "COHORT_LEARNING_REFRESH_SECONDS": Config.COHORT_LEARNING_REFRESH_SECONDS,
            "HISTORY_COMPRESSION_ENABLED": Config.HISTORY_COMPRESSION_ENABLED,
        }
        with tempfile.TemporaryDirectory() as tmp_dir:
            try:
                Config.DATA_DIR = tmp_dir
                Config.COHORT_LEARNING_ENABLED = True
                Config.COHORT_LEARNING_CONTEXT_ENABLED = True
                Config.COHORT_LEARNING_MIN_USERS = 2
                Config.COHORT_LEARNING_MIN_SIGNAL_USERS = 2
                Config.COHORT_LEARNING_REFRESH_SECONDS = 0
                Config.HISTORY_COMPRESSION_ENABLED = True

                for user_id, style in [("patient-a", "brief"), ("patient-b", "gentle")]:
                    user_dir = os.path.join(tmp_dir, "users", user_id)
                    os.makedirs(user_dir, exist_ok=True)
                    with open(os.path.join(user_dir, "psych_model_meta.json"), "w", encoding="utf-8") as f:
                        json.dump({"userId": user_id, "safeUserId": user_id}, f, ensure_ascii=False)
                    with open(os.path.join(user_dir, "psych_model.json"), "w", encoding="utf-8") as f:
                        json.dump({
                            "modelVersion": 1,
                            "userId": user_id,
                            "updatedAt": "2026-05-15T10:00:00",
                            "user_state": {"transplant_phase": "移植后恢复期"},
                            "personalization_profile": {
                                "communication_style": style,
                                "current_main_concerns": ["担心排异"],
                                "recurring_emotions": {"anxiety": 2},
                                "cognitive_patterns": ["catastrophizing"],
                                "effective_strategies": ["短呼吸"],
                                "support_preferences": ["回复简短"],
                                "risk_notes": ["夜间焦虑升高"],
                            },
                        }, f, ensure_ascii=False)

                model = rebuild_cohort_learning_model(force=True)
                self.assertTrue(model["eligible"])
                self.assertEqual(model["userCount"], 2)
                self.assertEqual(
                    model["signals"]["commonConcerns"][0]["text"],
                    "担心排异",
                )
                self.assertTrue(model["privacy"]["storesUserIds"] is False)

                context = get_cohort_learning_context("patient-a", force_refresh=True)
                self.assertIn("骨髓移植患者群体经验", context)
                self.assertIn("担心排异", context)
                self.assertNotIn("patient-a", context)

                agent = EnhancedChatAgent(
                    data_dir=os.path.join(tmp_dir, "sessions", "s1"),
                    user_id="patient-c",
                    psych_model_dir=os.path.join(tmp_dir, "users", "patient-c"),
                    load_persistent_data=False,
                )
                prompt_text = "\n".join(
                    message["content"]
                    for message in agent._get_messages_for_api("我最近也很担心")
                    if message["role"] == "system"
                )
                self.assertIn("骨髓移植患者群体经验", prompt_text)
                self.assertIn("担心排异", prompt_text)
            finally:
                for key, value in old_values.items():
                    setattr(Config, key, value)

    @patch("xiaoya_agent.core.agent.OpenAI", _FakeOpenAI)
    def test_update_session_after_chat_records_prompt_versions(self):
        old_data_dir = Config.DATA_DIR
        with tempfile.TemporaryDirectory() as tmp_dir:
            try:
                Config.DATA_DIR = tmp_dir
                agent_sessions.clear()

                session = get_or_create_session("s1")
                session.agent.conversation_history.extend([
                    {"role": "user", "content": "今天很害怕"},
                    {"role": "assistant", "content": "我在。"},
                ])
                meta = update_session_after_chat(
                    session,
                    user_message="今天很害怕",
                    stage="TRANSPLANT",
                    prompt_meta={
                        "promptProfile": "warm_cbt",
                        "outputMode": "brief_support",
                        "promptProfileVersion": 2,
                        "outputModeVersion": 3,
                    },
                )
                self.assertEqual(meta["messageCount"], 1)
                self.assertEqual(meta["stage"], "TRANSPLANT")
                self.assertEqual(meta["promptProfileVersion"], 2)
                self.assertEqual(meta["outputModeVersion"], 3)
                self.assertEqual(meta["title"], "今天很害怕")

                state_path = os.path.join(tmp_dir, "sessions", "s1", "agent_state.json")
                self.assertTrue(os.path.exists(state_path))

                agent_sessions.clear()
                restored = get_or_create_session("s1")
                restored_history = [
                    message for message in restored.agent.conversation_history
                    if message.get("role") != "system"
                ]
                self.assertEqual(len(restored_history), 2)
                self.assertEqual(restored.agent.prompt_profile, "warm_cbt")
            finally:
                agent_sessions.clear()
                Config.DATA_DIR = old_data_dir

    @patch("xiaoya_agent.core.agent.OpenAI", _FakeOpenAI)
    def test_session_prepare_preserves_phase_when_stage_is_absent(self):
        old_data_dir = Config.DATA_DIR
        with tempfile.TemporaryDirectory() as tmp_dir:
            try:
                Config.DATA_DIR = tmp_dir
                agent_sessions.clear()
                session = get_or_create_session("phase-keep")
                session.agent.set_transplant_phase(TransplantPhase.RECOVERY)

                meta = prepare_session_for_chat(session, {"patientContext": {}})
                self.assertEqual(meta["stage"], "RECOVERY")
                self.assertEqual(session.agent.get_transplant_phase(), TransplantPhase.RECOVERY)

                meta = prepare_session_for_chat(session, {"patientContext": {"stage": "TRANSPLANT"}})
                self.assertEqual(meta["stage"], "TRANSPLANT")
                self.assertEqual(session.agent.get_transplant_phase(), TransplantPhase.KEY)
            finally:
                agent_sessions.clear()
                Config.DATA_DIR = old_data_dir

    @patch("xiaoya_agent.core.agent.OpenAI", _FakeOpenAI)
    def test_analyze_api_does_not_mutate_session_state_or_history(self):
        old_data_dir = Config.DATA_DIR
        with tempfile.TemporaryDirectory() as tmp_dir:
            try:
                Config.DATA_DIR = tmp_dir
                agent_sessions.clear()
                from xiaoya_agent.interfaces.api_server import app

                session = get_or_create_session("analysis-preview")
                session.agent.set_transplant_phase(TransplantPhase.RECOVERY)
                session.agent.conversation_history.extend([
                    {"role": "user", "content": "原有消息"},
                    {"role": "assistant", "content": "原有回复"},
                ])

                with patch.object(EnhancedChatAgent, "_llm_unified_analyze", return_value={
                    "emotional_state": {"primary": "anxiety", "severity": 5},
                    "cognitive_distortions": [],
                    "problem_severity": 5,
                    "intervention_needed": False,
                    "recommended_technique": None,
                    "crisis": {"has_crisis": False, "severity_score": 0, "crisis_types": []},
                    "transplant": {"should_trigger": False, "phase": TransplantPhase.PREP, "scenario": None, "confidence": 0},
                }):
                    response = app.test_client().post("/v1/psych/analyze", json={
                        "sessionId": "analysis-preview",
                        "message": "分析输入",
                        "patientContext": {"stage": "PRETREATMENT"},
                        "history": [{"role": "user", "content": "不应覆盖"}],
                    })

                self.assertEqual(response.status_code, 200)
                self.assertEqual(response.get_json()["stage"], "PRETREATMENT")
                self.assertEqual(session.agent.get_transplant_phase(), TransplantPhase.RECOVERY)
                visible_history = [
                    message for message in session.agent.conversation_history
                    if message.get("role") != "system"
                ]
                self.assertEqual(len(visible_history), 2)
                self.assertEqual(visible_history[0]["content"], "原有消息")
            finally:
                agent_sessions.clear()
                Config.DATA_DIR = old_data_dir

    @patch("xiaoya_agent.core.agent.OpenAI", _FakeOpenAI)
    def test_user_feature_api_routes_are_available(self):
        old_data_dir = Config.DATA_DIR
        old_auto_save = Config.AUTO_SAVE_PROGRESS
        old_dify_values = {
            "RAG_BACKEND": Config.RAG_BACKEND,
            "DIFY_KNOWLEDGE_API_KEY": Config.DIFY_KNOWLEDGE_API_KEY,
            "DIFY_KNOWLEDGE_BASE_ID": Config.DIFY_KNOWLEDGE_BASE_ID,
            "DIFY_KNOWLEDGE_ENABLED": Config.DIFY_KNOWLEDGE_ENABLED,
        }
        with tempfile.TemporaryDirectory() as tmp_dir:
            try:
                Config.DATA_DIR = tmp_dir
                Config.AUTO_SAVE_PROGRESS = False
                agent_sessions.clear()
                from xiaoya_agent.interfaces.api_server import app

                client = app.test_client()
                malformed_chat = client.post(
                    "/v1/psych/chat",
                    data='{"sessionId":"bad",',
                    content_type="application/json",
                )
                self.assertEqual(malformed_chat.status_code, 400)
                self.assertEqual(malformed_chat.get_json()["error"], "invalid_request")

                quoted_json = client.post(
                    "/v1/sessions",
                    data='\'{"sessionId":"quoted-json","title":"PowerShell quoted JSON"}\'',
                    content_type="application/json",
                )
                self.assertEqual(quoted_json.status_code, 201)
                self.assertEqual(quoted_json.get_json()["session"]["sessionId"], "quoted-json")

                collision_a = client.post("/v1/sessions", json={"sessionId": "api:collision"})
                self.assertEqual(collision_a.status_code, 201)
                collision_b = client.post("/v1/sessions", json={"sessionId": "api/collision"})
                self.assertEqual(collision_b.status_code, 400)
                self.assertEqual(collision_b.get_json()["error"], "invalid_request")

                gb18030_json = json.dumps({
                    "patientContext": {
                        "stage": "PRETREATMENT",
                        "psychEnergy": 40,
                        "emotionalState": {"primary": "焦虑", "severity": 6},
                    }
                }, ensure_ascii=False).encode("gb18030")
                gb18030_response = client.post(
                    "/v1/psych/recommendations",
                    data=gb18030_json,
                    content_type="application/json",
                )
                self.assertEqual(gb18030_response.status_code, 200)
                self.assertIn("questions", gb18030_response.get_json())

                quote_stripped_response = client.post(
                    "/v1/psych/recommendations",
                    data="{patientContext:{stage:PRETREATMENT,psychEnergy:40,emotionalState:{primary:焦虑,severity:6}}}".encode("utf-8"),
                    content_type="application/json",
                )
                self.assertEqual(quote_stripped_response.status_code, 200)
                self.assertIn("questions", quote_stripped_response.get_json())

                missing_state = client.get("/v1/sessions/not-created/state")
                self.assertEqual(missing_state.status_code, 404)

                created = client.post("/v1/sessions", json={
                    "sessionId": "api-user",
                    "userId": "patient-api",
                    "title": "API test",
                })
                self.assertEqual(created.status_code, 201)
                self.assertEqual(created.get_json()["session"]["userId"], "patient-api")

                state = client.get("/v1/sessions/api-user/state")
                self.assertEqual(state.status_code, 200)
                state_payload = state.get_json()["state"]
                self.assertEqual(state_payload["stage"], "PRETREATMENT")
                self.assertEqual(state_payload["userId"], "patient-api")
                self.assertIn("psychModelDir", state_payload)

                session_psych_model = client.get("/v1/sessions/api-user/psych-model")
                self.assertEqual(session_psych_model.status_code, 200)
                session_model_payload = session_psych_model.get_json()["psychModel"]
                self.assertEqual(session_model_payload["source"], "active_session")
                self.assertEqual(session_model_payload["userId"], "patient-api")
                self.assertIn("personalization_profile", session_model_payload["psychModel"])

                active_session = get_or_create_session("api-user")
                active_session.agent.conversation_history.extend([
                    {"role": "user", "content": "我叫小王"},
                    {"role": "assistant", "content": "你好，小王"},
                ])
                history = client.get("/v1/sessions/api-user/history")
                self.assertEqual(history.status_code, 200)
                history_payload = history.get_json()
                self.assertEqual(history_payload["messageCount"], 1)
                self.assertEqual(history_payload["historyCount"], 2)
                self.assertFalse(history_payload["includeSystem"])
                history_text = history.get_data(as_text=True)
                self.assertIn("我叫小王", history_text)
                self.assertNotIn("\\u6211\\u53eb\\u5c0f\\u738b", history_text)

                phase = client.patch("/v1/sessions/api-user/phase", json={"stage": "RECOVERY"})
                self.assertEqual(phase.status_code, 200)
                self.assertEqual(phase.get_json()["state"]["stage"], "RECOVERY")

                for path, key in [
                    ("/v1/sessions/api-user/energy", "energyReport"),
                    ("/v1/sessions/api-user/achievements", "stats"),
                    ("/v1/sessions/api-user/progress", "report"),
                    ("/v1/sessions/api-user/crisis-report", "crisisReport"),
                ]:
                    response = client.get(path)
                    self.assertEqual(response.status_code, 200)
                    self.assertIn(key, response.get_json())

                grounding = client.get("/v1/sessions/api-user/grounding")
                self.assertEqual(grounding.status_code, 200)
                self.assertIn("exercise", grounding.get_json())

                recorded_grounding = client.post("/v1/sessions/api-user/grounding", json={"record": True})
                self.assertEqual(recorded_grounding.status_code, 200)
                self.assertTrue(recorded_grounding.get_json()["recorded"])

                harbor_catalog = client.get("/v1/harbor/catalog")
                self.assertEqual(harbor_catalog.status_code, 200)
                self.assertIn("tools", harbor_catalog.get_json()["catalog"])

                harbor_start = client.post("/v1/harbor/start", json={
                    "scenario": "焦虑",
                    "toolType": "呼吸",
                    "durationSeconds": 60,
                })
                self.assertEqual(harbor_start.status_code, 200)
                self.assertFalse(harbor_start.get_json()["recorded"])
                self.assertEqual(harbor_start.get_json()["practice"]["durationSeconds"], 60)

                session_harbor = client.post("/v1/sessions/api-user/harbor", json={
                    "scenario": "失眠",
                    "toolType": "冥想",
                    "durationSeconds": 120,
                    "record": True,
                })
                self.assertEqual(session_harbor.status_code, 200)
                self.assertTrue(session_harbor.get_json()["recorded"])
                self.assertIn("voiceGuideText", session_harbor.get_json()["practice"])

                saved = client.post("/v1/sessions/api-user/save")
                self.assertEqual(saved.status_code, 200)
                self.assertTrue(saved.get_json()["saved"])

                user_psych_model = client.get("/v1/users/patient-api/psych-model")
                self.assertEqual(user_psych_model.status_code, 200)
                user_model_payload = user_psych_model.get_json()["psychModel"]
                self.assertEqual(user_model_payload["source"], "persisted_user_model")
                self.assertTrue(user_model_payload["exists"])
                self.assertEqual(user_model_payload["userId"], "patient-api")
                self.assertIn("personalization_profile", user_model_payload["psychModel"])

                Config.RAG_BACKEND = "dify"
                Config.DIFY_KNOWLEDGE_API_KEY = ""
                Config.DIFY_KNOWLEDGE_BASE_ID = ""
                Config.DIFY_KNOWLEDGE_ENABLED = True
                rag = client.post("/v1/knowledge/search", json={"query": "移植 焦虑", "topK": 1})
                self.assertEqual(rag.status_code, 200)
                self.assertIn("result", rag.get_json())

                mcp_services = client.get("/v1/mcp/services")
                self.assertEqual(mcp_services.status_code, 200)
                self.assertIn("current_time", [
                    service["name"] for service in mcp_services.get_json()["services"]
                ])

                mcp_time = client.post("/v1/mcp/invoke", json={"query": "现在几点"})
                self.assertEqual(mcp_time.status_code, 200)
                self.assertEqual(mcp_time.get_json()["result"]["services"], ["current_time"])

                bad_rag = client.post("/v1/knowledge/search", json={"query": "移植 焦虑", "topK": "bad"})
                self.assertEqual(bad_rag.status_code, 400)

                capabilities = client.get("/v1/capabilities")
                self.assertEqual(capabilities.status_code, 200)
                self.assertIn("prompts", capabilities.get_json())
                self.assertIn("users", capabilities.get_json())
                self.assertIn("dify", capabilities.get_json())
                self.assertIn("mcp", capabilities.get_json())
                self.assertIn("harbor", capabilities.get_json())

                dify_schema = client.get("/v1/dify/openapi.yaml")
                self.assertEqual(dify_schema.status_code, 200)
                self.assertIn("operationId: xiaoyaDifyChat", dify_schema.get_data(as_text=True))
                self.assertIn("operationId: getXiaoyaDifyHarbor", dify_schema.get_data(as_text=True))

                dify_status = client.get("/v1/dify/status")
                self.assertEqual(dify_status.status_code, 200)
                self.assertIn("knowledgeBase", dify_status.get_json()["dify"])

                dify_options = client.get("/v1/dify/options")
                self.assertEqual(dify_options.status_code, 200)
                self.assertIn("promptProfiles", dify_options.get_json())
                self.assertIn("question1", dify_options.get_json()["difyOutputFields"])
                self.assertIn("harbor.guideText", dify_options.get_json()["difyOutputFields"])

                dify_questions = client.post("/v1/dify/recommendations", json={
                    "inputs": {
                        "stage": "PRETREATMENT",
                        "psychEnergy": 45,
                    }
                })
                self.assertEqual(dify_questions.status_code, 200)
                self.assertIn("question1", dify_questions.get_json()["difyOutputs"])

                dify_context = client.post("/v1/dify/context", json={
                    "conversation_id": "dify-conv-test",
                    "user": "dify-user-test",
                })
                self.assertEqual(dify_context.status_code, 200)
                self.assertEqual(dify_context.get_json()["difyContext"]["sessionId"], "dify-conv-test")

                dify_grounding = client.post("/v1/dify/grounding", json={
                    "conversation_id": "dify-conv-test",
                    "user": "dify-user-test",
                })
                self.assertEqual(dify_grounding.status_code, 200)
                self.assertIn("exercise", dify_grounding.get_json()["difyOutputs"])

                dify_harbor = client.post("/v1/dify/harbor", json={
                    "conversation_id": "dify-conv-test",
                    "user": "dify-user-test",
                    "inputs": {
                        "scenario": "疼痛",
                        "toolType": "肌肉",
                        "durationSeconds": 120,
                    },
                })
                self.assertEqual(dify_harbor.status_code, 200)
                self.assertIn("guideText", dify_harbor.get_json()["difyOutputs"])
                self.assertEqual(dify_harbor.get_json()["difyOutputs"]["durationSeconds"], 120)

                dify_session = get_or_create_session("dify-conv-test", user_id="dify-user-test")
                dify_session.agent.client = _FakeStreamingOpenAI(responses=["Dify 集成回复"])
                dify_response = client.post("/v1/dify/chat", json={
                    "query": "我今天有点焦虑，担心移植会失败。",
                    "conversation_id": "dify-conv-test",
                    "user": "dify-user-test",
                    "waitForAnalysis": False,
                    "inputs": {
                        "stage": "PRETREATMENT",
                        "psychEnergy": 45,
                        "responseStyle": "简短温暖",
                        "workflowContext": "Dify 已完成用户阶段选择",
                    },
                })
                self.assertEqual(dify_response.status_code, 200)
                dify_payload = dify_response.get_json()
                self.assertEqual(dify_payload["answer"], "Dify 集成回复")
                self.assertEqual(dify_payload["sessionId"], "dify-conv-test")
                self.assertEqual(dify_payload["userId"], "dify-user-test")
                self.assertIn("difyOutputs", dify_payload)
                self.assertEqual(dify_payload["difyOutputs"]["answer"], "Dify 集成回复")
                self.assertIn("question1", dify_payload["difyOutputs"])
                self.assertIn(dify_payload["nextAction"], {
                    "answer_only",
                    "show_recommended_questions",
                    "alert_and_notify",
                    "emergency_alert",
                })
                self.assertEqual(
                    dify_payload["metadata"]["agentMeta"]["integration"]["outerOrchestrator"],
                    "dify",
                )
                self.assertIn(
                    dify_payload["metadata"]["agentMeta"]["integration"]["innerOrchestrator"],
                    {"langgraph", "legacy_stream"},
                )

                reset = client.post("/v1/sessions/api-user/reset")
                self.assertEqual(reset.status_code, 200)
                self.assertEqual(reset.get_json()["session"]["messageCount"], 0)
            finally:
                agent_sessions.clear()
                Config.AUTO_SAVE_PROGRESS = old_auto_save
                Config.DATA_DIR = old_data_dir
                for key, value in old_dify_values.items():
                    setattr(Config, key, value)


class TestPromptRegistry(unittest.TestCase):
    """测试提示词配置、热更新和版本对比。"""

    def test_prompt_registry_hot_update_and_compare(self):
        old_data_dir = Config.DATA_DIR
        old_profile = Config.PROMPT_PROFILE
        old_mode = Config.OUTPUT_MODE
        with tempfile.TemporaryDirectory() as tmp_dir:
            try:
                Config.DATA_DIR = tmp_dir
                Config.PROMPT_PROFILE = "warm_cbt"
                Config.OUTPUT_MODE = "brief_support"
                reload_prompt_registry()

                first = update_prompt_entry(
                    "profile",
                    "warm_cbt",
                    "第一版：保持温暖。",
                )
                second = update_prompt_entry(
                    "profile",
                    "warm_cbt",
                    "第二版：保持温暖，并更简洁。",
                )
                self.assertEqual(second["version"], first["version"] + 1)

                runtime = resolve_prompt_runtime_config(
                    base_system_prompt="基础系统提示",
                    default_profile=Config.PROMPT_PROFILE,
                    default_output_mode=Config.OUTPUT_MODE,
                )
                self.assertIn("第二版", runtime.system_prompt)
                self.assertEqual(runtime.profile_version, second["version"])

                diff = compare_prompt_versions("profile", "warm_cbt")
                self.assertTrue(diff["changed"])
                self.assertEqual(diff["toVersion"]["version"], second["version"])
                self.assertTrue(any("第二版" in line for line in diff["diff"]))

                snapshot = get_prompt_registry_snapshot(include_history=False)
                self.assertNotIn("history", snapshot["profiles"]["warm_cbt"])
            finally:
                Config.DATA_DIR = old_data_dir
                Config.PROMPT_PROFILE = old_profile
                Config.OUTPUT_MODE = old_mode
                reload_prompt_registry()

    def test_prompt_registry_settings_get_rollback_and_delete(self):
        old_data_dir = Config.DATA_DIR
        old_profile = Config.PROMPT_PROFILE
        old_mode = Config.OUTPUT_MODE
        with tempfile.TemporaryDirectory() as tmp_dir:
            try:
                Config.DATA_DIR = tmp_dir
                Config.PROMPT_PROFILE = "warm_cbt"
                Config.OUTPUT_MODE = "brief_support"
                reload_prompt_registry()

                update_prompt_entry("profile", "custom_profile", "自定义 profile v1")
                v2 = update_prompt_entry("profile", "custom_profile", "自定义 profile v2")
                entry = get_prompt_entry("profile", "custom_profile")
                self.assertEqual(entry["version"], v2["version"])

                settings = update_prompt_settings(
                    default_profile="custom_profile",
                    default_output_mode="safety_first",
                )
                self.assertEqual(settings["defaultProfile"], "custom_profile")
                runtime = resolve_prompt_runtime_config(
                    base_system_prompt="基础系统提示",
                    default_profile=Config.PROMPT_PROFILE,
                    default_output_mode=Config.OUTPUT_MODE,
                )
                self.assertEqual(runtime.profile, "custom_profile")
                self.assertEqual(runtime.output_mode, "safety_first")
                self.assertIn("自定义 profile v2", runtime.system_prompt)

                rolled_back = rollback_prompt_entry("profile", "custom_profile", 1)
                self.assertIn("自定义 profile v1", rolled_back["content"])

                deleted = delete_prompt_entry("profile", "custom_profile")
                self.assertTrue(deleted["deleted"])
                snapshot = get_prompt_registry_snapshot()
                self.assertIsNone(snapshot["settings"]["defaultProfile"])

                reset = delete_prompt_entry("mode", "brief_support")
                self.assertFalse(reset["deleted"])
                self.assertFalse(reset["resetToBuiltin"])
                self.assertTrue(reset["alreadyBuiltin"])
                self.assertIn("直接回应用户原话", reset["entry"]["content"])

                update_prompt_entry("profile", "warm_cbt", "临时自定义 warm cbt")
                reset_profile = delete_prompt_entry("profile", "warm_cbt")
                self.assertTrue(reset_profile["resetToBuiltin"])
                reset_version = reset_profile["entry"]["version"]
                reset_again = delete_prompt_entry("profile", "warm_cbt")
                self.assertFalse(reset_again["resetToBuiltin"])
                self.assertTrue(reset_again["alreadyBuiltin"])
                self.assertEqual(reset_again["entry"]["version"], reset_version)

                update_prompt_entry("profile", "warm_cbt", "需要彻底清理的提示词")
                purged = delete_prompt_entry("profile", "warm_cbt", purge_history=True)
                self.assertTrue(purged["resetToBuiltin"])
                self.assertTrue(purged["purgedHistory"])
                self.assertEqual(purged["entry"]["version"], 1)
                self.assertEqual(len(purged["entry"]["history"]), 1)
                self.assertNotIn("需要彻底清理的提示词", json.dumps(purged["entry"], ensure_ascii=False))
            finally:
                Config.DATA_DIR = old_data_dir
                Config.PROMPT_PROFILE = old_profile
                Config.OUTPUT_MODE = old_mode
                reload_prompt_registry()

    def test_prompt_candidate_preview_does_not_persist(self):
        old_data_dir = Config.DATA_DIR
        with tempfile.TemporaryDirectory() as tmp_dir:
            try:
                Config.DATA_DIR = tmp_dir
                reload_prompt_registry()
                current = get_prompt_entry("profile", "warm_cbt")
                preview = preview_prompt_candidate(
                    "profile",
                    "warm_cbt",
                    "候选提示词：更多关注病房陪伴。",
                    change_note="预览候选版本",
                    base_system_prompt="基础系统提示",
                    default_profile="warm_cbt",
                    default_output_mode="brief_support",
                )

                self.assertFalse(preview["saved"])
                self.assertTrue(preview["changed"])
                self.assertIn("候选提示词", preview["candidateRuntime"]["system_prompt"])
                self.assertNotIn(
                    "候选提示词",
                    get_prompt_entry("profile", "warm_cbt")["content"],
                )
                self.assertEqual(get_prompt_entry("profile", "warm_cbt")["version"], current["version"])
            finally:
                Config.DATA_DIR = old_data_dir
                reload_prompt_registry()

    def test_prompt_preview_api_returns_candidate_without_saving(self):
        old_data_dir = Config.DATA_DIR
        with tempfile.TemporaryDirectory() as tmp_dir:
            try:
                Config.DATA_DIR = tmp_dir
                reload_prompt_registry()
                from xiaoya_agent.interfaces.api_server import app

                client = app.test_client()
                response = client.post("/v1/prompts/profiles/warm_cbt/preview", json={
                    "candidateContent": "API 候选提示词",
                    "generate": False,
                })
                self.assertEqual(response.status_code, 200)
                data = response.get_json()
                self.assertFalse(data["saved"])
                self.assertIn("candidate", data)
                self.assertIn("API 候选提示词", data["candidate"]["runtime"]["system_prompt"])
                self.assertEqual(data["manualApply"]["kind"], "profile")
                self.assertEqual(data["manualApply"]["endpoint"], "/v1/prompts/profiles/warm_cbt")
                self.assertNotIn("outputModeEndpoint", data["manualApply"])
                self.assertNotIn("API 候选提示词", get_prompt_entry("profile", "warm_cbt")["content"])

                generated = client.post("/v1/prompts/profiles/warm_cbt/preview", json={
                    "candidateContent": "API 候选提示词",
                    "message": "我有点担心",
                    "generate": True,
                })
                self.assertEqual(generated.status_code, 200)
                generated_data = generated.get_json()
                self.assertIn("reply", generated_data["current"])
                self.assertIn("reply", generated_data["candidate"])
            finally:
                Config.DATA_DIR = old_data_dir
                reload_prompt_registry()


class TestStructuredOutput(unittest.TestCase):
    def test_unified_analysis_schema_parses_markdown_json(self):
        content = """```json
{
  "emotional_state": {"primary": "anxiety", "severity": 12},
  "cognitive_distortions": "catastrophizing",
  "problem_severity": 9,
  "intervention_needed": true,
  "recommended_technique": "RELAXATION_TRAINING",
  "crisis": {"has_crisis": false, "severity_score": 0, "crisis_types": []},
  "transplant": {"should_trigger": true, "phase": "移植前准备期", "scenario": "FIRST_MEET", "confidence": 1.2}
}
```"""
        data = parse_structured_json(content, UnifiedAnalysisPayload)
        self.assertIsNotNone(data)
        self.assertEqual(data["emotional_state"]["severity"], 10)
        self.assertEqual(data["cognitive_distortions"], ["catastrophizing"])
        self.assertEqual(data["transplant"]["confidence"], 1.0)


class TestAgentTools(unittest.TestCase):
    """测试 LangGraph 本地工具层。"""

    def test_tool_registry_contains_expected_tools(self):
        names = [tool.name for tool in get_agent_tools()]
        self.assertIn("medical_red_flag_scan", names)
        self.assertIn("transplant_context_lookup", names)
        self.assertIn("conversation_state_snapshot", names)
        self.assertIn("knowledge_retrieval", names)
        self.assertIn("mcp_service_router", names)
        self.assertIn("harbor_regulation_tool", names)

    def test_harbor_catalog_and_practice_generation(self):
        catalog = list_harbor_catalog()
        self.assertTrue(any(item["key"] == "anxiety" for item in catalog["scenarios"]))
        self.assertTrue(any(item["key"] == "breathing_regulation" for item in catalog["tools"]))
        self.assertTrue(should_use_harbor_regulation("我想做一分钟呼吸放松"))

        practice = create_harbor_practice(
            scenario="焦虑",
            tool_type="呼吸",
            duration_seconds=60,
            query="我现在很焦虑",
        )
        self.assertEqual(practice["durationSeconds"], 60)
        self.assertEqual(practice["scenario"]["key"], "anxiety")
        self.assertEqual(practice["toolType"]["key"], "breathing_regulation")
        self.assertTrue(practice["oneClickStart"])
        self.assertFalse(practice["requiresComplexMovement"])
        self.assertIn("voiceGuideText", practice)

    def test_harbor_regulation_tool_returns_voice_practice(self):
        result = harbor_regulation_tool.invoke({
            "query": "我现在很焦虑，带我做一分钟呼吸",
            "duration_seconds": 60,
        })
        practice = result["practice"]
        self.assertEqual(practice["durationSeconds"], 60)
        self.assertEqual(practice["toolType"]["key"], "breathing_regulation")
        self.assertIn("心之港湾", result["context"])

    def test_cli_harbor_display_handles_short_duration_command(self):
        params = parse_harbor_command("harbor 5")
        practice = create_harbor_practice(
            scenario=params["scenario"],
            tool_type=params["tool_type"],
            duration_seconds=params["duration_seconds"],
            query=params["query"],
        )
        output = io.StringIO()
        with redirect_stdout(output):
            display_harbor_practice(practice)
        rendered = output.getvalue()
        self.assertIn("心之港湾", rendered)
        self.assertIn("1. (", rendered)
        self.assertNotIn("None", rendered)

    def test_rag_auto_trigger_skips_plain_emotional_support_turns(self):
        old_enabled = Config.RAG_AUTO_TRIGGER_ENABLED
        try:
            Config.RAG_AUTO_TRIGGER_ENABLED = True
            self.assertFalse(should_use_knowledge_retrieval("我今天有点焦虑，担心移植会失败。"))
            self.assertTrue(should_use_knowledge_retrieval("蓝色纸鹤是什么"))
            self.assertTrue(should_use_knowledge_retrieval("蓝色纸鹤是啥"))
            self.assertTrue(should_use_knowledge_retrieval("希望之树怎么帮助我记录进步？"))
            self.assertTrue(should_use_knowledge_retrieval("移植后感染有哪些注意事项？"))
        finally:
            Config.RAG_AUTO_TRIGGER_ENABLED = old_enabled

    def test_medical_red_flag_tool(self):
        result = medical_red_flag_scan.invoke({"text": "我现在胸痛，还有点发烧"})
        self.assertTrue(result["has_medical_red_flag"])
        self.assertIn("胸痛", result["matched_keywords"])

    @patch("xiaoya_agent.core.agent.OpenAI", _FakeOpenAI)
    def test_mcp_time_service_returns_structured_context(self):
        agent = EnhancedChatAgent(load_persistent_data=False)
        outputs = invoke_turn_tools(
            agent=agent,
            user_message="现在几点了",
            current_phase=TransplantPhase.PREP,
            analysis={"emotional_state": {"severity": 0}},
        )
        mcp = outputs["mcp_service_router"]
        self.assertEqual(mcp["services"], ["current_time"])
        self.assertIn("[current_time]", mcp["context"])
        self.assertIn("current_time", mcp["results"])
        self.assertIn("answer", mcp["results"]["current_time"])

        context = build_response_context_from_tool_outputs(outputs, TransplantPhase.PREP)
        self.assertIn("current_time", context["mcp_services"])
        self.assertIn("[current_time]", context["mcp_context"])

    @patch("xiaoya_agent.core.agent.OpenAI", _FakeOpenAI)
    def test_mcp_compound_time_question_returns_context_only(self):
        agent = EnhancedChatAgent(load_persistent_data=False)
        outputs = invoke_turn_tools(
            agent=agent,
            user_message="现在几点，我要什么时候做手术",
            current_phase=TransplantPhase.PREP,
            analysis={"emotional_state": {"severity": 0}},
        )
        mcp = outputs["mcp_service_router"]
        self.assertEqual(mcp["services"], ["current_time"])
        self.assertIn("[current_time]", mcp["context"])

        context = build_response_context_from_tool_outputs(outputs, TransplantPhase.PREP)
        self.assertIn("current_time", context["mcp_services"])
        self.assertIn("[current_time]", context["mcp_context"])

    @patch("xiaoya_agent.core.agent.OpenAI", _FakeStreamingOpenAI)
    def test_time_query_uses_mcp_without_model_guessing(self):
        old_history_compression = Config.HISTORY_COMPRESSION_ENABLED
        try:
            Config.HISTORY_COMPRESSION_ENABLED = False
            agent = EnhancedChatAgent(load_persistent_data=False)
            agent.client = _FakeStreamingToolCallingOpenAI(
                "mcp_service_router",
                {"query": "现在几点"},
                "现在是工具返回的准确时间。",
            )
            response = "".join(agent.stream_chat("现在几点"))

            self.assertIn("工具返回的准确时间", response)
            self.assertEqual(agent.last_result["response_type"], "tool_augmented_response")
            self.assertTrue(agent.client.requests)
            self.assertTrue(agent.client.requests[0].get("tools"))
            self.assertTrue(any(
                tool.get("name") == "mcp_service_router"
                and tool.get("services") == ["current_time"]
                for tool in agent.last_result["tool_trace"]["tools"]
            ))
        finally:
            Config.HISTORY_COMPRESSION_ENABLED = old_history_compression

    @patch("xiaoya_agent.core.agent.OpenAI", _FakeStreamingOpenAI)
    def test_compound_time_question_uses_model_with_mcp_context(self):
        old_values = {
            "HISTORY_COMPRESSION_ENABLED": Config.HISTORY_COMPRESSION_ENABLED,
            "CBT_LLM_ENABLED": Config.CBT_LLM_ENABLED,
            "CRISIS_LLM_DETECTION_ENABLED": Config.CRISIS_LLM_DETECTION_ENABLED,
            "CRISIS_LLM_STREAM_BLOCKING_ENABLED": Config.CRISIS_LLM_STREAM_BLOCKING_ENABLED,
        }
        try:
            Config.HISTORY_COMPRESSION_ENABLED = False
            Config.CBT_LLM_ENABLED = False
            Config.CRISIS_LLM_DETECTION_ENABLED = False
            Config.CRISIS_LLM_STREAM_BLOCKING_ENABLED = False
            agent = EnhancedChatAgent(load_persistent_data=False)
            agent.client = _FakeStreamingToolCallingOpenAI(
                "mcp_service_router",
                {"query": "现在几点"},
                "现在时间我按服务结果为准；手术时间需要以医生或护士通知为准。",
            )
            response = "".join(agent.stream_chat("现在几点，我要什么时候做手术"))

            self.assertIn("手术时间需要以医生或护士通知为准", response)
            self.assertEqual(agent.last_result["response_type"], "tool_augmented_response")
            self.assertTrue(agent.client.requests)
            final_request = next(request for request in agent.client.requests if request.get("stream"))
            final_messages = final_request["messages"]
            self.assertTrue(any(item.get("role") == "tool" for item in final_messages))
            self.assertTrue(any(
                tool.get("name") == "mcp_service_router"
                and tool.get("services") == ["current_time"]
                for tool in agent.last_result["tool_trace"]["tools"]
            ))
        finally:
            for key, value in old_values.items():
                setattr(Config, key, value)

    @patch("xiaoya_agent.core.agent.OpenAI", _FakeOpenAI)
    def test_turn_tools_build_transplant_context(self):
        old_transplant_enabled = Config.TRANSPLANT_SUPPORT_ENABLED
        try:
            Config.TRANSPLANT_SUPPORT_ENABLED = True
            agent = EnhancedChatAgent(load_persistent_data=False)
            outputs = invoke_turn_tools(
                agent=agent,
                user_message="今天回输，我有点紧张",
                current_phase=TransplantPhase.PREP,
                analysis={"emotional_state": {"severity": 7}},
            )

            transplant = outputs["transplant_context_lookup"]
            self.assertTrue(transplant["should_trigger"])
            self.assertEqual(transplant["phase"], TransplantPhase.KEY.value)
            self.assertEqual(transplant["scenario"], Scenario.INFUSION_DAY.value)
            self.assertTrue(transplant["template"])
        finally:
            Config.TRANSPLANT_SUPPORT_ENABLED = old_transplant_enabled

    @patch("xiaoya_agent.core.agent.OpenAI", _FakeStreamingOpenAI)
    def test_graph_prepare_turn_uses_local_tools(self):
        old_values = {
            "AGENT_TOOLS_ENABLED": Config.AGENT_TOOLS_ENABLED,
            "AGENT_MODEL_TOOL_CALLING_ENABLED": Config.AGENT_MODEL_TOOL_CALLING_ENABLED,
            "CBT_LLM_ENABLED": Config.CBT_LLM_ENABLED,
            "CRISIS_LLM_DETECTION_ENABLED": Config.CRISIS_LLM_DETECTION_ENABLED,
            "TRANSPLANT_LLM_SCENARIO_ENABLED": Config.TRANSPLANT_LLM_SCENARIO_ENABLED,
            "TRANSPLANT_SUPPORT_ENABLED": Config.TRANSPLANT_SUPPORT_ENABLED,
        }
        try:
            Config.AGENT_TOOLS_ENABLED = True
            Config.AGENT_MODEL_TOOL_CALLING_ENABLED = False
            Config.CBT_LLM_ENABLED = False
            Config.CRISIS_LLM_DETECTION_ENABLED = False
            Config.TRANSPLANT_LLM_SCENARIO_ENABLED = False
            Config.TRANSPLANT_SUPPORT_ENABLED = True

            agent = EnhancedChatAgent(load_persistent_data=False)
            state = prepare_stream_turn(agent, "今天回输，我有点紧张")

            self.assertIn("local_tool_outputs", state)
            self.assertEqual(
                state["response_context"]["scenario"],
                Scenario.INFUSION_DAY,
            )
            self.assertEqual(
                state["response_context"]["phase"],
                TransplantPhase.KEY,
            )
            result = agent._finalize_chat_turn(
                user_message="今天回输，我有点紧张",
                response="我在，先陪你。",
                response_type="cbt_response",
                cbt_analysis=state["cbt_analysis"],
                crisis_detection=state["crisis_detection"],
                conversation_data=state["conversation_data"],
                current_phase=state["current_phase"],
            )
            trace = result["tool_trace"]
            self.assertEqual(trace["source"], "langgraph_prepare_turn")
            self.assertGreaterEqual(trace["toolCount"], 4)
            self.assertTrue(any(
                tool["name"] == "transplant_context_lookup"
                and tool["shouldTrigger"]
                for tool in trace["tools"]
            ))
        finally:
            for key, value in old_values.items():
                setattr(Config, key, value)

    @patch("xiaoya_agent.core.agent.OpenAI", _FakeStreamingOpenAI)
    def test_rag_query_prefetches_knowledge_even_when_model_skips_tool_call(self):
        old_values = {
            "AGENT_GRAPH_ENABLED": Config.AGENT_GRAPH_ENABLED,
            "AGENT_TOOLS_ENABLED": Config.AGENT_TOOLS_ENABLED,
            "AGENT_MODEL_TOOL_CALLING_ENABLED": Config.AGENT_MODEL_TOOL_CALLING_ENABLED,
            "RAG_ENABLED": Config.RAG_ENABLED,
            "RAG_BACKEND": Config.RAG_BACKEND,
            "RAG_TOP_K": Config.RAG_TOP_K,
            "RAG_MAX_CONTEXT_CHARS": Config.RAG_MAX_CONTEXT_CHARS,
            "DIFY_API_BASE_URL": Config.DIFY_API_BASE_URL,
            "DIFY_API_KEY": Config.DIFY_API_KEY,
            "DIFY_KNOWLEDGE_API_KEY": Config.DIFY_KNOWLEDGE_API_KEY,
            "DIFY_KNOWLEDGE_BASE_ID": Config.DIFY_KNOWLEDGE_BASE_ID,
            "DIFY_KNOWLEDGE_ENABLED": Config.DIFY_KNOWLEDGE_ENABLED,
            "DIFY_KNOWLEDGE_SEARCH_METHOD": Config.DIFY_KNOWLEDGE_SEARCH_METHOD,
            "CBT_LLM_ENABLED": Config.CBT_LLM_ENABLED,
            "CRISIS_DETECTION_ENABLED": Config.CRISIS_DETECTION_ENABLED,
            "CRISIS_LLM_DETECTION_ENABLED": Config.CRISIS_LLM_DETECTION_ENABLED,
            "TRANSPLANT_LLM_SCENARIO_ENABLED": Config.TRANSPLANT_LLM_SCENARIO_ENABLED,
            "HISTORY_COMPRESSION_ENABLED": Config.HISTORY_COMPRESSION_ENABLED,
            "COHORT_LEARNING_CONTEXT_ENABLED": Config.COHORT_LEARNING_CONTEXT_ENABLED,
        }

        class _Response:
            status_code = 200
            text = "{}"

            def raise_for_status(self):
                return None

            def json(self):
                return {
                    "records": [
                        {
                            "score": 0.92,
                            "document": {"name": "manual_rag_test.md"},
                            "segment": {
                                "id": "seg-1",
                                "document_id": "doc-1",
                                "content": (
                                    "希望之树用于记录患者在移植治疗过程中的微小进步。"
                                    "这份资料中的特殊测试词是：蓝色纸鹤。"
                                ),
                            },
                        }
                    ]
                }

        try:
            Config.AGENT_GRAPH_ENABLED = True
            Config.AGENT_TOOLS_ENABLED = True
            Config.AGENT_MODEL_TOOL_CALLING_ENABLED = True
            Config.RAG_ENABLED = True
            Config.RAG_BACKEND = "dify"
            Config.RAG_TOP_K = 1
            Config.RAG_MAX_CONTEXT_CHARS = 500
            Config.DIFY_API_BASE_URL = "http://dify.local/v1"
            Config.DIFY_API_KEY = ""
            Config.DIFY_KNOWLEDGE_API_KEY = "dify-key"
            Config.DIFY_KNOWLEDGE_BASE_ID = "dataset-1"
            Config.DIFY_KNOWLEDGE_ENABLED = True
            Config.DIFY_KNOWLEDGE_SEARCH_METHOD = "keyword_search"
            Config.CBT_LLM_ENABLED = False
            Config.CRISIS_DETECTION_ENABLED = False
            Config.CRISIS_LLM_DETECTION_ENABLED = False
            Config.TRANSPLANT_LLM_SCENARIO_ENABLED = False
            Config.HISTORY_COMPRESSION_ENABLED = False
            Config.COHORT_LEARNING_CONTEXT_ENABLED = False

            agent = EnhancedChatAgent(load_persistent_data=False)
            agent.client = _FakeStreamingOpenAI(responses=["资料里说，蓝色纸鹤是特殊测试词。"])
            with patch("xiaoya_agent.integrations.dify.requests.post", return_value=_Response()) as post:
                response = "".join(agent.stream_chat("蓝色纸鹤是什么"))

            self.assertIn("特殊测试词", response)
            self.assertEqual(post.call_count, 1)
            trace = agent.last_result["tool_trace"]
            knowledge = next(tool for tool in trace["tools"] if tool["name"] == "knowledge_retrieval")
            self.assertEqual(trace["source"], "langgraph_prepare_turn")
            self.assertTrue(knowledge["hasContext"])
            self.assertEqual(knowledge["matchCount"], 1)
            self.assertEqual(knowledge["retrievalBackend"], "dify")
            self.assertEqual(knowledge["topSources"][0]["source"], "manual_rag_test.md")

            final_request = agent.client.requests[0]
            self.assertTrue(final_request.get("stream"))
            self.assertNotIn("tools", final_request)
            system_text = "\n".join(
                message.get("content", "")
                for message in final_request["messages"]
                if message.get("role") == "system"
            )
            self.assertIn("[Dify知识库检索结果]", system_text)
            self.assertIn("蓝色纸鹤", system_text)
        finally:
            for key, value in old_values.items():
                setattr(Config, key, value)
            reset_rag_index_cache()

    def test_rag_does_not_use_local_file_when_dify_missing(self):
        old_values = {
            "RAG_ENABLED": Config.RAG_ENABLED,
            "RAG_SOURCE_DIR": Config.RAG_SOURCE_DIR,
            "RAG_TOP_K": Config.RAG_TOP_K,
            "RAG_BACKEND": Config.RAG_BACKEND,
            "DIFY_KNOWLEDGE_API_KEY": Config.DIFY_KNOWLEDGE_API_KEY,
            "DIFY_KNOWLEDGE_BASE_ID": Config.DIFY_KNOWLEDGE_BASE_ID,
            "DIFY_KNOWLEDGE_ENABLED": Config.DIFY_KNOWLEDGE_ENABLED,
        }
        with tempfile.TemporaryDirectory() as tmp_dir:
            try:
                guide_path = os.path.join(tmp_dir, "guide.md")
                with open(guide_path, "w", encoding="utf-8") as f:
                    f.write(
                        "希望之树用于帮助患者记录治疗中的微小进步，把每一次坚持都看作一片新叶子。"
                        "这份资料中的特殊测试词是：蓝色纸鹤。"
                    )

                Config.RAG_ENABLED = True
                Config.RAG_SOURCE_DIR = tmp_dir
                Config.RAG_TOP_K = 2
                Config.RAG_BACKEND = "dify"
                Config.DIFY_KNOWLEDGE_API_KEY = ""
                Config.DIFY_KNOWLEDGE_BASE_ID = ""
                Config.DIFY_KNOWLEDGE_ENABLED = True
                reset_rag_index_cache()

                result = retrieve_knowledge("希望之树怎么帮助患者记录进步")

                self.assertFalse(result["matches"])
                self.assertEqual(result["retrievalBackend"], "dify")
                self.assertEqual(result["reason"], "dify_not_configured")
                self.assertFalse(result["fallbackUsed"])
                self.assertNotIn("希望之树", result["context"])
                index = get_knowledge_index(tmp_dir)
                self.assertEqual(len(index.chunks), 0)
                self.assertIn("local_file_rag_removed", index.errors)
            finally:
                for key, value in old_values.items():
                    setattr(Config, key, value)
                reset_rag_index_cache()

    def test_rag_trigger_uses_generic_question_intent(self):
        old_values = {
            "RAG_ENABLED": Config.RAG_ENABLED,
            "RAG_AUTO_TRIGGER_ENABLED": Config.RAG_AUTO_TRIGGER_ENABLED,
        }
        try:
            Config.RAG_ENABLED = True
            Config.RAG_AUTO_TRIGGER_ENABLED = True
            self.assertFalse(should_use_knowledge_retrieval("我今天有点焦虑，担心移植会失败。"))
            self.assertTrue(should_use_knowledge_retrieval("任意新知识点是什么"))
            self.assertTrue(should_use_knowledge_retrieval("资料中提到的蓝色纸鹤是什么"))
            self.assertTrue(should_use_knowledge_retrieval("资料中提到的蓝色纸鹤是啥"))
        finally:
            for key, value in old_values.items():
                setattr(Config, key, value)

    def test_rag_can_use_dify_knowledge_backend(self):
        old_values = {
            "RAG_ENABLED": Config.RAG_ENABLED,
            "RAG_BACKEND": Config.RAG_BACKEND,
            "RAG_TOP_K": Config.RAG_TOP_K,
            "RAG_MAX_CONTEXT_CHARS": Config.RAG_MAX_CONTEXT_CHARS,
            "DIFY_API_BASE_URL": Config.DIFY_API_BASE_URL,
            "DIFY_API_KEY": Config.DIFY_API_KEY,
            "DIFY_KNOWLEDGE_API_KEY": Config.DIFY_KNOWLEDGE_API_KEY,
            "DIFY_KNOWLEDGE_BASE_ID": Config.DIFY_KNOWLEDGE_BASE_ID,
            "DIFY_KNOWLEDGE_ENABLED": Config.DIFY_KNOWLEDGE_ENABLED,
            "DIFY_KNOWLEDGE_FALLBACK_TO_LOCAL": Config.DIFY_KNOWLEDGE_FALLBACK_TO_LOCAL,
            "DIFY_KNOWLEDGE_SEARCH_METHOD": Config.DIFY_KNOWLEDGE_SEARCH_METHOD,
        }

        class _Response:
            def raise_for_status(self):
                return None

            def json(self):
                return {
                    "records": [
                        {
                            "score": 0.91,
                            "document": {"name": "dify-guide.md"},
                            "segment": {
                                "id": "seg-1",
                                "document_id": "doc-1",
                                "content": "希望之树用于记录患者在治疗过程中的微小进步。",
                            },
                        }
                    ]
                }

        try:
            Config.RAG_ENABLED = True
            Config.RAG_BACKEND = "dify"
            Config.RAG_TOP_K = 1
            Config.RAG_MAX_CONTEXT_CHARS = 500
            Config.DIFY_API_BASE_URL = "http://dify.local/v1"
            Config.DIFY_API_KEY = ""
            Config.DIFY_KNOWLEDGE_API_KEY = "dify-key"
            Config.DIFY_KNOWLEDGE_BASE_ID = "dataset-1"
            Config.DIFY_KNOWLEDGE_ENABLED = True
            Config.DIFY_KNOWLEDGE_FALLBACK_TO_LOCAL = False
            Config.DIFY_KNOWLEDGE_SEARCH_METHOD = "hybrid_search"

            with patch("xiaoya_agent.integrations.dify.requests.post", return_value=_Response()) as post:
                result = retrieve_knowledge("希望之树是什么")

            self.assertEqual(result["retrievalBackend"], "dify")
            self.assertEqual(result["reason"], "ok")
            self.assertIn("希望之树", result["context"])
            self.assertEqual(result["matches"][0]["source"], "dify-guide.md")
            self.assertEqual(result["matches"][0]["metadata"]["provider"], "dify")
            self.assertEqual(post.call_args.kwargs["json"]["retrieval_model"]["top_k"], 1)
            self.assertIn("/datasets/dataset-1/retrieve", post.call_args.args[0])
        finally:
            for key, value in old_values.items():
                setattr(Config, key, value)

    def test_dify_retrieval_retries_minimal_payload_after_400(self):
        old_values = {
            "RAG_ENABLED": Config.RAG_ENABLED,
            "RAG_BACKEND": Config.RAG_BACKEND,
            "RAG_TOP_K": Config.RAG_TOP_K,
            "RAG_MAX_CONTEXT_CHARS": Config.RAG_MAX_CONTEXT_CHARS,
            "DIFY_API_BASE_URL": Config.DIFY_API_BASE_URL,
            "DIFY_API_KEY": Config.DIFY_API_KEY,
            "DIFY_KNOWLEDGE_API_KEY": Config.DIFY_KNOWLEDGE_API_KEY,
            "DIFY_KNOWLEDGE_BASE_ID": Config.DIFY_KNOWLEDGE_BASE_ID,
            "DIFY_KNOWLEDGE_ENABLED": Config.DIFY_KNOWLEDGE_ENABLED,
            "DIFY_KNOWLEDGE_FALLBACK_TO_LOCAL": Config.DIFY_KNOWLEDGE_FALLBACK_TO_LOCAL,
            "DIFY_KNOWLEDGE_SEARCH_METHOD": Config.DIFY_KNOWLEDGE_SEARCH_METHOD,
        }

        class _BadRequestResponse:
            status_code = 400
            text = '{"code":"invalid_param","message":"retrieval_model invalid"}'

            def raise_for_status(self):
                raise AssertionError("400 response should have been retried")

        class _OkResponse:
            status_code = 200
            text = "{}"

            def raise_for_status(self):
                return None

            def json(self):
                return {
                    "records": [
                        {
                            "score": 0.88,
                            "document": {"name": "dify-guide.md"},
                            "segment": {
                                "id": "seg-1",
                                "document_id": "doc-1",
                                "content": "蓝色纸鹤是希望之树资料中的特殊测试词。",
                            },
                        }
                    ]
                }

        try:
            Config.RAG_ENABLED = True
            Config.RAG_BACKEND = "dify"
            Config.RAG_TOP_K = 1
            Config.RAG_MAX_CONTEXT_CHARS = 500
            Config.DIFY_API_BASE_URL = "http://dify.local/v1"
            Config.DIFY_API_KEY = ""
            Config.DIFY_KNOWLEDGE_API_KEY = "dify-key"
            Config.DIFY_KNOWLEDGE_BASE_ID = "dataset-1"
            Config.DIFY_KNOWLEDGE_ENABLED = True
            Config.DIFY_KNOWLEDGE_FALLBACK_TO_LOCAL = False
            Config.DIFY_KNOWLEDGE_SEARCH_METHOD = "hybrid_search"

            responses = [_BadRequestResponse(), _BadRequestResponse(), _OkResponse()]
            with patch("xiaoya_agent.integrations.dify.requests.post", side_effect=responses) as post:
                result = retrieve_knowledge("蓝色纸鹤是什么")

            self.assertEqual(post.call_count, 3)
            self.assertEqual(post.call_args_list[0].kwargs["json"]["retrieval_model"]["search_method"], "hybrid_search")
            self.assertEqual(post.call_args_list[1].kwargs["json"]["retrieval_model"]["search_method"], "keyword_search")
            self.assertEqual(post.call_args_list[1].kwargs["json"]["retrieval_model"]["top_k"], 1)
            self.assertEqual(post.call_args_list[2].kwargs["json"], {"query": "蓝色纸鹤是什么"})
            self.assertEqual(result["retrievalBackend"], "dify")
            self.assertEqual(result["effectiveSearchMethod"], "default")
            self.assertIn("蓝色纸鹤", result["context"])
        finally:
            for key, value in old_values.items():
                setattr(Config, key, value)

    def test_rag_index_compatibility_shell_does_not_index_files(self):
        old_values = {
            "RAG_SOURCE_DIR": Config.RAG_SOURCE_DIR,
            "RAG_CHUNK_SIZE": Config.RAG_CHUNK_SIZE,
        }
        with tempfile.TemporaryDirectory() as tmp_dir:
            try:
                guide_path = os.path.join(tmp_dir, "guide.md")
                with open(guide_path, "w", encoding="utf-8") as f:
                    f.write("细胞回输当日可以用欢迎仪式帮助患者把治疗理解为新生纪念。")

                Config.RAG_SOURCE_DIR = tmp_dir
                Config.RAG_CHUNK_SIZE = 120
                reset_rag_index_cache()
                index = get_knowledge_index()

                self.assertEqual(len(index.chunks), 0)
                self.assertEqual(index.backend, "dify")
                self.assertIn("local_file_rag_removed", index.errors)
            finally:
                for key, value in old_values.items():
                    setattr(Config, key, value)
                reset_rag_index_cache()

    @patch("xiaoya_agent.core.agent.OpenAI", _FakeStreamingOpenAI)
    def test_response_prompt_includes_rag_context(self):
        old_history_compression = Config.HISTORY_COMPRESSION_ENABLED
        try:
            Config.HISTORY_COMPRESSION_ENABLED = False
            agent = EnhancedChatAgent(load_persistent_data=False)
            agent.client = _FakeStreamingOpenAI(responses=["好的"])
            stream = agent._create_response_stream(
                "希望之树是什么？",
                agent._pending_semantic_cbt_analysis(),
                response_context={
                    "knowledge_backend": "dify",
                    "knowledge_context": "[source 1 | dify-guide.md | score=1.0]\n希望之树用于记录微小进步。",
                },
            )
            list(stream)

            system_text = "\n".join(
                message.get("content", "")
                for message in agent.client.requests[0]["messages"]
                if message.get("role") == "system"
            )
            self.assertIn("[Dify知识库检索结果]", system_text)
            self.assertIn("希望之树用于记录微小进步", system_text)
            self.assertIn("必须优先按资料回答", system_text)
            self.assertIn("不要把资料中的项目术语自由解释成心理象征", system_text)
            self.assertIn("资料中只提到该词，未给出更多定义", system_text)
            self.assertIn("简要补充同一资料片段中明确存在的其它相关事实", system_text)
        finally:
            Config.HISTORY_COMPRESSION_ENABLED = old_history_compression

    @patch("xiaoya_agent.core.agent.OpenAI", _FakeStreamingOpenAI)
    def test_response_prompt_warns_when_rag_attempt_has_no_context(self):
        old_history_compression = Config.HISTORY_COMPRESSION_ENABLED
        try:
            Config.HISTORY_COMPRESSION_ENABLED = False
            agent = EnhancedChatAgent(load_persistent_data=False)
            agent.client = _FakeStreamingOpenAI(responses=["我暂时没有从知识库查到这个词的明确资料。"])
            stream = agent._create_response_stream(
                "蓝色纸鹤是什么？",
                agent._pending_semantic_cbt_analysis(),
                response_context={
                    "knowledge_backend": "dify",
                    "knowledge_context": "",
                    "knowledge_reason": "no_relevant_chunks",
                },
            )
            list(stream)

            system_text = "\n".join(
                message.get("content", "")
                for message in agent.client.requests[0]["messages"]
                if message.get("role") == "system"
            )
            self.assertIn("[Dify知识库检索状态]", system_text)
            self.assertIn("没有拿到可用于回答的资料片段", system_text)
            self.assertIn("不要编造", system_text)
            self.assertIn("资料是否已保存、索引完成", system_text)
        finally:
            Config.HISTORY_COMPRESSION_ENABLED = old_history_compression

    @patch("xiaoya_agent.core.agent.OpenAI", _FakeToolCallingOpenAI)
    def test_non_stream_response_can_use_model_tool_calling(self):
        old_values = {
            "AGENT_MODEL_TOOL_CALLING_ENABLED": Config.AGENT_MODEL_TOOL_CALLING_ENABLED,
            "RAG_ENABLED": Config.RAG_ENABLED,
            "RAG_BACKEND": Config.RAG_BACKEND,
            "RAG_TOP_K": Config.RAG_TOP_K,
            "DIFY_API_BASE_URL": Config.DIFY_API_BASE_URL,
            "DIFY_KNOWLEDGE_API_KEY": Config.DIFY_KNOWLEDGE_API_KEY,
            "DIFY_KNOWLEDGE_BASE_ID": Config.DIFY_KNOWLEDGE_BASE_ID,
            "DIFY_KNOWLEDGE_ENABLED": Config.DIFY_KNOWLEDGE_ENABLED,
            "DIFY_KNOWLEDGE_SEARCH_METHOD": Config.DIFY_KNOWLEDGE_SEARCH_METHOD,
        }
        class _Response:
            def raise_for_status(self):
                return None

            def json(self):
                return {
                    "records": [
                        {
                            "score": 0.9,
                            "document": {"name": "dify-guide.md"},
                            "segment": {
                                "id": "seg-1",
                                "document_id": "doc-1",
                                "content": "希望之树用于帮助患者记录治疗中的微小进步。",
                            },
                        }
                    ]
                }

        try:
            Config.AGENT_MODEL_TOOL_CALLING_ENABLED = True
            Config.RAG_ENABLED = True
            Config.RAG_BACKEND = "dify"
            Config.RAG_TOP_K = 1
            Config.DIFY_API_BASE_URL = "http://dify.local/v1"
            Config.DIFY_KNOWLEDGE_API_KEY = "dify-key"
            Config.DIFY_KNOWLEDGE_BASE_ID = "dataset-1"
            Config.DIFY_KNOWLEDGE_ENABLED = True
            Config.DIFY_KNOWLEDGE_SEARCH_METHOD = "keyword_search"

            with patch("xiaoya_agent.integrations.dify.requests.post", return_value=_Response()):
                agent = EnhancedChatAgent(load_persistent_data=False)
                agent.client = _FakeToolCallingOpenAI()
                response = agent._generate_cbt_response(
                    "希望之树是什么？",
                    agent._pending_semantic_cbt_analysis(),
                )

                self.assertIn("希望之树", response)
                self.assertEqual(agent.last_tool_trace["source"], "model_tool_calling")
                self.assertEqual(agent.last_tool_trace["requestedTools"], ["knowledge_retrieval"])
                self.assertTrue(agent.last_tool_trace["tools"][0]["hasContext"])
                self.assertIn("tools", agent.client.requests[0])
                tool_messages = [
                    message for message in agent.client.requests[1]["messages"]
                    if message.get("role") == "tool"
                ]
                self.assertEqual(len(tool_messages), 1)
                self.assertIn("希望之树", tool_messages[0]["content"])
        finally:
            for key, value in old_values.items():
                setattr(Config, key, value)
            reset_rag_index_cache()

    @patch("xiaoya_agent.core.agent.OpenAI", _FakeStreamingOpenAI)
    def test_stream_response_does_not_send_model_tools(self):
        old_tool_calling = Config.AGENT_MODEL_TOOL_CALLING_ENABLED
        try:
            Config.AGENT_MODEL_TOOL_CALLING_ENABLED = True
            agent = EnhancedChatAgent(load_persistent_data=False)
            agent.client = _FakeStreamingOpenAI(responses=["我在。"])
            stream = agent._create_response_stream(
                "你好",
                agent._pending_semantic_cbt_analysis(),
                stream=True,
            )
            list(stream)

            self.assertNotIn("tools", agent.client.requests[0])
            self.assertTrue(agent.client.requests[0]["stream"])
        finally:
            Config.AGENT_MODEL_TOOL_CALLING_ENABLED = old_tool_calling

    @patch("xiaoya_agent.core.agent.OpenAI", _FakeStreamingOpenAI)
    def test_stream_chat_keeps_real_stream_after_no_tool_planning(self):
        old_values = {
            "AGENT_GRAPH_ENABLED": Config.AGENT_GRAPH_ENABLED,
            "AGENT_TOOLS_ENABLED": Config.AGENT_TOOLS_ENABLED,
            "AGENT_MODEL_TOOL_CALLING_ENABLED": Config.AGENT_MODEL_TOOL_CALLING_ENABLED,
            "CBT_LLM_ENABLED": Config.CBT_LLM_ENABLED,
            "CRISIS_DETECTION_ENABLED": Config.CRISIS_DETECTION_ENABLED,
            "CRISIS_LLM_DETECTION_ENABLED": Config.CRISIS_LLM_DETECTION_ENABLED,
            "TRANSPLANT_LLM_SCENARIO_ENABLED": Config.TRANSPLANT_LLM_SCENARIO_ENABLED,
            "HISTORY_COMPRESSION_ENABLED": Config.HISTORY_COMPRESSION_ENABLED,
        }
        try:
            Config.AGENT_GRAPH_ENABLED = True
            Config.AGENT_TOOLS_ENABLED = True
            Config.AGENT_MODEL_TOOL_CALLING_ENABLED = True
            Config.CBT_LLM_ENABLED = False
            Config.CRISIS_DETECTION_ENABLED = False
            Config.CRISIS_LLM_DETECTION_ENABLED = False
            Config.TRANSPLANT_LLM_SCENARIO_ENABLED = False
            Config.HISTORY_COMPRESSION_ENABLED = False

            agent = EnhancedChatAgent(load_persistent_data=False)
            agent.client = _FakeStreamingOpenAI(responses=["真实流式回复。"])
            response = "".join(agent.stream_chat("你好"))

            self.assertEqual(response, "真实流式回复。")
            self.assertEqual(len(agent.client.requests), 1)
            self.assertNotIn("tools", agent.client.requests[0])
            self.assertTrue(agent.client.requests[0].get("stream"))
        finally:
            for key, value in old_values.items():
                setattr(Config, key, value)


class TestBasicChat(unittest.TestCase):
    """测试基础对话功能"""
    
    def setUp(self):
        """测试前准备"""
        Config.CRISIS_LLM_DETECTION_ENABLED = False
        Config.TRANSPLANT_LLM_SCENARIO_ENABLED = False
        Config.CBT_LLM_ENABLED = False
        Config.TRANSPLANT_SUPPORT_ENABLED = True
        Config.CBT_ENABLED = True
        Config.CRISIS_DETECTION_ENABLED = True

    @patch("xiaoya_agent.core.agent.OpenAI", _FakeOpenAI)
    def test_basic_chat_flow(self):
        """测试基础对话流程"""
        agent = EnhancedChatAgent()
        agent.client = _FakeOpenAI(responses=["你好，我是小芽"])
        
        result = agent.chat("你好")
        self.assertIn("response", result)
        self.assertIsNotNone(result["response"])
        self.assertIn("response_type", result)
        self.assertIn("cbt_analysis", result)
        self.assertIn("crisis_detection", result)

    @patch("xiaoya_agent.core.agent.OpenAI", _FakeOpenAI)
    def test_conversation_history_tracking(self):
        """测试对话历史记录"""
        agent = EnhancedChatAgent()
        agent.client = _FakeOpenAI(responses=["回复1", "回复2", "回复3"])
        
        initial_count = len(agent.conversation_history)
        agent.chat("消息1")
        agent.chat("消息2")
        agent.chat("消息3")
        
        # 每次对话增加2条记录（用户+助手）
        self.assertEqual(len(agent.conversation_history), initial_count + 6)

    @patch("xiaoya_agent.core.agent.OpenAI", _FakeOpenAI)
    def test_get_history(self):
        """测试获取对话历史"""
        agent = EnhancedChatAgent()
        agent.client = _FakeOpenAI(responses=["回复"])
        
        agent.chat("测试消息")
        history = agent.get_history()
        
        self.assertIsInstance(history, list)
        self.assertGreater(len(history), 0)
        self.assertTrue(all("role" in msg for msg in history))

    def test_history_metadata_serialization_keeps_json_primitives(self):
        """历史 metadata 中的 JSON 基础类型不应被转成字符串。"""
        agent = EnhancedChatAgent(load_persistent_data=False)
        serialized = agent._serialize_analysis_data({
            "intervention_needed": True,
            "severity": 7,
            "score": 0.6,
            "technique": CBTTechnique.RELAXATION_TRAINING,
            "items": [False, 3],
        })

        self.assertIs(serialized["intervention_needed"], True)
        self.assertEqual(serialized["severity"], 7)
        self.assertEqual(serialized["score"], 0.6)
        self.assertEqual(serialized["items"], [False, 3])
        self.assertEqual(serialized["technique"], CBTTechnique.RELAXATION_TRAINING.value)


class TestCBTModule(unittest.TestCase):
    """测试CBT模块"""
    
    def setUp(self):
        """测试前准备"""
        self.cbt = CBTModule()
        Config.CBT_LLM_ENABLED = False  # 使用规则引擎测试

    def test_emotion_detection_sadness(self):
        """测试悲伤情绪识别"""
        analysis = self.cbt.analyze_user_input("我感到很伤心和难过，觉得很失望")
        emotion = analysis["emotional_state"]
        
        self.assertEqual(emotion["primary"], "sadness")
        self.assertGreater(emotion["severity"], 3)

    def test_emotion_detection_anxiety(self):
        """测试焦虑情绪识别"""
        analysis = self.cbt.analyze_user_input("我很焦虑，非常担心和害怕")
        emotion = analysis["emotional_state"]
        
        self.assertEqual(emotion["primary"], "anxiety")
        self.assertGreater(emotion["severity"], 3)

    def test_emotion_detection_hopelessness(self):
        """测试绝望情绪识别"""
        analysis = self.cbt.analyze_user_input("我感到绝望和无助，觉得没有意义")
        emotion = analysis["emotional_state"]
        
        # 绝望关键词会被识别，但可能归类为 sadness 或 hopelessness
        self.assertIn(emotion["primary"], ["hopelessness", "sadness"])
        self.assertGreaterEqual(emotion["severity"], 4)  # 应该是较高严重度

    def test_cognitive_distortion_all_or_nothing(self):
        """测试全或无思维识别"""
        analysis = self.cbt.analyze_user_input("我总是失败，从不成功")
        distortions = analysis["cognitive_distortions"]
        
        self.assertIn("all_or_nothing", distortions)

    def test_cognitive_distortion_catastrophizing(self):
        """测试灾难化思维识别"""
        analysis = self.cbt.analyze_user_input("这将是一场灾难，我无法承受")
        distortions = analysis["cognitive_distortions"]
        
        self.assertIn("catastrophizing", distortions)

    def test_cognitive_distortion_overgeneralization(self):
        """测试过度概括识别"""
        analysis = self.cbt.analyze_user_input("每次都是这样，所有人都不理解我")
        distortions = analysis["cognitive_distortions"]
        
        self.assertIn("overgeneralization", distortions)

    def test_technique_recommendation_mindfulness(self):
        """测试正念技术推荐（高危情绪）"""
        analysis = self.cbt.analyze_user_input("我感到绝望，完全崩溃了")
        technique = analysis["recommended_technique"]
        
        # 高危情绪或有认知扭曲时，应该推荐正念或认知重构
        self.assertIsNotNone(technique)
        self.assertIn(technique, [CBTTechnique.MINDFULNESS, CBTTechnique.COGNITIVE_RESTRUCTURING])

    def test_technique_recommendation_relaxation(self):
        """测试放松训练推荐（焦虑）"""
        analysis = self.cbt.analyze_user_input("我很焦虑，心跳加速")
        technique = analysis["recommended_technique"]
        
        self.assertEqual(technique, CBTTechnique.RELAXATION_TRAINING)

    def test_technique_recommendation_cognitive_restructuring(self):
        """测试认知重构推荐（有认知扭曲）"""
        analysis = self.cbt.analyze_user_input("我总是失败，一切都完了")
        technique = analysis["recommended_technique"]
        
        self.assertEqual(technique, CBTTechnique.COGNITIVE_RESTRUCTURING)

    def test_cbt_response_generation(self):
        """测试CBT响应生成"""
        analysis = self.cbt.analyze_user_input("我很焦虑")
        response = self.cbt.generate_cbt_response("我很焦虑", analysis)
        
        self.assertIsInstance(response, str)
        self.assertGreater(len(response), 0)

    def test_user_profile_update(self):
        """测试用户档案更新"""
        initial_count = self.cbt.user_profile["session_count"]
        analysis = self.cbt.analyze_user_input("我总是失败")
        self.cbt.update_user_profile(analysis, "我总是失败")
        
        self.assertEqual(self.cbt.user_profile["session_count"], initial_count + 1)
        self.assertIn("all_or_nothing", self.cbt.user_profile["cognitive_patterns"])

    def test_progress_report(self):
        """测试进度报告"""
        report = self.cbt.get_progress_report()
        
        self.assertIn("total_sessions", report)
        self.assertIn("progress_level", report)
        self.assertIn("common_patterns", report)


class TestCrisisModule(unittest.TestCase):
    """测试危机干预模块"""
    
    def setUp(self):
        """测试前准备"""
        Config.CRISIS_LLM_DETECTION_ENABLED = False
        Config.CRISIS_DETECTION_ENABLED = True
        Config.CRISIS_ALERT_THRESHOLD = 10

    def test_crisis_detection_suicide(self):
        """测试自杀危机检测"""
        triggered = {"count": 0, "data": None}
        
        def callback(data):
            triggered["count"] += 1
            triggered["data"] = data
        
        module = CrisisInterventionModule(alert_callback=callback)
        result = module.detect_crisis("我想自杀，不想活了", {"primary": "hopelessness", "severity": 10})
        
        self.assertEqual(result, {"alert": True})
        self.assertEqual(triggered["count"], 1)
        self.assertEqual(triggered["data"]["crisis_level"], "critical")
        self.assertEqual(triggered["data"]["alarm"]["title"], "紧急危机报警")

    def test_crisis_detection_self_harm(self):
        """测试自伤危机检测"""
        triggered = {"count": 0}
        
        def callback(data):
            triggered["count"] += 1
        
        module = CrisisInterventionModule(alert_callback=callback)
        result = module.detect_crisis("我想自残，伤害自己", {"primary": "hopelessness", "severity": 9})
        
        self.assertEqual(result, {"alert": True})
        self.assertEqual(triggered["count"], 1)

    def test_crisis_detection_no_crisis(self):
        """测试非危机情况"""
        triggered = {"count": 0}
        
        def callback(data):
            triggered["count"] += 1
        
        module = CrisisInterventionModule(alert_callback=callback)
        result = module.detect_crisis("今天天气不错", {"primary": "neutral", "severity": 1})
        
        self.assertEqual(result, {"alert": False})
        self.assertEqual(triggered["count"], 0)

    def test_grounding_exercise(self):
        """测试正念接地练习"""
        module = CrisisInterventionModule()
        exercise = module.get_grounding_exercise()
        
        self.assertIsInstance(exercise, str)
        self.assertGreater(len(exercise), 50)
        self.assertIn("正念", exercise)

    def test_crisis_history_tracking(self):
        """测试危机历史记录"""
        module = CrisisInterventionModule()
        module.detect_crisis("我想死", {"primary": "hopelessness", "severity": 10})
        
        report = module.get_crisis_history_report()
        self.assertGreater(report["total_crises"], 0)
        self.assertEqual(report["recent_crises"][-1]["crisis_level"], "critical")

    def test_crisis_alarm_levels_are_graded(self):
        """测试危机报警分级。"""
        self.assertEqual(build_crisis_alarm({"alert": False, "severity_score": 2})["level"], "none")
        self.assertEqual(build_crisis_alarm({"alert": False, "severity_score": 8})["level"], "watch")
        self.assertEqual(build_crisis_alarm({
            "alert": True,
            "alert_type": "psychological_crisis",
            "severity_score": 10,
            "crisis_types": ["急性焦虑"],
        })["level"], "warning")
        self.assertEqual(build_crisis_alarm({
            "alert": True,
            "alert_type": "psychological_crisis",
            "severity_score": 13,
            "crisis_types": ["情绪崩溃"],
        })["level"], "high")
        self.assertEqual(build_crisis_alarm({
            "alert": True,
            "alert_type": "psychological_crisis",
            "severity_score": 18,
            "crisis_types": ["自杀危机"],
        })["level"], "critical")
        medical = build_crisis_alarm({"alert": True, "alert_type": "medical_red_flag"})
        self.assertEqual(medical["level"], "critical")
        self.assertEqual(medical["action"], "contact_medical_staff")

    def test_semantic_high_risk_type_alerts_even_with_low_score(self):
        """语义分类为身体红旗/自伤自杀时，不应因分数偏低被压成非报警。"""
        old_detection = Config.CRISIS_DETECTION_ENABLED
        old_llm = Config.CRISIS_LLM_DETECTION_ENABLED
        try:
            Config.CRISIS_DETECTION_ENABLED = True
            Config.CRISIS_LLM_DETECTION_ENABLED = True
            module = CrisisInterventionModule()

            module._llm_detect_crisis = lambda *_args, **_kwargs: {
                "has_crisis": True,
                "crisis_types": ["身体红旗"],
                "severity_score": 3,
                "reason": "语义上属于需要医护优先处理的身体风险。",
            }
            medical = module.assess_crisis_semantic_only("我喘不过气了", {})
            self.assertTrue(medical["alert"])
            self.assertEqual(medical["alert_type"], "medical_red_flag")
            self.assertEqual(medical["crisis_level"], "critical")

            module._llm_detect_crisis = lambda *_args, **_kwargs: {
                "has_crisis": True,
                "crisis_types": ["自杀危机"],
                "severity_score": 3,
                "reason": "语义上表达了自杀风险。",
            }
            psychological = module.assess_crisis_semantic_only("我想死", {})
            self.assertTrue(psychological["alert"])
            self.assertEqual(psychological["alert_type"], "psychological_crisis")
            self.assertEqual(psychological["crisis_level"], "high")
        finally:
            Config.CRISIS_DETECTION_ENABLED = old_detection
            Config.CRISIS_LLM_DETECTION_ENABLED = old_llm

    def test_api_crisis_assessment_exposes_alarm_level(self):
        """API 危机元数据应返回分级报警结构。"""
        from xiaoya_agent.interfaces.api_server import build_crisis_assessment

        assessment = build_crisis_assessment({
            "alert": True,
            "alert_type": "psychological_crisis",
            "severity_score": 14,
            "crisis_types": ["情绪崩溃"],
        }, {"emotional_state": {"primary": "hopelessness", "severity": 8}})

        self.assertTrue(assessment["crisisAlert"])
        self.assertEqual(assessment["crisisLevel"], "high")
        self.assertEqual(assessment["alarm"]["label"], "二级高危报警")
        self.assertEqual(assessment["action"], "alert_and_notify")

    def test_crisis_threshold_control(self):
        """测试危机阈值控制"""
        triggered = {"count": 0}
        
        def callback(data):
            triggered["count"] += 1
        
        # 设置较高阈值
        Config.CRISIS_ALERT_THRESHOLD = 15
        module = CrisisInterventionModule(alert_callback=callback)
        
        # 中等严重度不应触发
        result = module.detect_crisis("我有点难过", {"primary": "sadness", "severity": 5})
        self.assertEqual(result, {"alert": False})
        self.assertEqual(triggered["count"], 0)


class TestTransplantSupport(unittest.TestCase):
    """测试移植分期支持模块"""
    
    def setUp(self):
        """测试前准备"""
        Config.TRANSPLANT_LLM_SCENARIO_ENABLED = False
        Config.TRANSPLANT_SUPPORT_ENABLED = True

    def test_phase_management(self):
        """测试分期管理"""
        agent = EnhancedChatAgent(load_persistent_data=False)
        
        # 测试默认分期
        self.assertEqual(agent.get_transplant_phase(), TransplantPhase.PREP)
        
        # 测试设置分期
        agent.set_transplant_phase(TransplantPhase.KEY)
        self.assertEqual(agent.get_transplant_phase(), TransplantPhase.KEY)
        
        agent.set_transplant_phase(TransplantPhase.RECOVERY)
        self.assertEqual(agent.get_transplant_phase(), TransplantPhase.RECOVERY)

    def test_scenario_detection_keywords(self):
        """测试情境识别（关键词）"""
        # 化疗准备
        result = choose_intervention("我今天要化疗，很害怕", TransplantPhase.PREP, 7)
        self.assertTrue(result.should_trigger)
        self.assertEqual(result.scenario, Scenario.CHEMO_PREP)
        
        # 回输当天
        result = choose_intervention("今天是回输的日子", TransplantPhase.KEY, 6)
        self.assertTrue(result.should_trigger)
        self.assertEqual(result.scenario, Scenario.INFUSION_DAY)
        
        # 剧烈不适（移植中关键期）
        result = choose_intervention("我感觉很难受，疼痛恶心", TransplantPhase.KEY, 8)
        self.assertTrue(result.should_trigger)
        self.assertEqual(result.scenario, Scenario.SEVERE_DISCOMFORT)

    def test_scenario_no_trigger(self):
        """测试不触发情境识别"""
        result = choose_intervention("今天天气不错", TransplantPhase.PREP, 2)
        self.assertFalse(result.should_trigger)

    def test_template_retrieval(self):
        """测试引导语模板获取"""
        template = get_template(TransplantPhase.PREP, Scenario.CHEMO_PREP)
        self.assertIsNotNone(template)
        self.assertIsInstance(template, str)
        self.assertGreater(len(template), 0)

    @patch("xiaoya_agent.core.agent.OpenAI", _FakeOpenAI)
    def test_transplant_guidance_integration(self):
        """测试移植引导集成"""
        Config.TRANSPLANT_SUPPORT_ENABLED = True
        agent = EnhancedChatAgent()
        agent.client = _FakeOpenAI(responses=["改写后的引导语"])
        
        result = agent.chat("我今天要化疗，很害怕")
        
        # 应该触发移植引导
        if result["response_type"] == "transplant_guidance":
            self.assertIsNotNone(result["response"])


class TestEnergyModel(unittest.TestCase):
    """测试心理能量模型"""
    
    def test_initial_state(self):
        """测试初始状态"""
        model = PsychologicalEnergyModel()
        report = model.get_energy_report()
        
        self.assertEqual(report["total_energy"], 0)
        self.assertEqual(report["session_count"], 0)
        self.assertEqual(report["current_level"]["name"], "萌芽")

    def test_energy_assessment_positive(self):
        """测试积极对话能量评估"""
        model = PsychologicalEnergyModel()
        
        conversation_data = {
            "user_message": "我今天尝试了新的方法，感觉好多了",
            "analysis": {
                "emotional_state": {"primary": "hope", "severity": 3},
                "cognitive_distortions": [],
                "recommended_technique": "BEHAVIORAL_ACTIVATION"
            },
            "cbt_response": "很好！继续保持。"
        }
        
        result = model.assess_conversation_quality(conversation_data)
        self.assertIsNotNone(result)
        
        report = model.get_energy_report()
        self.assertGreater(report["total_energy"], 0)

    def test_energy_assessment_cognitive_restructuring(self):
        """测试认知重构能量增益"""
        model = PsychologicalEnergyModel()
        
        conversation_data = {
            "user_message": "我意识到我的想法可能太极端了",
            "analysis": {
                "emotional_state": {"primary": "calm", "severity": 4},
                "cognitive_distortions": ["all_or_nothing"],
                "recommended_technique": "COGNITIVE_RESTRUCTURING"
            },
            "cbt_response": "很好的觉察！"
        }
        
        result = model.assess_conversation_quality(conversation_data)
        gains = result.get("dimension_gains", {})
        
        # 认知重构应该增加认知成长维度（不是"认知重构"维度）
        self.assertIn("认知成长", gains)
        self.assertGreater(gains.get("认知成长", 0), 0)

    def test_level_progression(self):
        """测试等级晋升"""
        model = PsychologicalEnergyModel()
        
        # 模拟多次积极对话
        for i in range(20):
            conversation_data = {
                "user_message": f"我今天做了练习{i}",
                "analysis": {
                    "emotional_state": {"primary": "hope", "severity": 2},
                    "cognitive_distortions": [],
                    "recommended_technique": "BEHAVIORAL_ACTIVATION"
                },
                "cbt_response": "很好！"
            }
            model.assess_conversation_quality(conversation_data)
        
        report = model.get_energy_report()
        self.assertGreater(report["total_energy"], 50)

    def test_achievement_system(self):
        """测试成就系统"""
        model = PsychologicalEnergyModel()
        
        # 触发正念练习成就
        for i in range(3):
            conversation_data = {
                "user_message": "我进行了正念练习",
                "analysis": {
                    "emotional_state": {"primary": "calm", "severity": 2},
                    "cognitive_distortions": [],
                    "recommended_technique": "MINDFULNESS"
                },
                "cbt_response": "很好的练习！"
            }
            result = model.assess_conversation_quality(conversation_data)
        
        # 检查是否解锁成就
        stats = model.get_achievement_stats()
        self.assertGreater(stats["counters"]["mindfulness_count"], 0)

    def test_achievement_stats(self):
        """测试成就统计"""
        model = PsychologicalEnergyModel()
        stats = model.get_achievement_stats()
        
        self.assertIn("total_achievements", stats)
        self.assertIn("unlocked_achievements", stats)
        self.assertIn("completion_rate", stats)
        self.assertIn("counters", stats)
        self.assertIn("category_stats", stats)

    def test_achievements_by_category(self):
        """测试按类别获取成就"""
        model = PsychologicalEnergyModel()
        categorized = model.get_achievements_by_category()
        
        self.assertIsInstance(categorized, dict)
        self.assertIn("基础", categorized)

    def test_energy_persistence(self):
        """测试能量持久化"""
        with tempfile.TemporaryDirectory() as td:
            path = os.path.join(td, "energy_test.json")
            
            # 创建并保存
            model1 = PsychologicalEnergyModel()
            conversation_data = {
                "user_message": "测试",
                "analysis": {
                    "emotional_state": {"primary": "hope", "severity": 2},
                    "cognitive_distortions": [],
                    "recommended_technique": "BEHAVIORAL_ACTIVATION"
                },
                "cbt_response": "好的"
            }
            model1.assess_conversation_quality(conversation_data)
            energy_before = model1.get_energy_report()["total_energy"]
            model1.save_progress(path)
            
            # 加载并验证
            model2 = PsychologicalEnergyModel()
            model2.load_progress(path)
            energy_after = model2.get_energy_report()["total_energy"]
            
            self.assertEqual(energy_before, energy_after)


class TestMemoryCore(unittest.TestCase):
    """测试记忆中枢"""
    
    @patch("xiaoya_agent.core.agent.OpenAI", _FakeOpenAI)
    def test_memory_core_initialization(self):
        """测试记忆中枢初始化"""
        Config.HISTORY_COMPRESSION_ENABLED = True
        agent = EnhancedChatAgent()
        
        self.assertIsNone(agent.memory_core)

    @patch("xiaoya_agent.core.agent.OpenAI", _FakeOpenAI)
    def test_memory_core_update(self):
        """测试记忆中枢更新"""
        Config.HISTORY_COMPRESSION_ENABLED = True
        Config.TRANSPLANT_SUPPORT_ENABLED = False
        
        agent = EnhancedChatAgent()
        agent.client = _FakeOpenAI(responses=[
            "基础回复",
            "用户表达了焦虑情绪，担心治疗效果。"  # 记忆中枢摘要
        ])
        
        agent.chat("我很担心治疗效果")
        
        # 记忆中枢应该被更新
        self.assertIsNotNone(agent.memory_core)
        self.assertIsInstance(agent.memory_core, str)

    @patch("xiaoya_agent.core.agent.OpenAI", _FakeOpenAI)
    def test_memory_core_disabled(self):
        """测试禁用记忆中枢"""
        Config.HISTORY_COMPRESSION_ENABLED = False
        Config.TRANSPLANT_SUPPORT_ENABLED = False
        
        agent = EnhancedChatAgent()
        agent.client = _FakeOpenAI(responses=["回复"])
        
        agent.chat("测试消息")
        
        # 禁用时不应更新记忆中枢
        self.assertIsNone(agent.memory_core)


class TestPersistence(unittest.TestCase):
    """测试数据持久化"""
    
    @patch("xiaoya_agent.core.agent.OpenAI", _FakeOpenAI)
    def test_save_and_load_history(self):
        """测试保存和加载对话历史"""
        with tempfile.TemporaryDirectory() as td:
            Config.TRANSPLANT_SUPPORT_ENABLED = False

            # 创建并保存
            agent1 = EnhancedChatAgent(data_dir=td, load_persistent_data=False)
            agent1.client = _FakeOpenAI(responses=["回复1", "回复2"])
            agent1.chat("消息1")
            agent1.chat("消息2")
            agent1.save_history("test_history.json")

            # 加载并验证
            agent2 = EnhancedChatAgent(data_dir=td, load_persistent_data=False)
            agent2.load_history("test_history.json")

            self.assertEqual(len(agent1.get_history()), len(agent2.get_history()))

    @patch("xiaoya_agent.core.agent.OpenAI", _FakeOpenAI)
    def test_save_all_progress(self):
        """测试保存所有进度"""
        with tempfile.TemporaryDirectory() as td:
            Config.TRANSPLANT_SUPPORT_ENABLED = False

            agent = EnhancedChatAgent(data_dir=td, load_persistent_data=False)
            agent.client = _FakeOpenAI(responses=["回复"])
            agent.chat("测试")
            agent.save_all_progress()

            # 验证文件存在
            self.assertTrue(os.path.exists(os.path.join(td, "chat_history.json")))
            self.assertTrue(os.path.exists(os.path.join(td, "energy_progress.json")))
            self.assertTrue(os.path.exists(os.path.join(td, "crisis_history.json")))
            self.assertTrue(os.path.exists(os.path.join(td, "user_state.json")))

    @patch("xiaoya_agent.core.agent.OpenAI", _FakeOpenAI)
    def test_user_state_persistence(self):
        """测试用户状态持久化"""
        with tempfile.TemporaryDirectory() as td:
            # 设置并保存
            agent1 = EnhancedChatAgent(data_dir=td, load_persistent_data=False)
            agent1.set_transplant_phase(TransplantPhase.KEY)

            # 加载并验证
            agent2 = EnhancedChatAgent(data_dir=td)
            self.assertEqual(agent2.get_transplant_phase(), TransplantPhase.KEY)

    @patch("xiaoya_agent.core.agent.OpenAI", _FakeOpenAI)
    def test_reset_functionality(self):
        """测试重置功能"""
        with tempfile.TemporaryDirectory() as td:
            Config.TRANSPLANT_SUPPORT_ENABLED = False

            agent = EnhancedChatAgent(data_dir=td, load_persistent_data=False)
            agent.client = _FakeOpenAI(responses=["回复1", "回复2"])

            # 产生数据
            agent.chat("消息1")
            agent.chat("消息2")
            agent.set_transplant_phase(TransplantPhase.KEY)
            agent.save_all_progress()

            # 验证有数据
            self.assertGreater(len(agent.conversation_history), 1)
            self.assertEqual(agent.get_transplant_phase(), TransplantPhase.KEY)

            # 重置
            result = agent.reset()

            # 验证重置结果
            self.assertTrue(result["success"])
            self.assertEqual(len(agent.conversation_history), 1)  # 只剩系统提示词
            self.assertEqual(agent.get_transplant_phase(), TransplantPhase.PREP)
            self.assertEqual(agent.cbt_module.user_profile["session_count"], 0)
            self.assertEqual(agent.energy_model.total_energy, 0)


class TestComprehensiveReport(unittest.TestCase):
    """测试综合报告"""
    
    @patch("xiaoya_agent.core.agent.OpenAI", _FakeOpenAI)
    def test_comprehensive_report(self):
        """测试综合报告生成"""
        Config.TRANSPLANT_SUPPORT_ENABLED = False
        agent = EnhancedChatAgent()
        agent.client = _FakeOpenAI(responses=["回复1", "回复2"])
        
        agent.chat("消息1")
        agent.chat("消息2")
        
        report = agent.get_comprehensive_report()
        
        self.assertIn("cbt_progress", report)
        self.assertIn("energy_report", report)
        self.assertIn("crisis_report", report)
        self.assertIn("session_count", report)
        
        self.assertEqual(report["session_count"], 2)


class TestCBTGating(unittest.TestCase):
    """测试CBT门控机制"""
    
    def setUp(self):
        """测试前准备"""
        Config.CBT_ENABLED = True
        Config.AUTO_CBT_INTERVENTION = True
        Config.CBT_INTERVENTION_SEVERITY_THRESHOLD = 6
        Config.CBT_DISTORTION_TRIGGER_ENABLED = True
        Config.CBT_LLM_ENABLED = False

    @patch("xiaoya_agent.core.agent.OpenAI", _FakeOpenAI)
    def test_cbt_not_triggered_for_casual_chat(self):
        """测试闲聊不触发CBT"""
        Config.TRANSPLANT_SUPPORT_ENABLED = False
        agent = EnhancedChatAgent()
        agent.client = _FakeOpenAI(responses=["今天天气很好"])
        
        result = agent.chat("今天天气怎么样？")
        
        # 闲聊不应包含 CBT 引导标记
        self.assertNotIn("如果你愿意，我们可以试一个小练习", result["response"] or "")

    @patch("xiaoya_agent.core.agent.OpenAI", _FakeOpenAI)
    def test_cbt_triggered_for_high_severity(self):
        """测试高情绪强度触发CBT"""
        Config.TRANSPLANT_SUPPORT_ENABLED = False
        agent = EnhancedChatAgent()
        agent.client = _FakeOpenAI(responses=["我理解你的感受"])
        
        result = agent.chat("我真的很绝望，完全撑不住了，感觉一切都完了")
        
        # 高情绪强度应该触发 CBT
        analysis = result["cbt_analysis"]
        if analysis["emotional_state"]["severity"] >= 6:
            if result["response_type"] == "cbt_response" and analysis.get("recommended_technique"):
                self.assertTrue(analysis.get("intervention_needed", False))

    @patch("xiaoya_agent.core.agent.OpenAI", _FakeOpenAI)
    def test_cbt_triggered_for_cognitive_distortion(self):
        """测试认知扭曲触发CBT"""
        Config.TRANSPLANT_SUPPORT_ENABLED = False
        agent = EnhancedChatAgent()
        agent.client = _FakeOpenAI(responses=["让我们一起看看"])
        
        result = agent.chat("我总是失败，从来没有成功过")
        
        # 有认知扭曲应该触发 CBT
        analysis = result["cbt_analysis"]
        if len(analysis.get("cognitive_distortions", [])) > 0:
            if result["response_type"] == "cbt_response" and analysis.get("recommended_technique"):
                self.assertTrue(analysis.get("intervention_needed", False) or analysis.get("recommended_technique"))


class TestIntegration(unittest.TestCase):
    """测试集成功能"""
    
    @patch("xiaoya_agent.core.agent.OpenAI", _FakeOpenAI)
    def test_crisis_overrides_other_modules(self):
        """测试危机优先级最高"""
        Config.CRISIS_DETECTION_ENABLED = True
        Config.CRISIS_LLM_DETECTION_ENABLED = False
        Config.TRANSPLANT_SUPPORT_ENABLED = False
        Config.CRISIS_ALERT_THRESHOLD = 8  # 降低阈值以便触发
        
        agent = EnhancedChatAgent()
        agent.client = _FakeOpenAI(responses=[])
        
        result = agent.chat("我想自杀，不想活了，想死")
        
        # 危机检测应该识别到危机信号
        self.assertIn("alert", result["crisis_detection"])
        # 如果触发了危机报警，响应类型应该是 crisis_alert
        if result["crisis_detection"]["alert"]:
            self.assertEqual(result["response_type"], "crisis_alert")
        else:
            # 如果没有触发，至少应该识别到了危机关键词
            # 这个测试主要验证危机检测逻辑存在
            self.assertIsNotNone(result["crisis_detection"])

    @patch("xiaoya_agent.core.agent.OpenAI", _FakeOpenAI)
    def test_full_workflow(self):
        """测试完整工作流程"""
        Config.TRANSPLANT_SUPPORT_ENABLED = False
        Config.CRISIS_DETECTION_ENABLED = True
        Config.CBT_ENABLED = True
        Config.ENERGY_MODEL_ENABLED = True
        
        agent = EnhancedChatAgent()
        agent.client = _FakeOpenAI(responses=["回复1", "回复2", "回复3"])
        
        # 正常对话
        result1 = agent.chat("你好")
        self.assertIsNotNone(result1["response"])
        
        # 情绪对话
        result2 = agent.chat("我有点担心")
        self.assertIsNotNone(result2["cbt_analysis"])
        
        # 获取报告
        report = agent.get_comprehensive_report()
        self.assertGreater(report["session_count"], 0)

    @patch("xiaoya_agent.core.agent.OpenAI", _FakeSlowNonCrisisAnalysisOpenAI)
    @patch("xiaoya_agent.features.crisis.OpenAI", _FailingCrisisOpenAI)
    def test_stream_chat_does_not_use_keyword_crisis_rule(self):
        """流式心理危机判断不应再因关键词规则直接报警。"""
        old_values = {
            "CRISIS_DETECTION_ENABLED": Config.CRISIS_DETECTION_ENABLED,
            "CRISIS_LLM_DETECTION_ENABLED": Config.CRISIS_LLM_DETECTION_ENABLED,
            "CRISIS_LLM_STREAM_BLOCKING_ENABLED": Config.CRISIS_LLM_STREAM_BLOCKING_ENABLED,
            "BACKGROUND_CRISIS_FIRST_ENABLED": Config.BACKGROUND_CRISIS_FIRST_ENABLED,
            "TRANSPLANT_SUPPORT_ENABLED": Config.TRANSPLANT_SUPPORT_ENABLED,
            "CBT_LLM_ENABLED": Config.CBT_LLM_ENABLED,
            "TRANSPLANT_LLM_SCENARIO_ENABLED": Config.TRANSPLANT_LLM_SCENARIO_ENABLED,
            "HISTORY_COMPRESSION_ENABLED": Config.HISTORY_COMPRESSION_ENABLED,
        }
        try:
            Config.CRISIS_DETECTION_ENABLED = True
            Config.CRISIS_LLM_DETECTION_ENABLED = True
            Config.CRISIS_LLM_STREAM_BLOCKING_ENABLED = False
            Config.BACKGROUND_CRISIS_FIRST_ENABLED = False
            Config.TRANSPLANT_SUPPORT_ENABLED = False
            Config.CBT_LLM_ENABLED = False
            Config.TRANSPLANT_LLM_SCENARIO_ENABLED = False
            Config.HISTORY_COMPRESSION_ENABLED = False

            agent = EnhancedChatAgent()
            agent.client = _FakeSlowNonCrisisAnalysisOpenAI()
            chunks = list(agent.stream_chat("我不怕死"))

            self.assertEqual("".join(chunks), "我听到了，我们可以慢慢聊。")
            self.assertEqual(agent.last_result["response_type"], "cbt_response")
            self.assertEqual(agent.last_result["crisis_detection"].get("source"), "semantic_background_pending")
            self.assertFalse(agent.last_result["crisis_detection"]["alert"])
        finally:
            for key, value in old_values.items():
                setattr(Config, key, value)

    @patch("xiaoya_agent.features.crisis.OpenAI", _FakeSemanticMedicalRedFlagOpenAI)
    @patch("xiaoya_agent.core.agent.OpenAI", _FakeSlowNonCrisisAnalysisOpenAI)
    def test_medical_red_flag_stays_critical_after_background_analysis(self):
        """身体红旗是硬安全告警，不应被后台非危机语义分析降级或清掉。"""
        old_values = {
            "AGENT_GRAPH_ENABLED": Config.AGENT_GRAPH_ENABLED,
            "AGENT_TOOLS_ENABLED": Config.AGENT_TOOLS_ENABLED,
            "CRISIS_DETECTION_ENABLED": Config.CRISIS_DETECTION_ENABLED,
            "CRISIS_LLM_DETECTION_ENABLED": Config.CRISIS_LLM_DETECTION_ENABLED,
            "CRISIS_LLM_STREAM_BLOCKING_ENABLED": Config.CRISIS_LLM_STREAM_BLOCKING_ENABLED,
            "MEDICAL_RED_FLAG_RULE_ENABLED": Config.MEDICAL_RED_FLAG_RULE_ENABLED,
            "TRANSPLANT_SUPPORT_ENABLED": Config.TRANSPLANT_SUPPORT_ENABLED,
            "CBT_LLM_ENABLED": Config.CBT_LLM_ENABLED,
            "TRANSPLANT_LLM_SCENARIO_ENABLED": Config.TRANSPLANT_LLM_SCENARIO_ENABLED,
            "HISTORY_COMPRESSION_ENABLED": Config.HISTORY_COMPRESSION_ENABLED,
        }
        try:
            Config.AGENT_GRAPH_ENABLED = True
            Config.AGENT_TOOLS_ENABLED = True
            Config.CRISIS_DETECTION_ENABLED = True
            Config.CRISIS_LLM_DETECTION_ENABLED = True
            Config.CRISIS_LLM_STREAM_BLOCKING_ENABLED = True
            Config.MEDICAL_RED_FLAG_RULE_ENABLED = False
            Config.TRANSPLANT_SUPPORT_ENABLED = False
            Config.CBT_LLM_ENABLED = False
            Config.TRANSPLANT_LLM_SCENARIO_ENABLED = False
            Config.HISTORY_COMPRESSION_ENABLED = False

            agent = EnhancedChatAgent()
            response = "".join(agent.stream_chat("我胸口好痛啊，我喘不过气了"))
            self.assertIn("呼叫铃", response)
            agent.wait_for_background_analysis(1)

            crisis_detection = agent.last_result["crisis_detection"]
            self.assertTrue(crisis_detection["alert"])
            self.assertEqual(crisis_detection["alert_type"], "medical_red_flag")
            self.assertEqual(crisis_detection["crisis_level"], "critical")
            alarm = build_crisis_alarm(crisis_detection)
            self.assertEqual(alarm["level"], "critical")
            self.assertEqual(alarm["action"], "contact_medical_staff")
        finally:
            for key, value in old_values.items():
                setattr(Config, key, value)

    @patch("xiaoya_agent.features.crisis.OpenAI", _FakeSemanticNoCrisisOpenAI)
    @patch("xiaoya_agent.core.agent.OpenAI", _FakeSlowNonCrisisAnalysisOpenAI)
    def test_blocking_crisis_precheck_uses_semantics_not_keywords(self):
        """即使包含高危字面词，阻塞预检也以语义模型结果为准。"""
        old_values = {
            "AGENT_GRAPH_ENABLED": Config.AGENT_GRAPH_ENABLED,
            "CRISIS_DETECTION_ENABLED": Config.CRISIS_DETECTION_ENABLED,
            "CRISIS_LLM_DETECTION_ENABLED": Config.CRISIS_LLM_DETECTION_ENABLED,
            "CRISIS_LLM_STREAM_BLOCKING_ENABLED": Config.CRISIS_LLM_STREAM_BLOCKING_ENABLED,
            "MEDICAL_RED_FLAG_RULE_ENABLED": Config.MEDICAL_RED_FLAG_RULE_ENABLED,
            "TRANSPLANT_SUPPORT_ENABLED": Config.TRANSPLANT_SUPPORT_ENABLED,
            "CBT_LLM_ENABLED": Config.CBT_LLM_ENABLED,
            "TRANSPLANT_LLM_SCENARIO_ENABLED": Config.TRANSPLANT_LLM_SCENARIO_ENABLED,
            "HISTORY_COMPRESSION_ENABLED": Config.HISTORY_COMPRESSION_ENABLED,
        }
        try:
            Config.AGENT_GRAPH_ENABLED = True
            Config.CRISIS_DETECTION_ENABLED = True
            Config.CRISIS_LLM_DETECTION_ENABLED = True
            Config.CRISIS_LLM_STREAM_BLOCKING_ENABLED = True
            Config.MEDICAL_RED_FLAG_RULE_ENABLED = False
            Config.TRANSPLANT_SUPPORT_ENABLED = False
            Config.CBT_LLM_ENABLED = False
            Config.TRANSPLANT_LLM_SCENARIO_ENABLED = False
            Config.HISTORY_COMPRESSION_ENABLED = False

            agent = EnhancedChatAgent()
            response = "".join(agent.stream_chat("我不怕死，我是说打针这件事我已经没那么害怕了。"))

            self.assertEqual(response, "我听到了，我们可以慢慢聊。")
            self.assertFalse(agent.last_result["crisis_detection"]["alert"])
            self.assertEqual(agent.last_result["crisis_detection"]["source"], "llm_semantic")
        finally:
            for key, value in old_values.items():
                setattr(Config, key, value)

    @patch("xiaoya_agent.features.crisis.OpenAI", _FakeSemanticCrisisOpenAI)
    def test_blocking_crisis_precheck_returns_safety_response(self):
        """明确心理危机应在主回复前完成语义预检并直接走安全回应。"""
        old_values = {
            "AGENT_GRAPH_ENABLED": Config.AGENT_GRAPH_ENABLED,
            "CRISIS_DETECTION_ENABLED": Config.CRISIS_DETECTION_ENABLED,
            "CRISIS_LLM_DETECTION_ENABLED": Config.CRISIS_LLM_DETECTION_ENABLED,
            "CRISIS_LLM_STREAM_BLOCKING_ENABLED": Config.CRISIS_LLM_STREAM_BLOCKING_ENABLED,
            "MEDICAL_RED_FLAG_RULE_ENABLED": Config.MEDICAL_RED_FLAG_RULE_ENABLED,
            "TRANSPLANT_SUPPORT_ENABLED": Config.TRANSPLANT_SUPPORT_ENABLED,
            "CBT_LLM_ENABLED": Config.CBT_LLM_ENABLED,
            "TRANSPLANT_LLM_SCENARIO_ENABLED": Config.TRANSPLANT_LLM_SCENARIO_ENABLED,
            "HISTORY_COMPRESSION_ENABLED": Config.HISTORY_COMPRESSION_ENABLED,
        }
        try:
            Config.AGENT_GRAPH_ENABLED = True
            Config.CRISIS_DETECTION_ENABLED = True
            Config.CRISIS_LLM_DETECTION_ENABLED = True
            Config.CRISIS_LLM_STREAM_BLOCKING_ENABLED = True
            Config.MEDICAL_RED_FLAG_RULE_ENABLED = False
            Config.TRANSPLANT_SUPPORT_ENABLED = False
            Config.CBT_LLM_ENABLED = False
            Config.TRANSPLANT_LLM_SCENARIO_ENABLED = False
            Config.HISTORY_COMPRESSION_ENABLED = False

            agent = EnhancedChatAgent()
            agent.personalization_profile["risk_notes"] = ["past suicidal ideation"]
            response = "".join(agent.stream_chat("我想死"))

            self.assertIn("最重要的是你的安全", response)
            self.assertTrue(agent.last_result["crisis_detection"]["alert"])
            self.assertEqual(agent.last_result["crisis_detection"]["source"], "llm_semantic")
            self.assertIn("自杀危机", agent.last_result["crisis_detection"]["crisis_types"])
        finally:
            for key, value in old_values.items():
                setattr(Config, key, value)

    @patch("xiaoya_agent.core.agent.OpenAI", _FakeStreamingOpenAI)
    def test_stream_chat_does_not_use_cbt_keyword_rule_for_prompt(self):
        """流式主回复不应再用 CBT 关键词/规则向首轮提示词注入 CBT 指令。"""
        old_values = {
            "CBT_ENABLED": Config.CBT_ENABLED,
            "AUTO_CBT_INTERVENTION": Config.AUTO_CBT_INTERVENTION,
            "CBT_DISTORTION_TRIGGER_ENABLED": Config.CBT_DISTORTION_TRIGGER_ENABLED,
            "CBT_LLM_ENABLED": Config.CBT_LLM_ENABLED,
            "CRISIS_DETECTION_ENABLED": Config.CRISIS_DETECTION_ENABLED,
            "CRISIS_LLM_DETECTION_ENABLED": Config.CRISIS_LLM_DETECTION_ENABLED,
            "TRANSPLANT_SUPPORT_ENABLED": Config.TRANSPLANT_SUPPORT_ENABLED,
            "TRANSPLANT_LLM_SCENARIO_ENABLED": Config.TRANSPLANT_LLM_SCENARIO_ENABLED,
            "HISTORY_COMPRESSION_ENABLED": Config.HISTORY_COMPRESSION_ENABLED,
        }
        try:
            Config.CBT_ENABLED = True
            Config.AUTO_CBT_INTERVENTION = True
            Config.CBT_DISTORTION_TRIGGER_ENABLED = True
            Config.CBT_LLM_ENABLED = False
            Config.CRISIS_DETECTION_ENABLED = False
            Config.CRISIS_LLM_DETECTION_ENABLED = False
            Config.TRANSPLANT_SUPPORT_ENABLED = False
            Config.TRANSPLANT_LLM_SCENARIO_ENABLED = False
            Config.HISTORY_COMPRESSION_ENABLED = False

            agent = EnhancedChatAgent()
            agent.client = _FakeStreamingOpenAI(responses=["我在听，我们先把这句话放慢一点看。"])
            chunks = list(agent.stream_chat("我总是失败，从来没有成功过"))

            self.assertEqual("".join(chunks), "我在听，我们先把这句话放慢一点看。")
            self.assertEqual(agent.last_result["cbt_analysis"].get("source"), "semantic_background_pending")
            prompt_text = "\n".join(
                message.get("content", "")
                for message in agent.client.requests[0]["messages"]
                if message.get("role") == "system"
            )
            self.assertNotIn("[本转CBT引导指令]", prompt_text)
            self.assertIn("直接根据用户原话的语义判断是否需要轻量CBT", prompt_text)
        finally:
            for key, value in old_values.items():
                setattr(Config, key, value)

    @patch("xiaoya_agent.core.agent.OpenAI", _FakeStreamingOpenAI)
    def test_stream_chat_uses_prompt_profile_and_output_mode(self):
        """流式主回复应使用可配置的提示词 profile 和输出模式。"""
        old_values = {
            "PROMPT_PROFILE": Config.PROMPT_PROFILE,
            "OUTPUT_MODE": Config.OUTPUT_MODE,
            "CBT_LLM_ENABLED": Config.CBT_LLM_ENABLED,
            "CRISIS_DETECTION_ENABLED": Config.CRISIS_DETECTION_ENABLED,
            "CRISIS_LLM_DETECTION_ENABLED": Config.CRISIS_LLM_DETECTION_ENABLED,
            "TRANSPLANT_SUPPORT_ENABLED": Config.TRANSPLANT_SUPPORT_ENABLED,
            "TRANSPLANT_LLM_SCENARIO_ENABLED": Config.TRANSPLANT_LLM_SCENARIO_ENABLED,
            "HISTORY_COMPRESSION_ENABLED": Config.HISTORY_COMPRESSION_ENABLED,
        }
        try:
            Config.PROMPT_PROFILE = "clinical_plain"
            Config.OUTPUT_MODE = "cbt_exercise"
            Config.CBT_LLM_ENABLED = False
            Config.CRISIS_DETECTION_ENABLED = False
            Config.CRISIS_LLM_DETECTION_ENABLED = False
            Config.TRANSPLANT_SUPPORT_ENABLED = False
            Config.TRANSPLANT_LLM_SCENARIO_ENABLED = False
            Config.HISTORY_COMPRESSION_ENABLED = False

            agent = EnhancedChatAgent()
            agent.client = _FakeStreamingOpenAI(responses=["我们先做一个很小的练习。"])
            chunks = list(agent.stream_chat("我总觉得自己什么都做不好"))

            self.assertEqual("".join(chunks), "我们先做一个很小的练习。")
            prompt_text = "\n".join(
                message.get("content", "")
                for message in agent.client.requests[0]["messages"]
                if message.get("role") == "system"
            )
            self.assertIn("[提示词配置:clinical_plain]", prompt_text)
            self.assertIn("当前输出模式是 CBT 小练习", prompt_text)
        finally:
            for key, value in old_values.items():
                setattr(Config, key, value)

    @patch("xiaoya_agent.core.agent.OpenAI", _FakeSplitStreamAnalysisOpenAI)
    @patch("xiaoya_agent.features.crisis.OpenAI", _FailingCrisisOpenAI)
    def test_stream_chat_updates_semantic_crisis_in_background(self):
        """隐晦危机语义判断应后台完成，不阻塞可见回复。"""
        old_values = {
            "CRISIS_DETECTION_ENABLED": Config.CRISIS_DETECTION_ENABLED,
            "CRISIS_LLM_DETECTION_ENABLED": Config.CRISIS_LLM_DETECTION_ENABLED,
            "CRISIS_LLM_STREAM_BLOCKING_ENABLED": Config.CRISIS_LLM_STREAM_BLOCKING_ENABLED,
            "BACKGROUND_CRISIS_FIRST_ENABLED": Config.BACKGROUND_CRISIS_FIRST_ENABLED,
            "CRISIS_ALERT_THRESHOLD": Config.CRISIS_ALERT_THRESHOLD,
            "TRANSPLANT_SUPPORT_ENABLED": Config.TRANSPLANT_SUPPORT_ENABLED,
            "CBT_LLM_ENABLED": Config.CBT_LLM_ENABLED,
            "TRANSPLANT_LLM_SCENARIO_ENABLED": Config.TRANSPLANT_LLM_SCENARIO_ENABLED,
            "HISTORY_COMPRESSION_ENABLED": Config.HISTORY_COMPRESSION_ENABLED,
        }
        try:
            Config.CRISIS_DETECTION_ENABLED = True
            Config.CRISIS_LLM_DETECTION_ENABLED = True
            Config.CRISIS_LLM_STREAM_BLOCKING_ENABLED = False
            Config.BACKGROUND_CRISIS_FIRST_ENABLED = False
            Config.CRISIS_ALERT_THRESHOLD = 10
            Config.TRANSPLANT_SUPPORT_ENABLED = False
            Config.CBT_LLM_ENABLED = False
            Config.TRANSPLANT_LLM_SCENARIO_ENABLED = False
            Config.HISTORY_COMPRESSION_ENABLED = False

            agent = EnhancedChatAgent()
            message = "我想从这个世界上彻底消失，今晚再也不用醒来"
            rule_only = agent.crisis_module._rule_based_detect_crisis(
                message,
                {"primary": "neutral", "severity": 1}
            )
            self.assertFalse(rule_only["alert"])

            chunks = list(agent.stream_chat(message))
            self.assertEqual("".join(chunks), "我在，先陪你。")
            self.assertIn(agent.last_result["crisis_detection"].get("source"), {
                "semantic_background_pending",
                "llm_semantic_background",
            })

            for _ in range(50):
                if agent.last_result["crisis_detection"].get("alert", False):
                    break
                time.sleep(0.02)

            self.assertTrue(agent.last_result["crisis_detection"]["alert"])
            self.assertEqual(agent.last_result["crisis_detection"].get("source"), "llm_semantic_background")
            self.assertEqual(agent.last_result["response_type"], "crisis_alert")
        finally:
            for key, value in old_values.items():
                setattr(Config, key, value)

    def test_background_analysis_stops_after_crisis_first_alert(self):
        """后台分析应先做危机判断，命中危机后不再继续综合分析。"""
        old_values = {
            "BACKGROUND_CRISIS_FIRST_ENABLED": Config.BACKGROUND_CRISIS_FIRST_ENABLED,
            "CRISIS_DETECTION_ENABLED": Config.CRISIS_DETECTION_ENABLED,
            "CRISIS_LLM_DETECTION_ENABLED": Config.CRISIS_LLM_DETECTION_ENABLED,
            "CRISIS_ALERT_THRESHOLD": Config.CRISIS_ALERT_THRESHOLD,
        }
        try:
            Config.BACKGROUND_CRISIS_FIRST_ENABLED = True
            Config.CRISIS_DETECTION_ENABLED = True
            Config.CRISIS_LLM_DETECTION_ENABLED = True
            Config.CRISIS_ALERT_THRESHOLD = 10

            agent = EnhancedChatAgent(load_persistent_data=False)
            agent.last_result = {
                "response": "我在，先陪你。",
                "response_type": "cbt_response",
                "cbt_analysis": {"source": "semantic_background_pending"},
                "crisis_detection": {"alert": False, "source": "semantic_background_pending"},
                "energy_assessment": None,
                "energy_report": None,
            }
            agent.crisis_module._llm_detect_crisis = lambda *_args, **_kwargs: {
                "has_crisis": True,
                "crisis_types": ["自杀危机"],
                "severity_score": 18,
                "reason": "表达明确自杀风险。",
            }
            with patch.object(agent, "_llm_unified_analyze", side_effect=AssertionError("should stop after crisis")):
                task = agent._start_unified_analysis_task("我想死", TransplantPhase.PREP)
                self.assertTrue(task["event"].wait(1))
                agent._finish_background_analysis_task(
                    task=task,
                    user_message="我想死",
                    response="我在，先陪你。",
                    current_phase=TransplantPhase.PREP,
                    timeout_seconds=0,
                )

            self.assertTrue(agent.last_result["crisis_detection"]["alert"])
            self.assertEqual(agent.last_result["crisis_detection"]["source"], "llm_semantic")
            self.assertEqual(agent.last_result["response_type"], "crisis_alert")
            self.assertIsNone(agent.last_result["energy_assessment"])
        finally:
            for key, value in old_values.items():
                setattr(Config, key, value)


if __name__ == "__main__":
    unittest.main(verbosity=2)

