package com.xinya.business.clinical.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xinya.business.clinical.entity.ClinicalStageHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ClinicalStageHistoryMapper extends BaseMapper<ClinicalStageHistory> {

    @Select("SELECT * FROM clinical_stage_history WHERE patient_id = #{patientId} ORDER BY created_at DESC")
    List<ClinicalStageHistory> findByPatientIdOrderByCreatedAtDesc(String patientId);
}
