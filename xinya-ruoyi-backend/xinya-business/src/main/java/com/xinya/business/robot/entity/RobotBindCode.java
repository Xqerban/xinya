package com.xinya.business.robot.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("robot_bind_codes")
public class RobotBindCode {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String patientId;
    private String bindCode;
    private String createdBy;
    private LocalDateTime expiresAt;
    @Builder.Default
    private Boolean used = false;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
