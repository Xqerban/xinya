package com.xinya.business.robot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RobotHeartbeatResponse {
    private long serverTime;
    private int pendingPushMessages;
}
