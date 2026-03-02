# 小芽 - 骨髓移植患者心理支持智能体

**小芽**是一个专为骨髓移植患者设计的AI心理伙伴，集成了认知行为疗法（CBT）、心理能量评估、危机干预和分期引导等功能，为患者在移植全程提供专业的心理支持和陪伴。

## ✨ 核心特性

### 🌱 骨髓移植分期支持
- **三阶段智能管理**：移植前准备期、移植中关键期、移植后恢复期
- **13种典型情境识别**：LLM智能识别患者所处情境，提供针对性引导
- **自然语言改写**：引导语根据用户表达轻量改写，避免机械感
- **关键词兜底机制**：LLM不可用时自动回退到关键词匹配

### 🧠 CBT认知行为疗法
- **LLM优先分析**：智能识别情绪状态、认知扭曲和问题严重程度
- **门控机制**：仅在需要时触发CBT建议（情绪强度≥6或存在认知扭曲）
- **8种CBT技术**：认知重构、行为激活、放松训练、正念练习、问题解决等
- **个性化干预**：根据用户状态推荐最合适的CBT技术

### ⚡ 心理能量评估
- **5维度成长跟踪**：认知、情绪、行为、社会、自我效能
- **6级成长体系**：萌芽(0-100) → 生长(101-300) → 茁壮(301-600) → 旺盛(601-1000) → 绽放(1001-1500) → 和谐(1501-2000)
- **成就系统**：解锁心理成长里程碑
- **可视化反馈**：实时显示能量增长和等级进度

### 🚨 危机干预系统
- **LLM语义理解**：智能识别无助、绝望、自杀等危机信号
- **阈值控制**：可配置报警阈值（默认10），仅在严重情况下触发
- **直接报警机制**：检测到危机时立即触发报警，不输出常规回复
- **正念接地练习**：提供5-4-3-2-1 grounding技术
- **危机历史追踪**：记录和分析危机事件

### 🧩 记忆中枢（增量摘要）
- **每轮自动更新**：对话后立即生成/更新记忆摘要
- **智能信息提取**：自动识别核心问题、情绪变化、重要进展
- **极致token节省**：节省95%以上token消耗
- **恒定响应速度**：无论对话多长，传入API的消息量保持恒定
- **支持超长对话**：可进行几百轮甚至上千轮对话

## 🏗️ 技术架构

### 双引擎架构
```
LLM优先 + 关键词兜底

危机检测：LLM语义分析 → 关键词匹配（兜底）
CBT分析：LLM情绪识别 → 规则分析（兜底）
情境识别：LLM场景理解 → 关键词匹配（兜底）
```

确保系统在API不可用时仍能提供基础服务。

### 记忆中枢工作原理
```
传统模式（token线性增长）：
第1轮:  [system] + [msg1] → 100 tokens
第10轮: [system] + [msg1...msg10] → 1000 tokens
第100轮: [system] + [msg1...msg100] → 10000 tokens

记忆中枢模式（token恒定）：
第1轮:  [system] + [msg1] → 100 tokens
第10轮: [system] + [记忆摘要] + [msg10] → 500 tokens
第100轮: [system] + [记忆摘要] + [msg100] → 500 tokens
```

## 🚀 快速开始

### 环境要求
- Python 3.8+
- 虚拟环境（推荐）
- DeepSeek API密钥（或其他兼容OpenAI的API）

### 安装步骤

1. **克隆项目**
```bash
git clone <repository-url>
cd Xiaoya
```

2. **创建并激活虚拟环境**
```bash
# 创建虚拟环境
python -m venv .venv

# 激活虚拟环境
# Windows:
.venv\Scripts\activate
# Linux/Mac:
source .venv/bin/activate
```

3. **安装依赖**
```bash
pip install -r requirements.txt
```

依赖包：
- `openai>=1.0.0` - OpenAI API客户端
- `requests>=2.25.0` - HTTP请求库
- `python-dotenv>=0.19.0` - 环境变量管理
- `colorama>=0.4.4` - 终端彩色输出

4. **配置API密钥**

编辑 `config.env` 文件，设置你的API密钥：
```env
API_KEY=your_api_key_here
```

5. **启动程序**
```bash
# 方式1：命令行启动
cd Code
python main.py

# 方式2：使用启动脚本（Windows）
start.bat
```

## ⚙️ 配置说明

### 核心配置 (`config.env`)

