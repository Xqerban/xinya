package com.xinya.business.hopetree.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xinya.business.hopetree.entity.HopeTreeGrowthHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

@Mapper
public interface HopeTreeGrowthHistoryMapper extends BaseMapper<HopeTreeGrowthHistory> {

    @Select("SELECT * FROM hope_tree_growth_history WHERE patient_id = #{patientId} ORDER BY created_at DESC")
    IPage<HopeTreeGrowthHistory> findByPatientIdOrderByCreatedAtDesc(Page<HopeTreeGrowthHistory> page,
                                                                      @Param("patientId") String patientId);

    @Select("SELECT COALESCE(SUM(exp_amount), 0) FROM hope_tree_growth_history " +
            "WHERE patient_id = #{patientId} AND created_at >= #{dayStart} AND created_at < #{dayEnd}")
    int sumTodayExp(@Param("patientId") String patientId,
                    @Param("dayStart") LocalDateTime dayStart,
                    @Param("dayEnd") LocalDateTime dayEnd);

    @Select("SELECT COALESCE(SUM(exp_amount), 0) FROM hope_tree_growth_history WHERE patient_id = #{patientId}")
    int sumTotalExpByPatientId(String patientId);
}
