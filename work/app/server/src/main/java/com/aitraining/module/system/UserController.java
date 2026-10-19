package com.aitraining.module.system;

import com.aitraining.common.PageResult;
import com.aitraining.common.Result;
import com.aitraining.module.system.dto.UserForm;
import com.aitraining.module.system.dto.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "02-用户管理")
@RestController
@RequestMapping("/system/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "用户分页")
    @GetMapping("/page")
    public Result<PageResult<UserVO>> page(@RequestParam(defaultValue = "1") long current,
                                           @RequestParam(defaultValue = "10") long size,
                                           @RequestParam(required = false) String keyword,
                                           @RequestParam(required = false) Long deptId,
                                           @RequestParam(required = false) Long postId,
                                           @RequestParam(required = false) Long roleId,
                                           @RequestParam(required = false) Integer status) {
        return Result.ok(userService.page(current, size, keyword, deptId, postId, roleId, status));
    }

    @Operation(summary = "用户详情")
    @GetMapping("/{id}")
    public Result<UserVO> detail(@PathVariable Long id) {
        return Result.ok(userService.detail(id));
    }

    @Operation(summary = "新增或修改用户")
    @PostMapping
    public Result<Long> save(@Valid @RequestBody UserForm form) {
        return Result.ok(userService.save(form));
    }

    @Operation(summary = "删除用户")
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        userService.remove(id);
        return Result.ok();
    }

    @Operation(summary = "重置密码")
    @PostMapping("/{id}/reset-password")
    public Result<Void> resetPassword(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        userService.resetPassword(id, body == null ? null : body.get("password"));
        return Result.ok();
    }
}
