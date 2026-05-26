package com.xinya.ops.audit.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

import java.time.LocalDateTime;

@TableName("audit_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpsAuditLog {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("operator_id")
    private String operatorId;

    @TableField("operator_name")
    private String operatorName;

    @TableField("action")
    private String action;

    @TableField("target_type")
    private String targetType;

    @TableField("target_id")
    private String targetId;

    @TableField("detail")
    private String detail;

    @TableField("ip_address")
    private String ipAddress;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
