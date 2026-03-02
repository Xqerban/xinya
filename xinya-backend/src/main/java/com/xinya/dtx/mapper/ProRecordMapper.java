package com.xinya.dtx.mapper;

import com.xinya.dtx.entity.ProRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ProRecordMapper extends JpaRepository<ProRecord, Long> {

    /** 查询患者某天的所有打卡答案 */
    List<ProRecord> findByPatientIdAndRecordDateOrderByCreatedAtAsc(
            String patientId, LocalDate recordDate);

    /** 判断患者今天是否已打卡（存在任意一条即视为已打卡） */
    boolean existsByPatientIdAndRecordDate(String patientId, LocalDate recordDate);

    /** 判断某题目是否已有答案（幂等去重用） */
    boolean existsByPatientIdAndRecordDateAndQuestionId(
            String patientId, LocalDate recordDate, String questionId);

    /** 查询患者某日期范围的打卡记录（分页） */
    Page<ProRecord> findByPatientIdAndRecordDateBetweenOrderByRecordDateDesc(
            String patientId, LocalDate start, LocalDate end, Pageable pageable);

    /** 查询患者全部打卡记录（分页） */
    Page<ProRecord> findByPatientIdOrderByRecordDateDesc(String patientId, Pageable pageable);

    /** 查询患者某题目在日期范围内的答题趋势（症状走势图） */
    @Query("SELECT r.recordDate AS date, r.answerScore AS score FROM ProRecord r " +
           "WHERE r.patientId = :patientId AND r.questionId = :questionId " +
           "AND r.recordDate BETWEEN :start AND :end ORDER BY r.recordDate ASC")
    List<Object[]> findScoreTrend(@Param("patientId") String patientId,
                                  @Param("questionId") String questionId,
                                  @Param("start") LocalDate start,
                                  @Param("end") LocalDate end);

    /** 查询患者某天的总分（所有题目分数之和） */
    @Query("SELECT COALESCE(SUM(r.answerScore), 0) FROM ProRecord r " +
           "WHERE r.patientId = :patientId AND r.recordDate = :date")
    int sumScoreByPatientIdAndDate(@Param("patientId") String patientId,
                                   @Param("date") LocalDate date);

    /** 查询患者某天某症状Key的评分（触发症状阈值判断用） */
    @Query("SELECT COALESCE(SUM(r.answerScore), 0) FROM ProRecord r " +
           "WHERE r.patientId = :patientId AND r.recordDate = :date " +
           "AND r.symptomKey = :symptomKey")
    int sumScoreBySymptomKey(@Param("patientId") String patientId,
                             @Param("date") LocalDate date,
                             @Param("symptomKey") String symptomKey);

    /** 查询患者累计打卡天数（不同日期数量） */
    @Query("SELECT COUNT(DISTINCT r.recordDate) FROM ProRecord r WHERE r.patientId = :patientId")
    long countDistinctDateByPatientId(@Param("patientId") String patientId);

    /** 查询患者连续打卡天数（原生SQL，效率更高） */
    @Query(value = """
            SELECT COUNT(*) FROM (
                SELECT record_date,
                       DATE_SUB(record_date, INTERVAL ROW_NUMBER() OVER (ORDER BY record_date DESC) DAY) AS grp
                FROM (SELECT DISTINCT record_date FROM pro_records WHERE patient_id = :patientId
                      ORDER BY record_date DESC) t
            ) grouped
            WHERE grp = (
                SELECT DATE_SUB(CURDATE(), INTERVAL 0 DAY)
                     - INTERVAL ROW_NUMBER() OVER (ORDER BY record_date DESC) DAY
                FROM (SELECT DISTINCT record_date FROM pro_records WHERE patient_id = :patientId
                      ORDER BY record_date DESC LIMIT 1) latest
            )
            """, nativeQuery = true)
    int countContinuousCheckInDays(@Param("patientId") String patientId);

    /** 查询患者最近N天的打卡日期列表（用于判断连续打卡） */
    @Query(value = "SELECT DISTINCT record_date FROM pro_records " +
                   "WHERE patient_id = :patientId ORDER BY record_date DESC LIMIT :days",
           nativeQuery = true)
    List<java.sql.Date> findRecentCheckInDates(@Param("patientId") String patientId,
                                               @Param("days") int days);

    /** 病区维度：某天的症状汇总评分（热力图用） */
    @Query(value = """
            SELECT r.symptom_key, SUM(r.answer_score) AS total_score
            FROM pro_records r
            WHERE r.record_date = :date AND r.symptom_key IS NOT NULL
            GROUP BY r.symptom_key
            """, nativeQuery = true)
    List<Object[]> sumSymptomScoreByDate(@Param("date") LocalDate date);
}
