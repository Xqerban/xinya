package com.xinya.dtx.mapper;

import com.xinya.dtx.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface AuditLogMapper extends JpaRepository<AuditLog, Long> {

    /** 多条件分页查询（userId/action/startDate/endDate 均可为 null） */
    @Query("SELECT l FROM AuditLog l WHERE " +
           "(:operatorId IS NULL OR l.operatorId = :operatorId) " +
           "AND (:action IS NULL OR l.action = :action) " +
           "AND (:targetType IS NULL OR l.targetType = :targetType) " +
           "AND (:targetId IS NULL OR l.targetId = :targetId) " +
           "AND (:start IS NULL OR l.createdAt >= :start) " +
           "AND (:end IS NULL OR l.createdAt <= :end) " +
           "ORDER BY l.createdAt DESC")
    Page<AuditLog> findByFilters(@Param("operatorId") String operatorId,
                                 @Param("action") String action,
                                 @Param("targetType") String targetType,
                                 @Param("targetId") String targetId,
                                 @Param("start") LocalDateTime start,
                                 @Param("end") LocalDateTime end,
                                 Pageable pageable);

    /** 查询某患者的操作日志（患者删除审计） */
    Page<AuditLog> findByTargetTypeAndTargetIdOrderByCreatedAtDesc(
            String targetType, String targetId, Pageable pageable);

    /** 查询某操作人的操作日志 */
    Page<AuditLog> findByOperatorIdOrderByCreatedAtDesc(String operatorId, Pageable pageable);
}
