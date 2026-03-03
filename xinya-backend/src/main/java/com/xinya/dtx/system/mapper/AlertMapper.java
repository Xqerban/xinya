package com.xinya.dtx.system.mapper;

import com.xinya.dtx.system.entity.Alert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AlertMapper extends JpaRepository<Alert, String> {

    /** 分页查询全部预警（支持 resolved/level/patientId 三个可选过滤条件） */
    @Query("SELECT a FROM Alert a WHERE " +
           "(:resolved IS NULL OR a.resolved = :resolved) " +
           "AND (:level IS NULL OR a.level = :level) " +
           "AND (:patientId IS NULL OR a.patientId = :patientId) " +
           "ORDER BY a.createdAt DESC")
    Page<Alert> findByFilters(@Param("resolved") Boolean resolved,
                              @Param("level") String level,
                              @Param("patientId") String patientId,
                              Pageable pageable);

    /** 查询患者未处理的预警列表 */
    List<Alert> findByPatientIdAndResolvedFalseOrderByCreatedAtDesc(String patientId);

    /** 统计未处理预警总数（驾驶舱角标用） */
    long countByResolvedFalse();

    /** 统计某级别的未处理预警数 */
    long countByLevelAndResolvedFalse(String level);

    /** 统计患者未处理预警数 */
    long countByPatientIdAndResolvedFalse(String patientId);

    /** 处理预警 */
    @Modifying
    @Query("UPDATE Alert a SET a.resolved = true, a.resolvedBy = :resolvedBy, " +
           "a.resolvedNote = :note, a.resolvedAt = :now " +
           "WHERE a.id = :id AND a.resolved = false")
    int resolve(@Param("id") String id,
                @Param("resolvedBy") String resolvedBy,
                @Param("note") String note,
                @Param("now") LocalDateTime now);

    /** 查询驾驶舱概览用的最近未处理预警（限 10 条） */
    @Query("SELECT a FROM Alert a WHERE a.resolved = false ORDER BY a.createdAt DESC")
    List<Alert> findLatestUnresolved(Pageable pageable);

    /** 查询患者在某时间后是否有未处理的 critical 预警（危机升级判断用） */
    boolean existsByPatientIdAndLevelAndResolvedFalseAndCreatedAtAfter(
            String patientId, String level, LocalDateTime since);
}
