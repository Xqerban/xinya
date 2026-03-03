package com.xinya.dtx.patient.mapper;

import com.xinya.dtx.patient.entity.Patient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public interface PatientMapper extends JpaRepository<Patient, String> {

    /** 按阶段筛选（分页） */
    Page<Patient> findByStage(String stage, Pageable pageable);

    /** 按姓名模糊搜索（分页） */
    Page<Patient> findByNameContaining(String keyword, Pageable pageable);

    /** 按阶段 + 姓名模糊搜索（分页） */
    Page<Patient> findByStageAndNameContaining(String stage, String keyword, Pageable pageable);

    /** 获取所有患者（分页，无筛选） */
    Page<Patient> findAll(Pageable pageable);

    /** 按阶段统计人数（驾驶舱用） */
    long countByStage(String stage);

    /** 统计心理能量低于指定值的患者数（驾驶舱告警用） */
    long countByPsychEnergyLessThan(int threshold);

    /** 按阶段列表查询（批量） */
    List<Patient> findByStageIn(List<String> stages);

    /** 各阶段分布统计 */
    @Query("SELECT p.stage AS stage, COUNT(p) AS count FROM Patient p GROUP BY p.stage")
    List<Map<String, Object>> countGroupByStage();

    /** 全部患者平均心理能量 */
    @Query("SELECT AVG(p.psychEnergy) FROM Patient p")
    Double avgPsychEnergy();

    /** 更新心理能量（使用 +delta 而非绝对值，保证并发安全） */
    @Modifying
    @Query("UPDATE Patient p SET p.psychEnergy = LEAST(100, GREATEST(0, p.psychEnergy + :delta)), " +
           "p.updatedAt = CURRENT_TIMESTAMP WHERE p.id = :id")
    void addPsychEnergy(@Param("id") String id, @Param("delta") int delta);

    /** 更新希望之树等级（冗余字段同步） */
    @Modifying
    @Query("UPDATE Patient p SET p.treeLevel = :level, p.updatedAt = CURRENT_TIMESTAMP WHERE p.id = :id")
    void updateTreeLevel(@Param("id") String id, @Param("level") int level);

    /** 更新临床阶段及阶段开始日期 */
    @Modifying
    @Query("UPDATE Patient p SET p.stage = :stage, p.stageStartDate = CURRENT_DATE, " +
           "p.updatedAt = CURRENT_TIMESTAMP WHERE p.id = :id")
    void updateStage(@Param("id") String id, @Param("stage") String stage);

    /** 今日已打卡患者数（联查 pro_records） */
    @Query(value = "SELECT COUNT(DISTINCT pr.patient_id) FROM pro_records pr " +
                   "WHERE pr.record_date = CURDATE()", nativeQuery = true)
    long countTodayCheckIn();
}
