package com.xinya.dtx.hopetree.mapper;

import com.xinya.dtx.hopetree.entity.HopeTreeGrowthHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface HopeTreeGrowthHistoryMapper extends JpaRepository<HopeTreeGrowthHistory, Long> {

    /** 分页查询患者成长历史（成长日记） */
    Page<HopeTreeGrowthHistory> findByPatientIdOrderByCreatedAtDesc(
            String patientId, Pageable pageable);

    /** 查询患者今日已获得的经验值总量（防止单日超上限） */
    @Query("SELECT COALESCE(SUM(h.expAmount), 0) FROM HopeTreeGrowthHistory h " +
           "WHERE h.patientId = :patientId AND h.createdAt >= :dayStart AND h.createdAt < :dayEnd")
    int sumTodayExp(@Param("patientId") String patientId,
                    @Param("dayStart") LocalDateTime dayStart,
                    @Param("dayEnd") LocalDateTime dayEnd);

    /** 查询患者某来源类型的累计经验总量 */
    @Query("SELECT COALESCE(SUM(h.expAmount), 0) FROM HopeTreeGrowthHistory h " +
           "WHERE h.patientId = :patientId AND h.growthSource = :source")
    int sumExpBySource(@Param("patientId") String patientId,
                       @Param("source") String source);

    /** 查询患者历史累计经验总量 */
    @Query("SELECT COALESCE(SUM(h.expAmount), 0) FROM HopeTreeGrowthHistory h " +
           "WHERE h.patientId = :patientId")
    int sumTotalExpByPatientId(@Param("patientId") String patientId);

    /** 查询升级事件列表 */
    Page<HopeTreeGrowthHistory> findByPatientIdAndLevelUpTrueOrderByCreatedAtDesc(
            String patientId, Pageable pageable);

    /** 判断某来源引用是否已发放过奖励（防重，如宣教完成只奖励一次） */
    boolean existsByPatientIdAndSourceRefId(String patientId, String sourceRefId);
}
