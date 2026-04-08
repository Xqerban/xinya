package com.xinya.ops.config.mapper;

import com.xinya.ops.config.entity.OpsCrisisKeyword;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface OpsCrisisKeywordMapper extends JpaRepository<OpsCrisisKeyword, Long> {

    List<OpsCrisisKeyword> findByIsActiveTrueOrderByCrisisLevelDescKeywordAsc();

    Optional<OpsCrisisKeyword> findByKeyword(String keyword);

    boolean existsByKeyword(String keyword);

    @Modifying
    @Transactional
    @Query("UPDATE OpsCrisisKeyword k SET k.isActive = false WHERE k.id = :id")
    void deactivate(@Param("id") Long id);
}
