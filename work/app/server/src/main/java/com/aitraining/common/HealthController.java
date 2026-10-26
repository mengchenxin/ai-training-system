package com.aitraining.common;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 存活探针。
 * 不查数据库、不依赖登录态，供 Render healthCheck 与外部保活定时任务使用，
 * 避免因为数据库抖动导致平台误判实例已死而反复重启。
 */
@RestController
public class HealthController {

    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", "UP");
        m.put("app", "ai-training");
        m.put("time", LocalDateTime.now().toString());
        return Result.ok(m);
    }
}
