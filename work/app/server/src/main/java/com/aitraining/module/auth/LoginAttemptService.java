package com.aitraining.module.auth;

import com.aitraining.common.BizException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录失败计数与临时锁定。
 *
 * <p>原实现没有任何失败次数限制，可以无限次尝试口令。这里按「账号」计数：
 * 连续 {@value #MAX_FAIL} 次失败锁定 {@value #LOCK_MINUTES} 分钟，登录成功即清零。
 *
 * <p>单机内存实现：毕设为单实例部署，够用；若将来多实例，需要换成 Redis 计数。
 * 键只取用户名而不叠加 IP，是因为同一间机房/校园网出口 IP 相同，按 IP 会误伤。
 */
@Component
public class LoginAttemptService {

    private static final int MAX_FAIL = 5;
    private static final long LOCK_MINUTES = 10;
    /** 超过这个量级说明有大量随机用户名在刷，先清理过期项避免无限增长 */
    private static final int CLEAN_THRESHOLD = 5000;

    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    /** 登录前调用：仍在锁定期内则直接拒绝 */
    public void checkBlocked(String username) {
        if (username == null || username.isBlank()) {
            return;
        }
        Attempt a = attempts.get(username);
        if (a == null) {
            return;
        }
        synchronized (a) {
            if (a.lockUntil == null) {
                return;
            }
            LocalDateTime now = LocalDateTime.now();
            if (a.lockUntil.isAfter(now)) {
                long seconds = Duration.between(now, a.lockUntil).getSeconds();
                long minutes = Math.max(1, (seconds + 59) / 60);
                throw new BizException(429, "密码错误次数过多，请 " + minutes + " 分钟后再试");
            }
            // 锁定期已过，重新计数
            a.lockUntil = null;
            a.failCount = 0;
        }
    }

    /** 登录失败调用 */
    public void onFailure(String username) {
        if (username == null || username.isBlank()) {
            return;
        }
        if (attempts.size() > CLEAN_THRESHOLD) {
            cleanExpired();
        }
        Attempt a = attempts.computeIfAbsent(username, k -> new Attempt());
        synchronized (a) {
            a.failCount++;
            if (a.failCount >= MAX_FAIL) {
                a.lockUntil = LocalDateTime.now().plusMinutes(LOCK_MINUTES);
            }
        }
    }

    /** 登录成功调用 */
    public void onSuccess(String username) {
        if (username != null) {
            attempts.remove(username);
        }
    }

    /** 剩余可尝试次数，仅用于日志与自测 */
    int remaining(String username) {
        Attempt a = attempts.get(username);
        if (a == null) {
            return MAX_FAIL;
        }
        synchronized (a) {
            return Math.max(0, MAX_FAIL - a.failCount);
        }
    }

    private void cleanExpired() {
        LocalDateTime now = LocalDateTime.now();
        attempts.entrySet().removeIf(e -> {
            Attempt a = e.getValue();
            synchronized (a) {
                return a.lockUntil == null ? a.failCount == 0 : a.lockUntil.isBefore(now.minusMinutes(LOCK_MINUTES));
            }
        });
    }

    private static class Attempt {
        private int failCount;
        private LocalDateTime lockUntil;
    }
}