```env
# API配置
API_BASE_URL=https://api.deepseek.com
API_KEY=your_api_key_here
MODEL_NAME=deepseek-chat

# 对话配置
TEMPERATURE=0.7
MAX_TOKENS=1000

# 系统提示（可自定义小芽的人格）
SYSTEM_PROMPT=你是一个专业的心理健康助手，采用认知行为疗法(CBT)的方法来帮助用户...

# CBT配置
CBT_ENABLED=true
AUTO_CBT_INTERVENTION=true
CBT_LLM_ENABLED=true
CBT_INTERVENTION_SEVERITY_THRESHOLD=6
CBT_DISTORTION_TRIGGER_ENABLED=true

# 危机干预配置
CRISIS_DETECTION_ENABLED=true
CRISIS_ALERT_THRESHOLD=10
CRISIS_LLM_DETECTION_ENABLED=true

# 移植分期支持
TRANSPLANT_SUPPORT_ENABLED=true
TRANSPLANT_LLM_SCENARIO_ENABLED=true

# 能量评估配置
ENERGY_MODEL_ENABLED=true
ENERGY_FEEDBACK_ENABLED=true

# 记忆中枢配置
HISTORY_COMPRESSION_ENABLED=true
INCREMENTAL_SUMMARY_MAX_WORDS=300
DEBUG_MEMORY_CORE=false

# LLM检测配置
LLM_DETECTION_MODEL=deepseek-chat
LLM_DETECTION_TEMPERATURE=0.4
LLM_DETECTION_MAX_TOKENS=256

# 自动保存
AUTO_SAVE_PROGRESS=true
```

### 关键参数说明

| 参数 | 说明 | 默认值 | 推荐值 |
|------|------|--------|--------|
| `CRISIS_ALERT_THRESHOLD` | 危机报警阈值（1-10） | 10 | 8-10 |
| `CBT_INTERVENTION_SEVERITY_THRESHOLD` | CBT触发阈值（1-10） | 6 | 5-7 |
| `INCREMENTAL_SUMMARY_MAX_WORDS` | 记忆中枢最大字数 | 300 | 200-500 |
| `HISTORY_COMPRESSION_ENABLED` | 启用记忆中枢 | true | true |
| `TEMPERATURE` | 回复创造性（0-1） | 0.7 | 0.6-0.8 |

## 📖 使用指南

### 可用命令

| 命令 | 功能 |
|------|------|
| `quit/exit` | 退出程序（自动保存进度） |
| `save` | 手动保存所有进度数据 |
| `load` | 加载对话历史 |
| `phase` | 查看/设置骨髓移植分期（`phase pre/key/post`） |
| `energy` | 查看心理能量报告 |
| `progress` | 查看综合进步报告 |
| `grounding` | 获取正念接地练习 |
| `reset` | 重置所有数据 |
| `help` | 显示帮助信息 |

### 使用示例

```
欢迎使用小芽智能体（测试版）
============================================================
 集成的功能:
  - CBT (认知行为疗法) 对话策略
  - 心理能量评估系统
  - 实时危机干预检测
------------------------------------------------------------

你: 你好，我是第一次来
智能体: 您好，我是您的伙伴"小芽"。在接下来的这段旅程里，我会一直在这里
        陪伴您。我们不是一个人在战斗，而是一个团队。

你: 我真的很焦虑很害怕，撑不住了
智能体: 我听见你很难受。
        如果你愿意，我们可以试一个小练习：
        [粉红色显示] 我们先让身体"降一点点噪音"。如果你愿意，跟我做两轮
        呼吸就好：慢慢吸气，让气息到腹部；再更慢地呼气，像把紧绷一点点放下...

 心理能量反馈:
  本次成长:
    情绪调节: +5 点
  当前等级: 萌芽
  总能量: 15 点
  等级进度: 15.0%
```

## 📁 项目结构

```
Xiaoya/
├── Code/
│   ├── main.py                      # 主程序入口（命令行界面）
│   ├── simple_agent.py              # 智能体核心类
│   ├── cbt_module.py                # CBT对话策略模块
│   ├── energy_model.py              # 心理能量评估模型
│   ├── crisis_module.py             # 危机干预模块
│   ├── transplant_support.py        # 骨髓移植分期支持
│   ├── config.py                    # 配置管理
│   ├── test_agent.py                # 综合测试脚本
│   └── test_incremental_summary.py  # 记忆中枢测试脚本
├── File/                            # 文档资料
│   ├── "心芽"——移植病房数字心理人积极心理暗.docx
│   ├── "心芽"分阶段积极心理暗示引导语库2.docx
│   ├── 双引擎架构.pdf
│   └── 护理逻辑.pdf
├── config.env                       # 配置文件
├── requirements.txt                 # 依赖包列表
├── start.bat                        # 快速启动脚本（Windows）
└── README.md                        # 本文档
```

