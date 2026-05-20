package com.xinya.business.patient.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xinya.business.patient.entity.PsychEnergyLog;
import org.apache.ibatis.annotations.*;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface PsychEnergyLogMapper extends BaseMapper<PsychEnergyLog> {

    @Select("SELECT COALESCE(AVG(psych_energy), 0) FROM psych_energy_log " +
            "WHERE patient_id = #{patientId} AND log_date BETWEEN #{start} AND #{end}")
    Double avgEnergyBetween(@Param("patientId") String patientId,
                            @Param("start") LocalDate start,
                            @Param("end") LocalDate end);

    @Select("SELECT MIN(psych_energy) FROM psych_energy_log " +
            "WHERE patient_id = #{patientId} AND log_date BETWEEN #{start} AND #{end}")
    Integer minEnergyBetween(@Param("patientId") String patientId,
                              @Param("start") LocalDate start,
                              @Param("end") LocalDate end);

    @Select("SELECT MAX(psych_energy) FROM psych_energy_log " +
            "WHERE patient_id = #{patientId} AND log_date BETWEEN #{start} AND #{end}")
    Integer maxEnergyBetween(@Param("patientId") String patientId,
                              @Param("start") LocalDate start,
                              @Param("end") LocalDate end);

    @Select("SELECT l.log_date, l.psych_energy FROM psych_energy_log l " +
            "INNER JOIN (SELECT patient_id, log_date, MAX(created_at) AS max_time " +
            "FROM psych_energy_log WHERE patient_id = #{patientId} " +
            "AND log_date BETWEEN #{start} AND #{end} GROUP BY patient_id, log_date) latest " +
            "ON l.patient_id = latest.patient_id AND l.log_date = latest.log_date " +
            "AND l.created_at = latest.max_time ORDER BY l.log_date ASC")
    List<Object[]> findDailyEnergyTrend(@Param("patientId") String patientId,
                                        @Param("start") LocalDate start,
                                        @Param("end") LocalDate end);
}
