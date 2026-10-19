package com.aitraining.security;

import cn.dev33.satoken.stp.StpUtil;
import com.aitraining.common.BizException;
import com.aitraining.module.system.entity.SysUser;

/**
 * 当前登录用户上下文
 */
public class LoginHelper {

    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_LECTURER = "LECTURER";
    public static final String ROLE_EMPLOYEE = "EMPLOYEE";

    public static Long userId() {
        return StpUtil.getLoginIdAsLong();
    }

    public static String roleCode() {
        return (String) StpUtil.getSession().get("roleCode");
    }

    public static boolean isAdmin() {
        return StpUtil.hasRole(ROLE_ADMIN);
    }

    public static boolean isLecturer() {
        return StpUtil.hasRole(ROLE_LECTURER);
    }

    public static boolean isEmployee() {
        return StpUtil.hasRole(ROLE_EMPLOYEE);
    }

    /** 管理员或讲师：具备培训管理职责 */
    public static boolean isManager() {
        return isAdmin() || isLecturer();
    }

    public static void requireManager() {
        if (!isManager()) {
            throw new BizException(403, "该操作仅管理员或讲师可用");
        }
    }

    public static void requireAdmin() {
        if (!isAdmin()) {
            throw new BizException(403, "该操作仅管理员可用");
        }
    }

    public static void requireSelfOrManager(Long targetUserId) {
        if (!isManager() && !userId().equals(targetUserId)) {
            throw new BizException(403, "只能查看或操作自己的数据");
        }
    }
}
