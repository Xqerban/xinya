package com.xinya.dtx.mapper;

import com.xinya.dtx.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface UserMapper extends JpaRepository<User, String> {

    /** 按用户名查询（登录用） */
    Optional<User> findByUsername(String username);

    /** 用户名是否已存在 */
    boolean existsByUsername(String username);

    /** 按角色查询（支持分页） */
    Page<User> findByRole(String role, Pageable pageable);

    /** 按角色和启用状态查询 */
    Page<User> findByRoleAndEnabled(String role, Boolean enabled, Pageable pageable);

    /** 分页查询全部（不区分角色） */
    Page<User> findByEnabled(Boolean enabled, Pageable pageable);

    /** 更新最后登录时间 */
    @Modifying
    @Query("UPDATE User u SET u.lastLoginAt = :time WHERE u.id = :id")
    void updateLastLoginAt(@Param("id") String id, @Param("time") LocalDateTime time);

    /** 更新密码 */
    @Modifying
    @Query("UPDATE User u SET u.passwordHash = :hash WHERE u.id = :id")
    void updatePassword(@Param("id") String id, @Param("hash") String hash);

    /** 启用/禁用用户 */
    @Modifying
    @Query("UPDATE User u SET u.enabled = :enabled WHERE u.id = :id")
    void updateEnabled(@Param("id") String id, @Param("enabled") Boolean enabled);
}
