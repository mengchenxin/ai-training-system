package com.aitraining.module.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.aitraining.common.BizException;
import com.aitraining.common.Result;
import com.aitraining.module.system.entity.Post;
import com.aitraining.module.system.entity.SysDept;
import com.aitraining.module.system.entity.SysRole;
import com.aitraining.module.system.entity.SysUser;
import com.aitraining.module.system.mapper.PostMapper;
import com.aitraining.module.system.mapper.SysDeptMapper;
import com.aitraining.module.system.mapper.SysRoleMapper;
import com.aitraining.module.system.mapper.SysUserMapper;
import com.aitraining.security.LoginHelper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 组织架构维护：部门 / 岗位 / 角色
 */
@Tag(name = "03-组织与角色")
@RestController
@RequestMapping("/system")
@RequiredArgsConstructor
public class OrgController {

    private final SysDeptMapper deptMapper;
    private final PostMapper postMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserMapper userMapper;

    @Operation(summary = "部门列表")
    @GetMapping("/dept/list")
    public Result<List<SysDept>> deptList() {
        return Result.ok(deptMapper.selectList(new LambdaQueryWrapper<SysDept>()
                .orderByAsc(SysDept::getSortNo)));
    }

    @Operation(summary = "部门新增或修改")
    @PostMapping("/dept")
    public Result<Long> saveDept(@RequestBody SysDept dept) {
        LoginHelper.requireAdmin();
        if (dept.getId() == null) {
            deptMapper.insert(dept);
        } else {
            deptMapper.updateById(dept);
        }
        return Result.ok(dept.getId());
    }

    @Operation(summary = "删除部门")
    @DeleteMapping("/dept/{id}")
    public Result<Void> removeDept(@PathVariable Long id) {
        LoginHelper.requireAdmin();
        long sub = countDept(id);
        if (sub > 0) {
            throw new BizException("存在下级部门，无法删除");
        }
        Long users = userMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getDeptId, id));
        if (users != null && users > 0) {
            throw new BizException("部门下存在用户，无法删除");
        }
        deptMapper.deleteById(id);
        return Result.ok();
    }

    private long countDept(Long id) {
        Long c = deptMapper.selectCount(new LambdaQueryWrapper<SysDept>().eq(SysDept::getParentId, id));
        return c == null ? 0 : c;
    }

    @Operation(summary = "岗位列表")
    @GetMapping("/post/list")
    public Result<List<Post>> postList(@RequestParam(required = false) Long deptId) {
        LambdaQueryWrapper<Post> w = new LambdaQueryWrapper<>();
        if (deptId != null) {
            w.eq(Post::getDeptId, deptId);
        }
        w.orderByAsc(Post::getId);
        return Result.ok(postMapper.selectList(w));
    }

    @Operation(summary = "岗位新增或修改")
    @PostMapping("/post")
    public Result<Long> savePost(@RequestBody Post post) {
        LoginHelper.requireAdmin();
        if (post.getId() == null) {
            postMapper.insert(post);
        } else {
            postMapper.updateById(post);
        }
        return Result.ok(post.getId());
    }

    @Operation(summary = "删除岗位")
    @DeleteMapping("/post/{id}")
    public Result<Void> removePost(@PathVariable Long id) {
        LoginHelper.requireAdmin();
        Long users = userMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getPostId, id));
        if (users != null && users > 0) {
            throw new BizException("岗位下存在用户，无法删除");
        }
        postMapper.deleteById(id);
        return Result.ok();
    }

    @Operation(summary = "角色列表")
    @GetMapping("/role/list")
    public Result<List<SysRole>> roleList() {
        return Result.ok(roleMapper.selectList(new LambdaQueryWrapper<SysRole>().orderByAsc(SysRole::getId)));
    }
}
