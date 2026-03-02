package com.xinya.dtx.mapper;

import com.xinya.dtx.entity.PsychEnergyLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PsychEnergyLogMapper extends JpaRepository<PsychEnergyLog, Long> {

    /** 查询患者某日期范围内的能量日志（趋势图） */
    List<PsychEnergyLog> findByPatientIdAndLogDateBetweenOrderByLogDateAsc(
            String patientId, LocalDate start, LocalDate end);

    /** 查询患者最新一条能量记录（获取当前实时能量值） */
    Optional<PsychEnergyLog> findTopByPatientIdOrderByCreatedAtDesc(String patientId);

    /** 查询患者某天所有能量变化记录 */
    List<PsychEnergyLog> findByPatientIdAndLogDateOrderByCreatedAtAsc(
            String patientId, LocalDate logDate);

    /** 查询某日期范围内的平均能量（驾驶舱统计） */
    @Query("SELECT COALESCE(AVG(l.psychEnergy), 0) FROM PsychEnergyLog l " +
           "WHERE l.patientId = :patientId AND l.logDate BETWEEN :start AND :end")
    Double avgEnergyBetween(@Param("patientId") String patientId,
                            @Param("start") LocalDate start,
                            @Param("end") LocalDate end);

    /** 查询某日期范围内的最低能量 */
    @Query("SELECT MIN(l.psychEnergy) FROM PsychEnergyLog l " +
           "WHERE l.patientId = :patientId AND l.logDate BETWEEN :start AND :end")
    Integer minEnergyBetween(@Param("patientId") String patientId,
                              @Param("start") LocalDate start,
                              @Param("end") LocalDate end);

    /** 查询某日期范围内的最高能量 */
    @Query("SELECT MAX(l.psychEnergy) FROM PsychEnergyLog l " +
           "WHERE l.patientId = :patientId AND l.logDate BETWEEN :start AND :end")
    Integer maxEnergyBetween(@Param("patientId") String patientId,
                              @Param("start") LocalDate start,
                              @Param("end") LocalDate end);

    /** 按天聚合（每天取最后一次记录值，用于趋势折线图） */
    @Query(value = """
            SELECT l.log_date, l.psych_energy
            FROM psych_energy_log l
            INNER JOIN (
                SELECT patient_id, log_date, MAX(created_at) AS max_time
                FROM psych_energy_log
                WHERE patient_id = :patientId AND log_date BETWEEN :start AND :end
                GROUP BY patient_id, log_date
            ) latest ON l.patient_id = latest.patient_id
                     AND l.log_date = latest.log_date
                     AND l.created_at = latest.max_time
            ORDER BY l.log_date ASC
            """, nativeQuery = true)
    List<Object[]> findDailyEnergyTrend(@Param("patientId") String patientId,
                                        @Param("start") LocalDate start,
                                        @Param("end") LocalDate end);
}
