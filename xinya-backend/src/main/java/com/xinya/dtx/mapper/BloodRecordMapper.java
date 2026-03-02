package com.xinya.dtx.mapper;

import com.xinya.dtx.entity.BloodRecord;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface BloodRecordMapper extends JpaRepository<BloodRecord, Long> {

    /** 查询患者最新一条血象记录 */
    Optional<BloodRecord> findTopByPatientIdOrderByRecordDateDesc(String patientId);

    /** 查询患者最近 N 条血象记录（Agent 血象趋势接口用） */
    @Query("SELECT b FROM BloodRecord b WHERE b.patientId = :patientId " +
           "ORDER BY b.recordDate DESC")
    List<BloodRecord> findRecentByPatientId(@Param("patientId") String patientId,
                                            Pageable pageable);

    /** 查询患者某日期范围内的血象记录 */
    List<BloodRecord> findByPatientIdAndRecordDateBetweenOrderByRecordDateAsc(
            String patientId, LocalDate start, LocalDate end);

    /** 查询患者某天的血象记录 */
    Optional<BloodRecord> findByPatientIdAndRecordDate(String patientId, LocalDate recordDate);

    /** 某天是否已录入血象 */
    boolean existsByPatientIdAndRecordDate(String patientId, LocalDate recordDate);

    /** 查询最近7天血象（构建 Agent bloodTrend.history 用） */
    @Query("SELECT b FROM BloodRecord b WHERE b.patientId = :patientId " +
           "AND b.recordDate >= :since ORDER BY b.recordDate ASC")
    List<BloodRecord> findSince(@Param("patientId") String patientId,
                                @Param("since") LocalDate since);

    /** 查询所有活跃患者中有血象记录的患者ID列表（每日批量触发 reminder-plan 用） */
    @Query(value = "SELECT DISTINCT b.patient_id FROM blood_records b " +
                   "INNER JOIN patients p ON b.patient_id = p.id " +
                   "WHERE p.stage NOT IN ('DISCHARGE') " +
                   "AND b.record_date >= :since",
           nativeQuery = true)
    List<String> findActivePatientIdsWithRecentRecord(@Param("since") LocalDate since);
}
