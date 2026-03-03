package com.xinya.dtx.common.task;

import com.xinya.dtx.robot.mapper.RobotBindCodeMapper;
import com.xinya.dtx.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 简单的定时任务：每天清理一次过期 token / 绑定码
 */
@Component
@RequiredArgsConstructor
public class TokenCleanupTask {

    private final RobotBindCodeMapper robotBindCodeMapper;
    private final UserMapper userMapper;

    /**
     * 每天凌晨 3 点执行一次清理
     */
    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void cleanExpired() {
        LocalDateTime now = LocalDateTime.now();

        // 删除已过期且未使用的绑定码
        robotBindCodeMapper.deleteExpired(now);

        // 清空已过期的 refreshToken
        userMapper.clearExpiredRefreshTokens(now);
    }
}

