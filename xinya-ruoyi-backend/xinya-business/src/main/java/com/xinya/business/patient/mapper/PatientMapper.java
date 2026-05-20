package com.xinya.business.patient.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xinya.business.patient.entity.Patient;
import org.apache.ibatis.annotations.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Mapper
public interface PatientMapper extends BaseMapper<Patient> {

    @Select("SELECT COUNT(1) FROM patients")
    long countTotal();

    @Select("SELECT COUNT(1) FROM patients WHERE DATE(created_at) = #{today}")
    long countNewToday(@Param("today") LocalDate today);

    @Select("SELECT COUNT(1) FROM patients WHERE stage = #{stage}")
    long countByStageValue(String stage);

    @Select("SELECT COUNT(1) FROM patients WHERE psych_energy < #{threshold}")
    long countByPsychEnergyLessThan(int threshold);

    @Select("SELECT AVG(psych_energy) FROM patients")
    Double avgPsychEnergy();

    @Select("SELECT COUNT(DISTINCT pr.patient_id) FROM pro_records pr WHERE pr.record_date = CURDATE()")
    long countTodayCheckIn();

    @Update("UPDATE patients SET psych_energy = LEAST(100, GREATEST(0, psych_energy + #{delta})), " +
            "updated_at = NOW() WHERE id = #{id}")
    void addPsychEnergy(@Param("id") String id, @Param("delta") int delta);

    @Update("UPDATE patients SET tree_level = #{level}, updated_at = NOW() WHERE id = #{id}")
    void updateTreeLevel(@Param("id") String id, @Param("level") int level);

    @Update("UPDATE patients SET stage = #{stage}, stage_start_date = CURDATE(), updated_at = NOW() WHERE id = #{id}")
    void updateStage(@Param("id") String id, @Param("stage") String stage);

    @Select("SELECT stage, COUNT(*) as count FROM patients GROUP BY stage")
    List<Map<String, Object>> countByStage();

    @Select("<script>" +
            "SELECT stage, COUNT(*) as count FROM patients GROUP BY stage" +
            "</script>")
    List<Map<String, Object>> countGroupByStage();

    IPage<Patient> pageByStageAndKeyword(Page<Patient> page,
                                          @Param("stage") String stage,
                                          @Param("keyword") String keyword);
}
