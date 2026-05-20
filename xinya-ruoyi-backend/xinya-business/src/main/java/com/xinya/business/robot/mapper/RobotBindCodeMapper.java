package com.xinya.business.robot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xinya.business.robot.entity.RobotBindCode;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;

@Mapper
public interface RobotBindCodeMapper extends BaseMapper<RobotBindCode> {

    @Select("SELECT * FROM robot_bind_codes WHERE patient_id = #{patientId} " +
            "AND bind_code = #{bindCode} AND used = false AND expires_at > #{now} LIMIT 1")
    RobotBindCode findValidCode(@Param("patientId") String patientId,
                                @Param("bindCode") String bindCode,
                                @Param("now") LocalDateTime now);

    @Select("SELECT * FROM robot_bind_codes WHERE patient_id = #{patientId} " +
            "AND used = false AND expires_at > #{now} ORDER BY created_at DESC LIMIT 1")
    RobotBindCode findLatestValidByPatientId(@Param("patientId") String patientId,
                                             @Param("now") LocalDateTime now);

    @Update("UPDATE robot_bind_codes SET used = true WHERE id = #{id}")
    void markUsed(Long id);

    @Delete("DELETE FROM robot_bind_codes WHERE expires_at < #{now}")
    void deleteExpired(LocalDateTime now);
}
