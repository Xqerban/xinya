package com.xinya.dtx.clinical

/**
 * 临床5阶段枚举
 * 对应骨髓移植的完整治疗周期
 */
enum class ClinicalStage(
    val displayName: String,
    val description: String,
    val order: Int
) {
    ADMISSION(
        displayName = "入仓期",
        description = "入住隔离病房，进行基础检查和心理准备",
        order = 1
    ),
    PRETREATMENT(
        displayName = "预处理期",
        description = "进行化疗或放疗，清除原有造血细胞",
        order = 2
    ),
    TRANSPLANT(
        displayName = "移植期",
        description = "输注造血干细胞，新生命开始",
        order = 3
    ),
    REBUILD(
        displayName = "重建期",
        description = "等待造血重建，免疫系统恢复",
        order = 4
    ),
    DISCHARGE(
        displayName = "出仓期",
        description = "达到出仓标准，准备离开隔离病房",
        order = 5
    );
    
    companion object {
        fun fromOrder(order: Int): ClinicalStage? {
            return entries.find { it.order == order }
        }
        
        fun fromName(name: String): ClinicalStage? {
            return entries.find { it.name == name || it.displayName == name }
        }
    }
    
    /**
     * 获取下一个阶段
     */
    fun next(): ClinicalStage? {
        return fromOrder(order + 1)
    }
    
    /**
     * 获取上一个阶段
     */
    fun previous(): ClinicalStage? {
        return fromOrder(order - 1)
    }
    
    /**
     * 是否可以流转到目标阶段
     */
    fun canTransitionTo(target: ClinicalStage): Boolean {
        // 只允许顺序流转或回退一个阶段
        return target.order == this.order + 1 || target.order == this.order - 1
    }
}
