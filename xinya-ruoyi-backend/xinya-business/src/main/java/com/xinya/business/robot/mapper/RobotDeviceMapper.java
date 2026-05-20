package com.xinya.business.robot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xinya.business.robot.entity.RobotDevice;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RobotDeviceMapper extends BaseMapper<RobotDevice> {

    @Select("SELECT * FROM robot_devices WHERE device_id = #{deviceId} LIMIT 1")
    RobotDevice findByDeviceId(String deviceId);

    @Select("SELECT * FROM robot_devices WHERE patient_id = #{patientId} LIMIT 1")
    RobotDevice findByPatientId(String patientId);
}
