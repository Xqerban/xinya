package com.xinya.business.sync.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sync_items")
public class SyncItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String clientId;
    private String deviceId;
    private String patientId;
    /** pro_submit | agent_chat | hopetree_grow | education_progress | robot_data */
    private String itemType;
    private String payload;
    /** pending | success | failed */
    @Builder.Default
    private String status = "pending";
    private String serverId;
    private Integer errorCode;
    private String errorMessage;
    @Builder.Default
    private Integer retryCount = 0;
    private Long clientCreatedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    private LocalDateTime processedAt;
}
