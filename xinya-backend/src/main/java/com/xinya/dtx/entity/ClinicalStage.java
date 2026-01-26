package com.xinya.dtx.entity;

/**
 * 临床5阶段枚举
 */
public enum ClinicalStage {
    ADMISSION("入仓期", 1),
    PRETREATMENT("预处理期", 2),
    TRANSPLANT("移植期", 3),
    REBUILD("重建期", 4),
    DISCHARGE("出仓期", 5);
    
    private final String displayName;
    private final int order;
    
    ClinicalStage(String displayName, int order) {
        this.displayName = displayName;
        this.order = order;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public int getOrder() {
        return order;
    }
    
    public ClinicalStage next() {
        for (ClinicalStage stage : values()) {
            if (stage.order == this.order + 1) {
                return stage;
            }
        }
        return null;
    }
    
    public boolean canTransitionTo(ClinicalStage target) {
        return target.order == this.order + 1 || target.order == this.order - 1;
    }
}
