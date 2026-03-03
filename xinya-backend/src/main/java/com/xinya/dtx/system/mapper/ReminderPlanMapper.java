package com.xinya.dtx.system.mapper;

import com.xinya.dtx.system.entity.ReminderPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReminderPlanMapper extends JpaRepository<ReminderPlan, Long> {

    /** 查询患者某天的全部提醒计划（按优先级升序） */
    List<ReminderPlan> findByPatientIdAndPlanDateOrderByPriorityAsc(
            String patientId, LocalDate planDate);

    /** 查询患者某天指定状态的提醒 */
    List<ReminderPlan> findByPatientIdAndPlanDateAndStatusOrderByPriorityAsc(
            String patientId, LocalDate planDate, String status);

    /** 幂等检查：Patient+Date+ReminderId 是否已存在 */
    boolean existsByPatientIdAndPlanDateAndReminderId(
            String patientId, LocalDate planDate, String reminderId);

    /** 查询某天待推送的所有计划（定时任务扫描用，按推送时间排序） */
    @Query("SELECT r FROM ReminderPlan r WHERE r.planDate = :date " +
           "AND r.status = 'pending' ORDER BY r.scheduledTime ASC")
    List<ReminderPlan> findPendingByDate(@Param("date") LocalDate date);

    /** 查询某患者某天某时间点前待推送的计划（精确触发用） */
    @Query("SELECT r FROM ReminderPlan r WHERE r.patientId = :patientId " +
           "AND r.planDate = :date AND r.status = 'pending' " +
           "AND r.scheduledTime <= :time ORDER BY r.scheduledTime ASC")
    List<ReminderPlan> findDuePlans(@Param("patientId") String patientId,
                                    @Param("date") LocalDate date,
                                    @Param("time") String time);

    /** 标记为已推送 */
    @Modifying
    @Query("UPDATE ReminderPlan r SET r.status = 'sent', r.sentAt = :now " +
           "WHERE r.id = :id AND r.status = 'pending'")
    int markSent(@Param("id") Long id, @Param("now") LocalDateTime now);

    /** 标记为已完成（患者互动后触发） */
    @Modifying
    @Query("UPDATE ReminderPlan r SET r.status = 'completed' " +
           "WHERE r.id = :id AND r.status = 'sent'")
    int markCompleted(@Param("id") Long id);

    /** 查询患者某天已完成的提醒数量（用于奖励结算） */
    long countByPatientIdAndPlanDateAndStatus(String patientId, LocalDate planDate, String status);

    /** 查询关联某宣教内容的所有待推送计划（内容下架时需取消） */
    List<ReminderPlan> findByContentIdAndStatus(String contentId, String status);
}
