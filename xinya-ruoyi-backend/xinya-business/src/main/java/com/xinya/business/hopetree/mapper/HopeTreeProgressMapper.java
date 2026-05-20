package com.xinya.business.hopetree.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xinya.business.hopetree.entity.HopeTreeProgress;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;

@Mapper
public interface HopeTreeProgressMapper extends BaseMapper<HopeTreeProgress> {

    @Select("SELECT * FROM hope_tree_progress WHERE patient_id = #{patientId} LIMIT 1")
    HopeTreeProgress findByPatientId(String patientId);

    @Select("SELECT COUNT(1) > 0 FROM hope_tree_progress WHERE patient_id = #{patientId}")
    boolean existsByPatientId(String patientId);

    @Update("UPDATE hope_tree_progress SET current_exp = current_exp + #{exp}, " +
            "total_exp = total_exp + #{exp}, last_growth_date = #{now}, updated_at = #{now} " +
            "WHERE patient_id = #{patientId}")
    void addExp(@Param("patientId") String patientId,
                @Param("exp") int exp,
                @Param("now") LocalDateTime now);

    @Update("UPDATE hope_tree_progress SET current_level = #{level}, current_exp = #{exp}, " +
            "next_level_exp = #{nextExp}, updated_at = NOW() WHERE patient_id = #{patientId}")
    void updateAfterLevelUp(@Param("patientId") String patientId,
                            @Param("level") int level,
                            @Param("exp") int exp,
                            @Param("nextExp") int nextExp);
}
