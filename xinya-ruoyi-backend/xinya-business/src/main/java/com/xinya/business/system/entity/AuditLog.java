package com.xinya.business.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("audit_logs")
public class AuditLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String operatorId;
    private String operatorName;
    /** STAGE_TRANSITION | CREATE_PATIENT | RESOLVE_ALERT | ... */
    private String action;
    /** patient | user | alert | content */
    private String targetType;
    private String targetId;
    private String detail;
    private String ipAddress;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
