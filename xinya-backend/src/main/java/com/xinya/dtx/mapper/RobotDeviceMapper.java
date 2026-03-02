package com.xinya.dtx.mapper;

import com.xinya.dtx.entity.RobotDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface RobotDeviceMapper extends JpaRepository<RobotDevice, Long> {

    /** 按设备序列号查询 */
    Optional<RobotDevice> findByDeviceId(String deviceId);

    /** 按患者ID查询绑定设备 */
    Optional<RobotDevice> findByPatientId(String patientId);

    /** 设备序列号是否已存在 */
    boolean existsByDeviceId(String deviceId);

    /** 心跳上报：更新在线状态、心跳时间、网络状态、电量 */
    @Modifying
    @Query("UPDATE RobotDevice d SET d.onlineStatus = 'ONLINE', " +
           "d.lastHeartbeatAt = :time, d.networkStatus = :networkStatus, " +
           "d.batteryLevel = :battery, d.appVersion = :version, d.updatedAt = :time " +
           "WHERE d.deviceId = :deviceId")
    void updateHeartbeat(@Param("deviceId") String deviceId,
                         @Param("time") LocalDateTime time,
                         @Param("networkStatus") String networkStatus,
                         @Param("battery") Integer battery,
                         @Param("version") String version);

    /** 将超时未心跳的设备标记为 OFFLINE（由定时任务调用） */
    @Modifying
    @Query("UPDATE RobotDevice d SET d.onlineStatus = 'OFFLINE' " +
           "WHERE d.onlineStatus = 'ONLINE' AND d.lastHeartbeatAt < :threshold")
    int markOfflineByHeartbeatBefore(@Param("threshold") LocalDateTime threshold);

    /** 更新绑定的患者 */
    @Modifying
    @Query("UPDATE RobotDevice d SET d.patientId = :patientId, " +
           "d.deviceTokenHash = :tokenHash, d.tokenExpiresAt = :expiresAt, d.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE d.deviceId = :deviceId")
    void updateBinding(@Param("deviceId") String deviceId,
                       @Param("patientId") String patientId,
                       @Param("tokenHash") String tokenHash,
                       @Param("expiresAt") LocalDateTime expiresAt);
}
