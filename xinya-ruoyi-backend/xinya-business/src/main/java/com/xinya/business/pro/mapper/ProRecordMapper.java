package com.xinya.business.pro.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xinya.business.pro.entity.ProRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface ProRecordMapper extends BaseMapper<ProRecord> {

    @Select("SELECT COUNT(1) > 0 FROM pro_records WHERE patient_id = #{patientId} AND record_date = #{recordDate}")
    boolean existsByPatientIdAndRecordDate(@Param("patientId") String patientId,
                                           @Param("recordDate") LocalDate recordDate);

    @Select("SELECT COALESCE(SUM(answer_score), 0) FROM pro_records " +
            "WHERE patient_id = #{patientId} AND record_date = #{recordDate}")
    int sumScoreByPatientIdAndDate(@Param("patientId") String patientId,
                                   @Param("recordDate") LocalDate recordDate);

    @Select("SELECT * FROM pro_records WHERE patient_id = #{patientId} AND record_date = #{recordDate} " +
            "ORDER BY created_at ASC")
    List<ProRecord> findByPatientIdAndRecordDateOrderByCreatedAtAsc(@Param("patientId") String patientId,
                                                                     @Param("recordDate") LocalDate recordDate);

    IPage<ProRecord> findByPatientIdAndDateRangeOrderByDesc(Page<ProRecord> page,
                                                            @Param("patientId") String patientId,
                                                            @Param("start") LocalDate start,
                                                            @Param("end") LocalDate end);

    @Select("SELECT log_date AS record_date, AVG(answer_score) AS avg_score FROM pro_records " +
            "WHERE patient_id = #{patientId} AND question_id = #{questionId} " +
            "AND record_date BETWEEN #{start} AND #{end} " +
            "GROUP BY record_date ORDER BY record_date ASC")
    List<Object[]> findScoreTrend(@Param("patientId") String patientId,
                                   @Param("questionId") String questionId,
                                   @Param("start") LocalDate start,
                                   @Param("end") LocalDate end);

    @Select("SELECT symptom_key, SUM(answer_score) AS total_score FROM pro_records " +
            "WHERE record_date = #{date} AND symptom_key IS NOT NULL " +
            "GROUP BY symptom_key")
    List<Object[]> sumSymptomScoreByDate(LocalDate date);

    @Select("SELECT COUNT(DISTINCT record_date) FROM pro_records WHERE patient_id = #{patientId}")
    long countDistinctDateByPatientId(String patientId);

    @Select("SELECT COUNT(DISTINCT patient_id) FROM pro_records WHERE record_date = #{recordDate}")
    long countByRecordDate(@Param("recordDate") LocalDate recordDate);

    @Select("""
        SELECT COUNT(*) FROM (
            SELECT record_date FROM pro_records
            WHERE patient_id = #{patientId}
            GROUP BY record_date
            HAVING record_date >= CURDATE() - INTERVAL 30 DAY
        ) t
    """)
    int countContinuousCheckInDays(String patientId);
}
