package com.aitraining.module.train;

import com.aitraining.common.PageResult;
import com.aitraining.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Tag(name = "07-培训实施与进度跟踪")
@RestController
@RequestMapping("/train/progress")
@RequiredArgsConstructor
public class TrainProgressController {

    private final TrainProgressService progressService;

    @Operation(summary = "我的培训任务")
    @GetMapping("/my")
    public Result<List<Map<String, Object>>> my(@RequestParam(required = false) String status) {
        return Result.ok(progressService.myTasks(status));
    }

    @Operation(summary = "上报学习进度")
    @PostMapping("/report")
    public Result<Void> report(@RequestBody ReportForm form) {
        progressService.report(form.getId(), form.getProgressPercent(),
                form.getSpentHours(), form.getRemark());
        return Result.ok();
    }

    @Operation(summary = "某计划的进度明细")
    @GetMapping("/plan/{planId}")
    public Result<PageResult<Map<String, Object>>> planProgress(@PathVariable Long planId,
                                                                @RequestParam(defaultValue = "1") long current,
                                                                @RequestParam(defaultValue = "20") long size) {
        return Result.ok(progressService.planProgress(planId, current, size));
    }

    @Operation(summary = "统计看板")
    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard(@RequestParam(required = false) Long deptId) {
        return Result.ok(progressService.dashboard(deptId));
    }

    @Operation(summary = "拖期风险计划")
    @GetMapping("/risk")
    public Result<List<Map<String, Object>>> risk() {
        return Result.ok(progressService.riskPlans());
    }

    @Data
    public static class ReportForm {
        private Long id;
        private BigDecimal progressPercent;
        private BigDecimal spentHours;
        private String remark;
    }
}
