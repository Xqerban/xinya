package com.xinya.dtx.mapper;

import com.xinya.dtx.entity.RobotBindCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface RobotBindCodeMapper extends JpaRepository<RobotBindCode, Long> {

    /** 校验绑定码：未使用且未过期 */
    @Query("SELECT b FROM RobotBindCode b WHERE b.patientId = :patientId " +
           "AND b.bindCode = :code AND b.used = false AND b.expiresAt > :now")
    Optional<RobotBindCode> findValidCode(@Param("patientId") String patientId,
                                          @Param("code") String code,
                                          @Param("now") LocalDateTime now);

    /** 将绑定码标记为已使用 */
    @Modifying
    @Query("UPDATE RobotBindCode b SET b.used = true WHERE b.id = :id")
    void markUsed(@Param("id") Long id);

    /** 删除过期未使用的绑定码（清理任务用） */
    @Modifying
    @Query("DELETE FROM RobotBindCode b WHERE b.used = false AND b.expiresAt < :now")
    int deleteExpired(@Param("now") LocalDateTime now);

    /** 查询患者最新一条有效绑定码（判断是否已有码未使用） */
    @Query("SELECT b FROM RobotBindCode b WHERE b.patientId = :patientId " +
           "AND b.used = false AND b.expiresAt > :now ORDER BY b.createdAt DESC")
    Optional<RobotBindCode> findLatestValidByPatientId(@Param("patientId") String patientId,
                                                       @Param("now") LocalDateTime now);
}
