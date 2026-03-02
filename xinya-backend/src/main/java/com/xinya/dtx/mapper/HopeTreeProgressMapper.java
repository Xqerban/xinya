package com.xinya.dtx.mapper;

import com.xinya.dtx.entity.HopeTreeProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface HopeTreeProgressMapper extends JpaRepository<HopeTreeProgress, Long> {

    /** 按患者ID查询（每个患者唯一） */
    Optional<HopeTreeProgress> findByPatientId(String patientId);

    /** 患者是否已有进度记录 */
    boolean existsByPatientId(String patientId);

    /**
     * 增加经验值并自动处理升级逻辑（乐观更新，Service层负责升级判断）。
     * 升级逻辑复杂时建议在 Service 层用悲观锁或 select-for-update 保证一致性。
     */
    @Modifying
    @Query("UPDATE HopeTreeProgress h SET " +
           "h.currentExp = h.currentExp + :expAmount, " +
           "h.totalExp = h.totalExp + :expAmount, " +
           "h.lastGrowthDate = :now, " +
           "h.updatedAt = :now " +
           "WHERE h.patientId = :patientId")
    void addExp(@Param("patientId") String patientId,
                @Param("expAmount") int expAmount,
                @Param("now") LocalDateTime now);

    /** 升级后同步写入新等级、新经验（currentExp 重置为溢出余量）、下一级所需经验 */
    @Modifying
    @Query("UPDATE HopeTreeProgress h SET " +
           "h.currentLevel = :newLevel, " +
           "h.currentExp = :newExp, " +
           "h.nextLevelExp = :nextLevelExp, " +
           "h.totalGrowthDays = h.totalGrowthDays + 1, " +
           "h.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE h.patientId = :patientId")
    void updateAfterLevelUp(@Param("patientId") String patientId,
                            @Param("newLevel") int newLevel,
                            @Param("newExp") int newExp,
                            @Param("nextLevelExp") int nextLevelExp);

    /** 累计成长天数 +1（每日首次获得经验时调用） */
    @Modifying
    @Query("UPDATE HopeTreeProgress h SET h.totalGrowthDays = h.totalGrowthDays + 1 " +
           "WHERE h.patientId = :patientId")
    void incrementGrowthDays(@Param("patientId") String patientId);
}
