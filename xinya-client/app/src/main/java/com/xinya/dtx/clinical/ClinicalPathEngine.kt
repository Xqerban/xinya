package com.xinya.dtx.clinical

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 临床路径状态机引擎
 * 管理患者在5个临床阶段之间的状态流转
 */
@Singleton
class ClinicalPathEngine @Inject constructor() {
    
    private val _currentStage = MutableStateFlow(ClinicalStage.ADMISSION)
    val currentStage: StateFlow<ClinicalStage> = _currentStage.asStateFlow()
    
    private val _stageHistory = MutableStateFlow<List<StageTransition>>(emptyList())
    val stageHistory: StateFlow<List<StageTransition>> = _stageHistory.asStateFlow()
    
    // 阶段任务配置
    private val stageTasks = mapOf(
        ClinicalStage.ADMISSION to listOf(
            StageTask("admission_ceremony", "入仓仪式", "完成入仓仪式，播下希望的种子", TaskType.CEREMONY),
            StageTask("first_checkin", "首次打卡", "完成第一次每日打卡", TaskType.CHECKIN),
            StageTask("meet_xiaoya", "认识小芽", "与小芽进行第一次对话", TaskType.CONVERSATION)
        ),
        ClinicalStage.PRETREATMENT to listOf(
            StageTask("pretreatment_education", "预处理知识", "学习预处理相关知识", TaskType.EDUCATION),
            StageTask("symptom_diary", "症状日记", "记录预处理期间的症状", TaskType.CHECKIN),
            StageTask("relaxation", "放松训练", "完成一次冥想放松练习", TaskType.MEDITATION)
        ),
        ClinicalStage.TRANSPLANT to listOf(
            StageTask("transplant_ceremony", "移植仪式", "迎接新生命的到来", TaskType.CEREMONY),
            StageTask("transplant_education", "移植知识", "了解移植后注意事项", TaskType.EDUCATION)
        ),
        ClinicalStage.REBUILD to listOf(
            StageTask("daily_checkin", "每日打卡", "坚持每日健康打卡", TaskType.CHECKIN),
            StageTask("rebuild_education", "重建知识", "学习免疫重建期护理", TaskType.EDUCATION),
            StageTask("positive_diary", "正能量日记", "记录每天的积极事件", TaskType.CONVERSATION)
        ),
        ClinicalStage.DISCHARGE to listOf(
            StageTask("discharge_ceremony", "出仓仪式", "庆祝康复，准备出仓", TaskType.CEREMONY),
            StageTask("discharge_education", "出仓指导", "学习出仓后的注意事项", TaskType.EDUCATION)
        )
    )
    
    /**
     * 初始化患者阶段
     */
    fun initializeStage(stage: ClinicalStage) {
        _currentStage.value = stage
    }
    
    /**
     * 尝试流转到下一阶段
     */
    fun transitionToNext(): TransitionResult {
        val current = _currentStage.value
        val next = current.next()
        
        return if (next != null) {
            performTransition(current, next)
        } else {
            TransitionResult.Failure("已经是最后一个阶段")
        }
    }
    
    /**
     * 流转到指定阶段
     */
    fun transitionTo(targetStage: ClinicalStage): TransitionResult {
        val current = _currentStage.value
        
        return if (current.canTransitionTo(targetStage)) {
            performTransition(current, targetStage)
        } else {
            TransitionResult.Failure("无法从${current.displayName}直接流转到${targetStage.displayName}")
        }
    }
    
    /**
     * 执行阶段流转
     */
    private fun performTransition(from: ClinicalStage, to: ClinicalStage): TransitionResult {
        val transition = StageTransition(
            fromStage = from,
            toStage = to,
            timestamp = System.currentTimeMillis()
        )
        
        _stageHistory.value = _stageHistory.value + transition
        _currentStage.value = to
        
        return TransitionResult.Success(
            newStage = to,
            tasks = getTasksForStage(to),
            ceremony = getCeremonyForTransition(from, to)
        )
    }
    
    /**
     * 获取阶段任务列表
     */
    fun getTasksForStage(stage: ClinicalStage): List<StageTask> {
        return stageTasks[stage] ?: emptyList()
    }
    
    /**
     * 获取阶段流转时的仪式内容
     */
    private fun getCeremonyForTransition(from: ClinicalStage, to: ClinicalStage): CeremonyContent? {
        return when (to) {
            ClinicalStage.ADMISSION -> CeremonyContent(
                title = "生命奠基礼",
                description = "欢迎来到心之港湾，让我们一起种下希望的种子",
                meditationId = "ceremony_admission"
            )
            ClinicalStage.TRANSPLANT -> CeremonyContent(
                title = "生命之河",
                description = "新的生命即将注入，让我们怀着感恩迎接这一刻",
                meditationId = "ceremony_transplant"
            )
            ClinicalStage.DISCHARGE -> CeremonyContent(
                title = "内在黎明",
                description = "恭喜您完成了这段旅程，您是真正的勇士",
                meditationId = "ceremony_discharge"
            )
            else -> null
        }
    }
    
    /**
     * 获取当前阶段的进度信息
     */
    fun getStageProgress(): StageProgress {
        val current = _currentStage.value
        val tasks = getTasksForStage(current)
        
        return StageProgress(
            stage = current,
            totalTasks = tasks.size,
            completedTasks = 0, // TODO: 从数据库获取完成数量
            daysInStage = 0     // TODO: 计算在当前阶段的天数
        )
    }
}

/**
 * 阶段流转记录
 */
data class StageTransition(
    val fromStage: ClinicalStage,
    val toStage: ClinicalStage,
    val timestamp: Long
)

/**
 * 阶段任务
 */
data class StageTask(
    val id: String,
    val title: String,
    val description: String,
    val type: TaskType,
    val isCompleted: Boolean = false
)

enum class TaskType {
    CEREMONY,      // 仪式
    CHECKIN,       // 打卡
    EDUCATION,     // 宣教
    CONVERSATION,  // 对话
    MEDITATION     // 冥想
}

/**
 * 仪式内容
 */
data class CeremonyContent(
    val title: String,
    val description: String,
    val meditationId: String
)

/**
 * 阶段进度
 */
data class StageProgress(
    val stage: ClinicalStage,
    val totalTasks: Int,
    val completedTasks: Int,
    val daysInStage: Int
)

/**
 * 流转结果
 */
sealed class TransitionResult {
    data class Success(
        val newStage: ClinicalStage,
        val tasks: List<StageTask>,
        val ceremony: CeremonyContent?
    ) : TransitionResult()
    
    data class Failure(val reason: String) : TransitionResult()
}
