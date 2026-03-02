package com.xinya.dtx.mapper;

import com.xinya.dtx.entity.ClinicalStageHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClinicalStageHistoryMapper extends JpaRepository<ClinicalStageHistory, Long> {

    /** 查询患者全部流转历史（按时间升序，用于展示路径） */
    List<ClinicalStageHistory> findByPatientIdOrderByCreatedAtAsc(String patientId);

    /** 查询患者全部流转历史（按时间降序，用于列表展示） */
    List<ClinicalStageHistory> findByPatientIdOrderByCreatedAtDesc(String patientId);

    /** 查询患者最近一次流转记录（获取当前阶段开始日期用） */
    Optional<ClinicalStageHistory> findTopByPatientIdOrderByCreatedAtDesc(String patientId);

    /** 查询患者流转到某阶段的记录 */
    Optional<ClinicalStageHistory> findTopByPatientIdAndToStageOrderByCreatedAtDesc(
            String patientId, String toStage);

    /** 统计患者经历的总阶段数 */
    long countByPatientId(String patientId);

    /** 查询某操作人执行的全部流转记录 */
    List<ClinicalStageHistory> findByOperatorIdOrderByCreatedAtDesc(String operatorId);

    /** 查询患者在指定阶段待了多少天（取最近一次进入该阶段的记录） */
    @Query("SELECT h.daysInStage FROM ClinicalStageHistory h " +
           "WHERE h.patientId = :patientId AND h.fromStage = :stage " +
           "ORDER BY h.createdAt DESC")
    List<Integer> findDaysInStage(@Param("patientId") String patientId,
                                  @Param("stage") String stage);
}
