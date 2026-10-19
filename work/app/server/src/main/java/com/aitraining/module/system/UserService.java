package com.aitraining.module.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aitraining.common.BizException;
import com.aitraining.common.PageResult;
import com.aitraining.module.system.dto.UserForm;
import com.aitraining.module.system.dto.UserVO;
import com.aitraining.module.system.entity.*;
import com.aitraining.module.system.mapper.*;
import com.aitraining.security.LoginHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final SysDeptMapper deptMapper;
    private final PostMapper postMapper;
    private final PasswordEncoder passwordEncoder;

    public PageResult<UserVO> page(long current, long size, String keyword, Long deptId,
                                   Long postId, Long roleId, Integer status) {
        LambdaQueryWrapper<SysUser> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            w.and(q -> q.like(SysUser::getUsername, keyword).or().like(SysUser::getRealName, keyword));
        }
        if (deptId != null) {
            w.eq(SysUser::getDeptId, deptId);
        }
        if (postId != null) {
            w.eq(SysUser::getPostId, postId);
        }
        if (status != null) {
            w.eq(SysUser::getStatus, status);
        }
        if (roleId != null) {
            List<Long> ids = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                            .eq(SysUserRole::getRoleId, roleId)).stream()
                    .map(SysUserRole::getUserId).toList();
            if (ids.isEmpty()) {
                return PageResult.of(List.of(), 0, current, size);
            }
            w.in(SysUser::getId, ids);
        }
        w.orderByAsc(SysUser::getId);
        Page<SysUser> page = userMapper.selectPage(new Page<>(current, size), w);
        return PageResult.of(toVOList(page.getRecords()), page.getTotal(), current, size);
    }

    public List<UserVO> toVOList(List<SysUser> users) {
        if (users.isEmpty()) {
            return List.of();
        }
        Set<Long> deptIds = users.stream().map(SysUser::getDeptId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> postIds = users.stream().map(SysUser::getPostId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> deptMap = deptIds.isEmpty() ? Map.of() : deptMapper.selectBatchIds(deptIds).stream()
                .collect(Collectors.toMap(SysDept::getId, SysDept::getDeptName));
        Map<Long, String> postMap = postIds.isEmpty() ? Map.of() : postMapper.selectBatchIds(postIds).stream()
                .collect(Collectors.toMap(Post::getId, Post::getPostName));

        List<Long> userIds = users.stream().map(SysUser::getId).toList();
        List<SysUserRole> links = userRoleMapper.selectList(
                new LambdaQueryWrapper<SysUserRole>().in(SysUserRole::getUserId, userIds));
        Set<Long> roleIds = links.stream().map(SysUserRole::getRoleId).collect(Collectors.toSet());
        Map<Long, SysRole> roleMap = roleIds.isEmpty() ? Map.of() : roleMapper.selectBatchIds(roleIds).stream()
                .collect(Collectors.toMap(SysRole::getId, r -> r));
        Map<Long, List<SysUserRole>> linkMap = links.stream()
                .collect(Collectors.groupingBy(SysUserRole::getUserId));

        List<UserVO> out = new ArrayList<>();
        for (SysUser u : users) {
            UserVO vo = new UserVO();
            vo.setId(u.getId());
            vo.setUsername(u.getUsername());
            vo.setRealName(u.getRealName());
            vo.setDeptId(u.getDeptId());
            vo.setDeptName(deptMap.get(u.getDeptId()));
            vo.setPostId(u.getPostId());
            vo.setPostName(postMap.get(u.getPostId()));
            vo.setPhone(u.getPhone());
            vo.setEmail(u.getEmail());
            vo.setStatus(u.getStatus());
            vo.setLastLoginAt(u.getLastLoginAt());
            vo.setCreateTime(u.getCreateTime());
            List<SysUserRole> l = linkMap.getOrDefault(u.getId(), List.of());
            vo.setRoleIds(l.stream().map(SysUserRole::getRoleId).toList());
            vo.setRoles(l.stream().map(x -> roleMap.get(x.getRoleId()))
                    .filter(Objects::nonNull).map(SysRole::getRoleCode).toList());
            out.add(vo);
        }
        return out;
    }

    public UserVO detail(Long id) {
        SysUser u = userMapper.selectById(id);
        if (u == null) {
            throw new BizException("用户不存在");
        }
        return toVOList(List.of(u)).get(0);
    }

    @Transactional
    public Long save(UserForm form) {
        LoginHelper.requireAdmin();
        boolean isNew = form.getId() == null;
        Long uid;
        if (isNew) {
            Long cnt = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getUsername, form.getUsername()));
            if (cnt != null && cnt > 0) {
                throw new BizException("账号已存在");
            }
            if (!StringUtils.hasText(form.getPassword())) {
                throw new BizException("新增用户必须设置初始密码");
            }
            SysUser u = new SysUser();
            u.setUsername(form.getUsername());
            u.setPassword(passwordEncoder.encode(form.getPassword()));
            u.setRealName(form.getRealName());
            u.setDeptId(form.getDeptId());
            u.setPostId(form.getPostId());
            u.setPhone(form.getPhone());
            u.setEmail(form.getEmail());
            u.setStatus(form.getStatus() == null ? 1 : form.getStatus());
            userMapper.insert(u);
            uid = u.getId();
        } else {
            SysUser u = userMapper.selectById(form.getId());
            if (u == null) {
                throw new BizException("用户不存在");
            }
            u.setRealName(form.getRealName());
            u.setDeptId(form.getDeptId());
            u.setPostId(form.getPostId());
            u.setPhone(form.getPhone());
            u.setEmail(form.getEmail());
            if (form.getStatus() != null) {
                u.setStatus(form.getStatus());
            }
            if (StringUtils.hasText(form.getPassword())) {
                u.setPassword(passwordEncoder.encode(form.getPassword()));
            }
            userMapper.updateById(u);
            uid = u.getId();
        }
        if (form.getRoleIds() != null) {
            userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, uid));
            for (Long rid : form.getRoleIds()) {
                SysUserRole ur = new SysUserRole();
                ur.setUserId(uid);
                ur.setRoleId(rid);
                userRoleMapper.insert(ur);
            }
        }
        return uid;
    }

    @Transactional
    public void remove(Long id) {
        LoginHelper.requireAdmin();
        if (id.equals(LoginHelper.userId())) {
            throw new BizException("不能删除当前登录账号");
        }
        userMapper.deleteById(id);
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, id));
    }

    public void resetPassword(Long id, String newPassword) {
        LoginHelper.requireAdmin();
        SysUser u = new SysUser();
        u.setId(id);
        u.setPassword(passwordEncoder.encode(
                StringUtils.hasText(newPassword) ? newPassword : "123456"));
        userMapper.updateById(u);
    }
}
