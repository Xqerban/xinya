package com.xinya.business.education.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xinya.business.education.entity.EducationProgress;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface EducationProgressMapper extends BaseMapper<EducationProgress> {

    @Select("SELECT * FROM education_progress WHERE patient_id = #{patientId} AND content_id = #{contentId} LIMIT 1")
    EducationProgress findByPatientIdAndContentId(@Param("patientId") String patientId,
                                                  @Param("contentId") String contentId);

    @Select("SELECT COUNT(1) FROM education_progress WHERE patient_id = #{patientId} AND completed = true")
    long countByPatientIdAndCompletedTrue(String patientId);

    @Select("SELECT COALESCE(SUM(watched_seconds), 0) FROM education_progress WHERE patient_id = #{patientId}")
    int sumWatchedSecondsByPatientId(String patientId);

    @Select("SELECT * FROM education_progress WHERE patient_id = #{patientId} ORDER BY last_watched_at DESC")
    List<EducationProgress> findByPatientIdOrderByLastWatchedAtDesc(String patientId);

    @Update("UPDATE education_progress SET completed = true, reward_given = true " +
            "WHERE patient_id = #{patientId} AND content_id = #{contentId} AND reward_given = false")
    int markCompletedAndReward(@Param("patientId") String patientId,
                               @Param("contentId") String contentId);

    @Select("SELECT COALESCE(AVG(CASE WHEN completed = true THEN 1.0 ELSE 0.0 END), 0) FROM education_progress")
    Double avgCompletionRate();

    @Select("SELECT COALESCE(AVG(watched_seconds), 0) FROM education_progress")
    Double avgWatchSeconds();
}
