package com.aitraining.module.train;

import com.aitraining.common.PageResult;
import com.aitraining.common.Result;
import com.aitraining.module.train.dto.PlanDetailVO;
import com.aitraining.module.train.dto.PlanForm;
import com.aitraining.security.LoginHelper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "06-培训计划")
@RestController
@RequestMapping("/train/plan")
@RequiredArgsConstructor
public class TrainPlanController {

    private final TrainPlanService planService;

    @Operation(summary = "计划分页")
    @GetMapping("/page")
    public Result<PageResult<Map<String, Object>>> page(@RequestParam(defaultValue = "1") long current,
                                                        @RequestParam(defaultValue = "10") long size,
                                                        @RequestParam(required = false) String status,
                                                        @RequestParam(required = false) String keyword,
                                                        @RequestParam(required = false) String planType) {
        return Result.ok(planService.page(current, size, status, keyword, planType));
    }

    @Operation(summary = "计划详情（含课程与进度统计）")
    @GetMapping("/{id}")
    public Result<PlanDetailVO> detail(@PathVariable Long id) {
        return Result.ok(planService.detail(id));
    }

    @Operation(summary = "保存计划（新建或编辑，含课程编排）")
    @PostMapping
    public Result<Long> save(@Valid @RequestBody PlanForm form) {
        return Result.ok(planService.save(form, LoginHelper.userId()));
    }

    @Operation(summary = "状态流转：提交/审核发布/开始/结束/取消")
    @PostMapping("/{id}/status")
    public Result<Void> changeStatus(@PathVariable Long id, @RequestParam String status) {
        planService.changeStatus(id, status);
        return Result.ok();
    }

    @Operation(summary = "删除计划")
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        planService.remove(id);
        return Result.ok();
    }
}
