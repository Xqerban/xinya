package com.xinya.business.agent.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("conversations")
public class Conversation {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String patientId;
    /** psych | nurse */
    private String agentType;
    private String sessionId;
    private String message;
    private Boolean isFromUser;
    @Builder.Default
    private Integer psychEnergyDelta = 0;
    @Builder.Default
    private Integer hopeTreeExpDelta = 0;
    @Builder.Default
    private Boolean crisisAlert = false;
    /** none | watch | warning | critical */
    private String crisisLevel;
    private String crisisKeywords;
    private String emotionSignals;
    private Long clientTimestamp;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
