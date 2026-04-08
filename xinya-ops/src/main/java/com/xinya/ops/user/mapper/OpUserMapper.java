package com.xinya.ops.user.mapper;

import com.xinya.ops.user.entity.OpUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public interface OpUserMapper extends JpaRepository<OpUser, String> {

    Optional<OpUser> findByUsername(String username);

    Optional<OpUser> findByPhone(String phone);

    boolean existsByUsername(String username);

    boolean existsByPhone(String phone);

    @Query("SELECT u FROM OpUser u WHERE (:role IS NULL OR u.role = :role)")
    Page<OpUser> findByRoleOrAll(@Param("role") String role, Pageable pageable);

    @Modifying
    @Transactional
    @Query("UPDATE OpUser u SET u.enabled = false WHERE u.id = :id")
    int deactivate(@Param("id") String id);

    @Modifying
    @Transactional
    @Query("DELETE FROM OpUser u WHERE u.id = :id")
    int hardDelete(@Param("id") String id);
}
