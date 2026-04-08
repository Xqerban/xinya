package com.xinya.ops.audit.mapper;

import com.xinya.ops.audit.entity.OpsAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface OpsAuditLogMapper extends JpaRepository<OpsAuditLog, Long> {

    @Query("SELECT l FROM OpsAuditLog l WHERE " +
           "(:operatorId IS NULL OR l.operatorId = :operatorId) AND " +
           "(:action IS NULL OR l.action = :action) AND " +
           "(:targetType IS NULL OR l.targetType = :targetType) AND " +
           "(:targetId IS NULL OR l.targetId = :targetId) AND " +
           "(:startTime IS NULL OR l.createdAt >= :startTime) AND " +
           "(:endTime IS NULL OR l.createdAt <= :endTime) " +
           "ORDER BY l.createdAt DESC")
    Page<OpsAuditLog> findByFilters(
            @Param("operatorId") String operatorId,
            @Param("action") String action,
            @Param("targetType") String targetType,
            @Param("targetId") String targetId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            Pageable pageable);
}
