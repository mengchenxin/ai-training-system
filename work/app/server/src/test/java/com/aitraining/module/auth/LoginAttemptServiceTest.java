package com.aitraining.module.auth;

import com.aitraining.common.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 登录失败限流：验证阈值、锁定与成功清零。
 */
class LoginAttemptServiceTest {

    private final LoginAttemptService service = new LoginAttemptService();

    @Test
    @DisplayName("连续 5 次失败后锁定，第 6 次直接拒绝")
    void lockAfterFiveFailures() {
        String user = "emp001";
        for (int i = 0; i < 5; i++) {
            assertDoesNotThrow(() -> service.checkBlocked(user), "第 " + (i + 1) + " 次前不应被锁");
            service.onFailure(user);
        }
        BizException e = assertThrows(BizException.class, () -> service.checkBlocked(user));
        assertEquals(429, e.getCode());
        assertTrue(e.getMessage().contains("次数过多"));
    }

    @Test
    @DisplayName("未达阈值不锁定")
    void notLockedBelowThreshold() {
        String user = "emp002";
        for (int i = 0; i < 4; i++) {
            service.onFailure(user);
        }
        assertDoesNotThrow(() -> service.checkBlocked(user));
        assertEquals(1, service.remaining(user));
    }

    @Test
    @DisplayName("登录成功后计数清零")
    void resetOnSuccess() {
        String user = "emp003";
        for (int i = 0; i < 4; i++) {
            service.onFailure(user);
        }
        service.onSuccess(user);
        assertEquals(5, service.remaining(user));
        assertDoesNotThrow(() -> service.checkBlocked(user));
    }

    @Test
    @DisplayName("不同账号互不影响")
    void isolatedPerUser() {
        for (int i = 0; i < 5; i++) {
            service.onFailure("attacker");
        }
        assertThrows(BizException.class, () -> service.checkBlocked("attacker"));
        assertDoesNotThrow(() -> service.checkBlocked("emp004"));
    }
}
