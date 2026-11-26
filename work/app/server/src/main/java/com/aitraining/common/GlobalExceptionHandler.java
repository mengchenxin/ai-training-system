package com.aitraining.common;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.UUID;

/**
 * 全局异常处理
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public Result<Void> handleBiz(BizException e) {
        log.warn("业务异常: {}", e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(NotLoginException.class)
    public Result<Void> handleNotLogin(NotLoginException e) {
        return Result.fail(401, "登录状态已失效，请重新登录");
    }

    @ExceptionHandler({NotRoleException.class, NotPermissionException.class})
    public Result<Void> handleNoPermission(Exception e) {
        return Result.fail(403, "没有操作权限");
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public Result<Void> handleValid(Exception e) {
        String msg = "参数校验失败";
        if (e instanceof MethodArgumentNotValidException ex && ex.getBindingResult().getFieldError() != null) {
            msg = ex.getBindingResult().getFieldError().getDefaultMessage();
        } else if (e instanceof BindException ex && ex.getBindingResult().getFieldError() != null) {
            msg = ex.getBindingResult().getFieldError().getDefaultMessage();
        }
        return Result.fail(400, msg);
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleOther(Exception e) {
        // 不把 e.getMessage() 回给前端：它可能是 SQL 片段、表名、文件路径或类名，
        // 等于把内部结构告诉调用方。对外只给一个短错误码，完整堆栈留在日志里。
        String traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        log.error("系统异常 traceId={} type={}", traceId, e.getClass().getSimpleName(), e);
        return Result.fail(500, "系统繁忙，请稍后重试（错误码 " + traceId + "）");
    }
}
