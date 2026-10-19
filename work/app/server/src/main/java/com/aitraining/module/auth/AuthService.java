package com.aitraining.module.auth;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.aitraining.common.BizException;
import com.aitraining.module.auth.dto.LoginRequest;
import com.aitraining.module.auth.dto.LoginResult;
import com.aitraining.module.auth.dto.UserInfoVO;
import com.aitraining.module.system.entity.*;
import com.aitraining.module.system.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final SysDeptMapper deptMapper;
    private final PostMapper postMapper;
    private final PasswordEncoder passwordEncoder;

    public LoginResult login(LoginRequest req) {
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, req.getUsername()));
        if (user == null) {
            throw new BizException("账号不存在");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BizException("账号已被停用，请联系管理员");
        }
        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new BizException("密码错误");
        }
        StpUtil.login(user.getId());
        // 角色写入 Session，避免每次请求重复查库
        List<String> roles = roleCodes(user.getId());
        StpUtil.getSession().set("roleCode", roles.isEmpty() ? "EMPLOYEE" : roles.get(0));

        SysUser upd = new SysUser();
        upd.setId(user.getId());
        upd.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(upd);

        return LoginResult.builder()
                .token(StpUtil.getTokenValue())
                .userId(user.getId())
                .username(user.getUsername())
                .realName(user.getRealName())
                .roles(roles)
                .build();
    }

    public void logout() {
        StpUtil.logout();
    }

    public UserInfoVO currentUser() {
        Long uid = StpUtil.getLoginIdAsLong();
        SysUser user = userMapper.selectById(uid);
        if (user == null) {
            throw new BizException(401, "用户不存在");
        }
        List<String> roles = roleCodes(uid);
        List<String> roleNames = userRoleMapper.selectList(
                        new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, uid)).stream()
                .map(ur -> roleMapper.selectById(ur.getRoleId()))
                .filter(r -> r != null)
                .map(SysRole::getRoleName).toList();

        SysDept dept = user.getDeptId() == null ? null : deptMapper.selectById(user.getDeptId());
        Post post = user.getPostId() == null ? null : postMapper.selectById(user.getPostId());

        return UserInfoVO.builder()
                .userId(uid)
                .username(user.getUsername())
                .realName(user.getRealName())
                .avatar(user.getAvatar())
                .deptId(user.getDeptId())
                .deptName(dept == null ? null : dept.getDeptName())
                .postId(user.getPostId())
                .postName(post == null ? null : post.getPostName())
                .roles(roles)
                .roleNames(roleNames)
                .permissions(StpUtil.getPermissionList())
                .build();
    }

    private List<String> roleCodes(Long userId) {
        List<SysUserRole> links = userRoleMapper.selectList(
                new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        if (links.isEmpty()) {
            return List.of();
        }
        return links.stream().map(ur -> roleMapper.selectById(ur.getRoleId()))
                .filter(r -> r != null).map(SysRole::getRoleCode).toList();
    }
}
