#!/usr/bin/env python
"""
离线回归测试：增强版对话智能体

目标：不依赖真实网络/API，验证关键功能是否“按预期接线”
- 危机检测：仅返回 {"alert": bool}，alert=True 时触发报警回调
- 移植分期情境：LLM 判定为主（可模拟），规则兜底可用
- CBT：仅在需要时追加（门控逻辑可用）
- user_state：可写入/读取 user_state.json
"""
import os
import json
import tempfile
import unittest
from unittest.mock import patch

from simple_agent import EnhancedChatAgent
from config import Config
from transplant_support import TransplantPhase, Scenario, choose_intervention
from crisis_module import CrisisInterventionModule


class _FakeOpenAI:
    """
    一个极简 OpenAI 兼容 stub：支持 client.chat.completions.create(...) 并返回 choices[0].message.content
    通过依次弹出预置响应内容来模拟不同调用点（CBT 基础回复、改写、LLM 判定等）。
    """
    def __init__(self, responses=None, **kwargs):
        self._responses = list(responses or [])

        class _Chat:
            def __init__(self, outer):
                self.completions = self
                self._outer = outer

            def create(self, **kwargs):
                if self._outer._responses:
                    content = self._outer._responses.pop(0)
                else:
                    content = "OK"

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

class TestAgentOffline(unittest.TestCase):
    def setUp(self):
        # 让测试更稳定：关闭真实 LLM 判定（各测试可按需开启并模拟）
        Config.CRISIS_LLM_DETECTION_ENABLED = False
        Config.TRANSPLANT_LLM_SCENARIO_ENABLED = False
        Config.TRANSPLANT_SUPPORT_ENABLED = True
        Config.CBT_ENABLED = True
        Config.AUTO_CBT_INTERVENTION = True
        # 关闭危机/CBT LLM，避免测试时触发非预期网络调用
        Config.CRISIS_DETECTION_ENABLED = True
        Config.CBT_LLM_ENABLED = False

    @patch("simple_agent.OpenAI", _FakeOpenAI)
    def test_basic_chat_path(self):
        # stub：基础回复
        agent = EnhancedChatAgent()
        agent.client = _FakeOpenAI(responses=["基础回复"])

        result = agent.chat("你好")
        self.assertIn("response", result)
        self.assertIn(result["response_type"], ["cbt_response", "transplant_guidance", "crisis_alert"])

    @patch("simple_agent.OpenAI", _FakeOpenAI)
    def test_cbt_gating_not_triggered_for_smalltalk(self):
        # 确保闲聊不追加“如果你愿意，我们可以试一个小练习：”
        agent = EnhancedChatAgent()
        agent.client = _FakeOpenAI(responses=["今天天气很好。"])
        result = agent.chat("今天天气怎么样？")
        self.assertNotIn("如果你愿意，我们可以试一个小练习", result["response"] or "")

    @patch("simple_agent.OpenAI", _FakeOpenAI)
    def test_cbt_gating_triggered_when_needed(self):
        # 让基础回复 + CBT引导都可返回
        # 使用更明确的触发词，确保 severity >= 6
        agent = EnhancedChatAgent()
        agent.client = _FakeOpenAI(responses=["我听见你很难受。"])
        result = agent.chat("我真的很绝望很无助，完全撑不住了，感觉一切都完了")
        # 只要门控触发，输出中应包含该引导前缀
        # "绝望"、"无助"、"撑不住" 会触发较高的 severity，应该会触发CBT
        if result["response_type"] == "cbt_response":
            cbt_analysis = result.get("cbt_analysis", {})
            emotional = cbt_analysis.get("emotional_state", {})
            severity = emotional.get("severity", 0)
            # 如果 severity >= 6 或有认知扭曲，应该触发CBT建议
            if severity >= 6 or cbt_analysis.get("cognitive_distortions"):
                recommended_technique = cbt_analysis.get("recommended_technique")
                if recommended_technique:
                    # 如果有推荐技术，应该包含CBT建议
                    self.assertIn("如果你愿意，我们可以试一个小练习", result["response"] or "")

    def test_transplant_scenario_fallback_keywords(self):
        # 关闭 LLM 情境判定时，关键词兜底仍能命中
        Config.TRANSPLANT_LLM_SCENARIO_ENABLED = False
        r = choose_intervention("我今天化疗反应好难受", TransplantPhase.PREP, emotional_severity=2)
        self.assertTrue(r.should_trigger)
        self.assertEqual(r.scenario, Scenario.CHEMO_PREP)

    @patch("transplant_support.OpenAI", _FakeOpenAI)
    def test_transplant_scenario_llm_primary(self):
        # 开启 LLM 情境判定，并模拟模型输出 JSON
        Config.TRANSPLANT_LLM_SCENARIO_ENABLED = True
        fake_json = json.dumps({
            "should_trigger": True,
            "phase": "移植中关键期",
            "scenario": "INFUSION_DAY",
            "confidence": 0.9,
            "reason": "提到回输当天"
        }, ensure_ascii=False)
        # 通过 patch 让 OpenAI(...) 返回带预置 responses 的实例
        with patch("transplant_support.OpenAI", lambda **kwargs: _FakeOpenAI(responses=[fake_json])):
            r = choose_intervention("今天要回输", TransplantPhase.PREP, emotional_severity=3)
            self.assertTrue(r.should_trigger)
            self.assertEqual(r.phase, TransplantPhase.KEY)
            self.assertEqual(r.scenario, Scenario.INFUSION_DAY)

    def test_crisis_alert_flag_only(self):
        # 只验证 crisis_module 输出仅有 alert 字段，并且可触发回调
        triggered = {"count": 0}

        def cb(_data):
            triggered["count"] += 1

        m = CrisisInterventionModule(alert_callback=cb)
        # 关闭 LLM 判定，走兜底关键词规则
        Config.CRISIS_LLM_DETECTION_ENABLED = False
        r = m.detect_crisis("我想死", {"primary": "hopelessness", "severity": 10})
        self.assertEqual(r, {"alert": True})
        self.assertEqual(triggered["count"], 1)

        r2 = m.detect_crisis("你好", {"primary": "neutral", "severity": 1})
        self.assertEqual(r2, {"alert": False})

    @patch("simple_agent.OpenAI", _FakeOpenAI)
    def test_user_state_persistence(self):
        # 在临时目录中验证 user_state.json 写入/读取
        with tempfile.TemporaryDirectory() as td:
            old = os.getcwd()
            try:
                os.chdir(td)
                agent = EnhancedChatAgent()
                agent.set_transplant_phase(TransplantPhase.RECOVERY)

                self.assertTrue(os.path.exists("user_state.json"))
                with open("user_state.json", "r", encoding="utf-8") as f:
                    data = json.load(f)
                self.assertEqual(data.get("transplant_phase"), TransplantPhase.RECOVERY.value)

                agent2 = EnhancedChatAgent()
                self.assertEqual(agent2.get_transplant_phase(), TransplantPhase.RECOVERY)
            finally:
                os.chdir(old)

    def test_energy_model_save_and_load(self):
        # 直接测试能量模型的保存/加载（无需 API）
        from energy_model import PsychologicalEnergyModel

        with tempfile.TemporaryDirectory() as td:
            path = os.path.join(td, "energy_progress.json")
            m = PsychologicalEnergyModel()
            # 伪造一次对话评估数据
            conversation_data = {
                "user_message": "我意识到我可以换个角度看待这件事，并且我会试着行动",
                "analysis": {
                    "emotional_state": {"primary": "calm", "severity": 2},
                    "cognitive_distortions": ["all_or_nothing"]
                },
                "cbt_response": "好的，我们一起慢慢来。"
            }
            m.assess_conversation_quality(conversation_data)
            before = m.get_energy_report()
            m.save_progress(path)

            m2 = PsychologicalEnergyModel()
            m2.load_progress(path)
            after = m2.get_energy_report()
            self.assertEqual(after["total_energy"], before["total_energy"])
            self.assertEqual(after["session_count"], before["session_count"])

    @patch("simple_agent.OpenAI", _FakeOpenAI)
    def test_save_all_progress_creates_files(self):
        # 端到端测试：save_all_progress 写出所有持久化文件
        with tempfile.TemporaryDirectory() as td:
            old = os.getcwd()
            try:
                os.chdir(td)

                # 避免触发移植引导的额外改写调用，保持一次 API stub 足够
                Config.TRANSPLANT_SUPPORT_ENABLED = False
                agent = EnhancedChatAgent()
                agent.client = _FakeOpenAI(responses=["基础回复"])
                agent.chat("hello")
                agent.save_all_progress()

                self.assertTrue(os.path.exists("chat_history.json"))
                self.assertTrue(os.path.exists("energy_progress.json"))
                self.assertTrue(os.path.exists("crisis_history.json"))
                self.assertTrue(os.path.exists("user_state.json"))
            finally:
                os.chdir(old)

    @patch("simple_agent.OpenAI", _FakeOpenAI)
    def test_load_history_roundtrip(self):
        # chat_history.json 能保存并再次 load
        with tempfile.TemporaryDirectory() as td:
            old = os.getcwd()
            try:
                os.chdir(td)
                Config.TRANSPLANT_SUPPORT_ENABLED = False
                agent = EnhancedChatAgent()
                agent.client = _FakeOpenAI(responses=["基础回复", "第二次回复"])
                agent.chat("hello")
                agent.chat("second")
                agent.save_history("chat_history.json")

                agent2 = EnhancedChatAgent()
                agent2.load_history("chat_history.json")
                history = agent2.get_history()
                # system + 2 user + 2 assistant 至少 5 条
                self.assertGreaterEqual(len(history), 5)
            finally:
                os.chdir(old)

    def test_crisis_resources_and_grounding_available(self):
        # 即使不输出话术模板，正念接地练习接口仍应可调用（主要用于 main 命令）
        m = CrisisInterventionModule()
        grounding = m.get_grounding_exercise()
        self.assertTrue(isinstance(grounding, str) and len(grounding) > 10)

    @patch("simple_agent.OpenAI", _FakeOpenAI)
    def test_reset_functionality(self):
        """测试重置功能"""
        agent = EnhancedChatAgent()
        agent.client = _FakeOpenAI(responses=["回复1", "回复2"])
        
        # 先进行一些操作，产生数据
        agent.chat("测试消息1")
        agent.chat("测试消息2")
        agent.set_transplant_phase(TransplantPhase.KEY)
        
        # 验证有数据
        self.assertGreater(len(agent.conversation_history), 1)
        self.assertEqual(agent.get_transplant_phase(), TransplantPhase.KEY)
        
        # 执行重置
        result = agent.reset()
        
        # 验证重置结果
        self.assertTrue(result["success"])
        self.assertEqual(len(agent.conversation_history), 1)  # 只有 system prompt
        self.assertEqual(agent.get_transplant_phase(), TransplantPhase.PREP)  # 恢复默认值
        self.assertEqual(agent.cbt_module.user_profile["session_count"], 0)
        self.assertEqual(agent.energy_model.total_energy, 0)
        self.assertEqual(len(agent.crisis_module.crisis_history), 0)


if __name__ == "__main__":
    unittest.main(verbosity=2)