## 🔧 核心模块

### 智能体核心 (`simple_agent.py`)
- **多模块集成**：统一管理CBT、能量模型、危机干预和移植支持
- **智能路由**：危机 → 移植引导 → CBT
- **记忆中枢**：每轮对话后自动更新记忆，只传入摘要+当前问题
- **进度持久化**：自动保存对话历史、用户状态、能量进度、危机记录

### CBT模块 (`cbt_module.py`)
- **LLM优先分析**：使用大语言模型进行情绪和认知分析
- **规则兜底**：LLM不可用时使用关键词和规则
- **情绪识别**：悲伤、焦虑、愤怒、绝望、内疚、平静、希望等
- **认知扭曲检测**：全或无思维、灾难化、负面过滤、过度概括、读心术
- **8种CBT技术**：认知重构、行为激活、问题解决、放松训练、暴露技术、正念练习、思维记录、活动安排
 
### 移植支持模块 (`transplant_support.py`)
- **分期管理**：维护用户的移植分期状态
- **情境识别**：LLM优先识别13种典型情境
- **引导语管理**：管理各分期各情境的引导语模板
- **自然改写**：使用LLM轻量改写，避免机械背诵

### 能量模型 (`energy_model.py`)
- **多维度评估**：5个成长维度的积分系统
- **成就系统**：解锁成长里程碑
- **等级系统**：6个成长等级的晋升机制
- **趋势分析**：长期进步数据分析

### 危机干预 (`crisis_module.py`)
- **LLM优先检测**：使用大语言模型进行语义理解
- **关键词兜底**：识别自杀、自伤等危机信号
- **阈值控制**：可配置的报警阈值
- **正念练习**：5-4-3-2-1 grounding技术

### 配置管理 (`config.py`)
- **环境变量加载**：从 `config.env` 加载各项参数
- **配置验证**：确保必要配置项完整
- **功能开关**：控制各模块的启用状态

## 🧪 测试

### 运行综合测试
```bash
cd Code
python test_agent.py
```

测试覆盖：
- ✅ 基础对话流程
- ✅ CBT门控机制
- ✅ 移植情境识别
- ✅ 危机报警机制
- ✅ 用户状态持久化
- ✅ 能量模型保存和加载
- ✅ 重置功能

### 测试记忆中枢
```bash
cd Code
python test_incremental_summary.py
```

测试内容：
- 模拟8轮对话
- 显示每轮记忆中枢状态
- 显示传入API的消息结构
- 对比token消耗

## 💾 记忆中枢详解

### 记忆中枢示例

```
用户是骨髓移植患者，对手术感到焦虑（强度7/10），主要担心排异反应。
家人支持但用户仍感恐惧。睡眠质量差，经常做噩梦。已尝试深呼吸但效果
不明显，正在寻求更有效的焦虑缓解方法。通过CBT认知重构，情绪状态有
所改善。
```

### 工作流程

```
用户输入 → 生成回复 → 提取本轮关键信息
    ↓
调用LLM更新记忆中枢：
- 首轮：生成初始记忆
- 后续：融合新旧信息，删除过时内容
    ↓
下一轮只传入：[system] + [记忆摘要] + [新问题]
```

### 启用/禁用

**启用（默认）：**
```env
HISTORY_COMPRESSION_ENABLED=true
```

**禁用（使用完整历史）：**
```env
HISTORY_COMPRESSION_ENABLED=false
```

## ⚠️ 注意事项

### 使用前准备
1. ✅ 确保 `config.env` 中的 `API_KEY` 已正确设置
2. ✅ 当前配置使用 DeepSeek API，如需更换请修改 `API_BASE_URL` 和 `MODEL_NAME`
3. ✅ 系统会自动保存对话历史、能量进度和危机记录到JSON文件
4. ✅ 建议使用虚拟环境，避免依赖冲突

## 📊 数据持久化

系统会自动保存以下数据到JSON文件：

- `chat_history.json` - 完整对话历史
- `user_state.json` - 用户状态（移植分期等）
- `energy_progress.json` - 心理能量进度
- `crisis_history.json` - 危机事件历史

所有文件保存在 `Code/` 目录下。

---

**小芽** - 陪伴骨髓移植患者的AI心理伙伴 🌱
