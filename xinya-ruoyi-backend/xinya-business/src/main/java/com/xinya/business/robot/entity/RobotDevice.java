package com.xinya.business.robot.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("robot_devices")
public class RobotDevice {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String deviceId;
    private String patientId;
    private String deviceTokenHash;
    private LocalDateTime tokenExpiresAt;
    /** ONLINE | OFFLINE */
    @Builder.Default
    private String onlineStatus = "OFFLINE";
    private LocalDateTime lastHeartbeatAt;
    /** WIFI | 4G | OFFLINE */
    private String networkStatus;
    private Integer batteryLevel;
    private String appVersion;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
