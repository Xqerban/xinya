package com.xinya.dtx.system.mapper;

import com.xinya.dtx.system.entity.EducationProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface EducationProgressMapper extends JpaRepository<EducationProgress, Long> {

    /** 查询患者某条内容的观看进度 */
    Optional<EducationProgress> findByPatientIdAndContentId(String patientId, String contentId);

    /** 查询患者全部进度列表 */
    List<EducationProgress> findByPatientIdOrderByLastWatchedAtDesc(String patientId);

    /** 查询患者已完成的内容ID列表（供 Agent 接口传入 viewedContentIds） */
    @Query("SELECT p.contentId FROM EducationProgress p " +
           "WHERE p.patientId = :patientId AND p.completed = true")
    List<String> findCompletedContentIdsByPatientId(@Param("patientId") String patientId);

    /** 查询患者所有已观看过的内容ID（包含未完成，同样不重复推荐） */
    @Query("SELECT p.contentId FROM EducationProgress p WHERE p.patientId = :patientId")
    List<String> findViewedContentIdsByPatientId(@Param("patientId") String patientId);

    /** 统计患者已完成内容数量 */
    long countByPatientIdAndCompletedTrue(String patientId);

    /** 统计患者总观看秒数 */
    @Query("SELECT COALESCE(SUM(p.watchedSeconds), 0) FROM EducationProgress p " +
           "WHERE p.patientId = :patientId")
    int sumWatchedSecondsByPatientId(@Param("patientId") String patientId);

    /** 全局平均完成率（用于驾驶舱学习统计），按记录维度近似计算 */
    @Query("SELECT COALESCE(AVG(CASE WHEN p.completed = true THEN 1.0 ELSE 0.0 END), 0.0) " +
           "FROM EducationProgress p")
    Double avgCompletionRate();

    /** 全局平均观看时长（秒），按记录维度近似计算 */
    @Query("SELECT COALESCE(AVG(p.watchedSeconds), 0.0) FROM EducationProgress p")
    Double avgWatchSeconds();

    /** 更新观看进度（upsert 由 Service 层先 findByPatientIdAndContentId 再 save 处理） */
    @Modifying
    @Query("UPDATE EducationProgress p SET " +
           "p.watchedSeconds = :seconds, " +
           "p.lastWatchedAt = :now, " +
           "p.updatedAt = :now " +
           "WHERE p.patientId = :patientId AND p.contentId = :contentId")
    void updateWatchedSeconds(@Param("patientId") String patientId,
                              @Param("contentId") String contentId,
                              @Param("seconds") int seconds,
                              @Param("now") LocalDateTime now);

    /** 标记完成并锁定奖励发放标志（防重） */
    @Modifying
    @Query("UPDATE EducationProgress p SET p.completed = true, p.rewardGiven = true, " +
           "p.updatedAt = CURRENT_TIMESTAMP WHERE p.patientId = :patientId " +
           "AND p.contentId = :contentId AND p.rewardGiven = false")
    int markCompletedAndReward(@Param("patientId") String patientId,
                               @Param("contentId") String contentId);

    /** 查询患者某内容是否已领取过奖励 */
    @Query("SELECT p.rewardGiven FROM EducationProgress p " +
           "WHERE p.patientId = :patientId AND p.contentId = :contentId")
    Optional<Boolean> isRewardGiven(@Param("patientId") String patientId,
                                    @Param("contentId") String contentId);
}
