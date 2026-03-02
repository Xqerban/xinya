package com.xinya.dtx.mapper;

import com.xinya.dtx.entity.ProQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProQuestionMapper extends JpaRepository<ProQuestion, String> {

    /** 查询指定阶段 + 全阶段(ALL) 的启用题目，按排序升序 */
    @Query("SELECT q FROM ProQuestion q WHERE q.isActive = true " +
           "AND (q.stage = :stage OR q.stage = 'ALL') ORDER BY q.sortOrder ASC")
    List<ProQuestion> findActiveByStage(@Param("stage") String stage);

    /** 查询全部启用题目（运维管理用） */
    List<ProQuestion> findByIsActiveTrueOrderBySortOrderAsc();

    /** 查询全部题目（含停用，运维管理用） */
    List<ProQuestion> findAllByOrderBySortOrderAsc();

    /** 查询某症状Key对应的题目（PRO打卡后触发症状阈值判断用） */
    List<ProQuestion> findBySymptomKeyAndIsActiveTrue(String symptomKey);

    /** 查询关联了某症状的全部启用题目 */
    @Query("SELECT q FROM ProQuestion q WHERE q.isActive = true " +
           "AND q.symptomKey IS NOT NULL ORDER BY q.sortOrder ASC")
    List<ProQuestion> findAllActiveWithSymptomKey();
}
