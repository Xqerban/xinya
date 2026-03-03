package com.xinya.dtx.user.mapper;

import com.xinya.dtx.user.entity.User;
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

    /** 按手机号查询（手机号登录用） */
    Optional<User> findByPhone(String phone);

    /** 手机号是否已存在 */
    boolean existsByPhone(String phone);

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

    /** 按 refreshToken 查询用户（刷新登录态用） */
    Optional<User> findByRefreshToken(String refreshToken);

    /** 清理已过期的 refreshToken（定时任务用） */
    @Modifying
    @Query("UPDATE User u SET u.refreshToken = null, u.refreshTokenExpiresAt = null " +
           "WHERE u.refreshTokenExpiresAt IS NOT NULL AND u.refreshTokenExpiresAt < :now")
    int clearExpiredRefreshTokens(@Param("now") LocalDateTime now);
}
