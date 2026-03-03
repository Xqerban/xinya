package com.xinya.dtx.system.mapper;

import com.xinya.dtx.system.entity.SyncItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SyncItemMapper extends JpaRepository<SyncItem, Long> {

    /** 按 clientId 查询（幂等去重的核心查询） */
    Optional<SyncItem> findByClientId(String clientId);

    /** 按 clientId 判断是否存在 */
    boolean existsByClientId(String clientId);

    /** 查询某患者/设备的待处理同步项 */
    List<SyncItem> findByPatientIdAndStatusOrderByClientCreatedAtAsc(
            String patientId, String status);

    /** 查询某设备的历史同步记录（状态检查接口） */
    @Query("SELECT s FROM SyncItem s WHERE s.deviceId = :deviceId " +
           "AND s.patientId = :patientId ORDER BY s.createdAt DESC")
    List<SyncItem> findRecentByDevice(@Param("deviceId") String deviceId,
                                      @Param("patientId") String patientId,
                                      org.springframework.data.domain.Pageable pageable);

    /** 标记处理成功 */
    @Modifying
    @Query("UPDATE SyncItem s SET s.status = 'success', s.serverId = :serverId, " +
           "s.processedAt = :now WHERE s.clientId = :clientId")
    void markSuccess(@Param("clientId") String clientId,
                     @Param("serverId") String serverId,
                     @Param("now") LocalDateTime now);

    /** 标记处理失败 */
    @Modifying
    @Query("UPDATE SyncItem s SET s.status = 'failed', s.errorCode = :code, " +
           "s.errorMessage = :msg, s.processedAt = :now WHERE s.clientId = :clientId")
    void markFailed(@Param("clientId") String clientId,
                    @Param("code") Integer code,
                    @Param("msg") String msg,
                    @Param("now") LocalDateTime now);

    /** 查询某患者服务端是否有等待下发的数据（离线同步状态检查） */
    long countByPatientIdAndStatus(String patientId, String status);
}
