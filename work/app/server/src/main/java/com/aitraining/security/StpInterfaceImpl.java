package com.aitraining.security;

import cn.dev33.satoken.stp.StpInterface;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.aitraining.module.system.entity.SysRole;
import com.aitraining.module.system.entity.SysUserRole;
import com.aitraining.module.system.mapper.SysRoleMapper;
import com.aitraining.module.system.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Sa-Token 角色/权限数据源（RBAC）
 */
@Component
@RequiredArgsConstructor
public class StpInterfaceImpl implements StpInterface {

    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        List<String> roles = getRoleList(loginId, loginType);
        List<String> permissions = new ArrayList<>();
        for (String role : roles) {
            switch (role) {
                case LoginHelper.ROLE_ADMIN -> permissions.addAll(Arrays.asList(
                        "user:manage", "skill:manage", "plan:manage", "plan:audit",
                        "exam:manage", "ai:gen", "stat:view"));
                case LoginHelper.ROLE_LECTURER -> permissions.addAll(Arrays.asList(
                        "skill:view", "plan:manage", "plan:audit", "course:manage",
                        "exam:manage", "question:manage", "ai:gen", "stat:view", "progress:view"));
                case LoginHelper.ROLE_EMPLOYEE -> permissions.addAll(Arrays.asList(
                        "skill:self", "plan:self", "progress:report", "exam:take", "ai:qa"));
                default -> {
                }
            }
        }
        return permissions;
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        Long userId = Long.valueOf(loginId.toString());
        List<SysUserRole> links = userRoleMapper.selectList(
                new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        if (links.isEmpty()) {
            return List.of();
        }
        List<Long> roleIds = links.stream().map(SysUserRole::getRoleId).toList();
        return roleMapper.selectBatchIds(roleIds).stream().map(SysRole::getRoleCode).toList();
    }
}
