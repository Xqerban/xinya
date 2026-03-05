package com.xinya.dtx.agent.mapper;

import com.xinya.dtx.agent.entity.CrisisKeyword;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CrisisKeywordMapper extends JpaRepository<CrisisKeyword, Long> {

    /** 查询所有启用的关键词（按危机级别分组，应用启动时可缓存） */
    List<CrisisKeyword> findByIsActiveTrueOrderByCrisisLevelDesc();

    /** 按级别查询启用关键词 */
    List<CrisisKeyword> findByCrisisLevelAndIsActiveTrue(String crisisLevel);

    /** 关键词是否已存在 */
    boolean existsByKeyword(String keyword);

    /** 停用关键词 */
    @Modifying
    @Query("UPDATE CrisisKeyword k SET k.isActive = false WHERE k.id = :id")
    void deactivate(@Param("id") Long id);

    /** 查询所有关键词（含停用，运维管理列表） */
    List<CrisisKeyword> findAllByOrderByCrisisLevelDescKeywordAsc();

    /**
     * 文本中是否命中危机关键词（原生SQL，IN子句匹配）。
     * 由于关键词数量不多，Service 层直接遍历列表做 contains 判断更灵活，
     * 此查询仅作为备用方案。
     */
    @Query(value = "SELECT * FROM crisis_keywords WHERE is_active = 1 " +
                   "AND :text LIKE CONCAT('%', keyword, '%') ORDER BY crisis_level DESC LIMIT 1",
           nativeQuery = true)
    List<CrisisKeyword> findMatchedInText(@Param("text") String text);
}
