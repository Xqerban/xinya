package com.xinya.business.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xinya.business.user.entity.User;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.Optional;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    @Select("SELECT * FROM users WHERE username = #{username} LIMIT 1")
    User findByUsername(String username);

    @Select("SELECT * FROM users WHERE phone = #{phone} LIMIT 1")
    User findByPhone(String phone);

    @Select("SELECT * FROM users WHERE refresh_token = #{token} LIMIT 1")
    User findByRefreshToken(String token);

    @Select("SELECT COUNT(1) > 0 FROM users WHERE username = #{username}")
    boolean existsByUsername(String username);

    @Select("SELECT COUNT(1) > 0 FROM users WHERE phone = #{phone}")
    boolean existsByPhone(String phone);

    @Update("UPDATE users SET last_login_at = #{time} WHERE id = #{id}")
    void updateLastLoginAt(@Param("id") String id, @Param("time") LocalDateTime time);

    @Update("UPDATE users SET password_hash = #{hash} WHERE id = #{id}")
    void updatePassword(@Param("id") String id, @Param("hash") String hash);

    @Update("UPDATE users SET enabled = #{enabled} WHERE id = #{id}")
    void updateEnabled(@Param("id") String id, @Param("enabled") Boolean enabled);

    @Update("UPDATE users SET enabled = false, refresh_token = null, refresh_token_expires_at = null WHERE id = #{id}")
    int deactivateUser(String id);

    @Delete("DELETE FROM users WHERE id = #{id}")
    int hardDeleteById(String id);

    @Update("UPDATE users SET refresh_token = null, refresh_token_expires_at = null " +
            "WHERE refresh_token_expires_at IS NOT NULL AND refresh_token_expires_at < #{now}")
    int clearExpiredRefreshTokens(LocalDateTime now);
}
