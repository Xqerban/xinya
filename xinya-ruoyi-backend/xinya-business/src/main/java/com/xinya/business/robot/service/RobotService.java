package com.xinya.business.robot.service;

import com.xinya.business.robot.dto.*;

public interface RobotService {
    RobotDeviceDto registerOrUpdate(RobotRegisterRequest request);
    RobotDeviceDto getByDeviceId(String deviceId);
    BindCodeDto generateBindCode(String patientId);
    RobotDeviceDto bindPatient(RobotBindRequest request);
    RobotDeviceDto unbindPatient(String deviceId);
}
