package com.aitraining.module.exam;

import com.aitraining.common.PageResult;
import com.aitraining.common.Result;
import com.aitraining.module.exam.dto.PaperGenForm;
import com.aitraining.module.exam.dto.SubmitForm;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "09-考核与试卷")
@RestController
@RequestMapping("/exam")
@RequiredArgsConstructor
public class ExamController {

    private final ExamService examService;

    @Operation(summary = "按技能自动组卷")
    @PostMapping("/paper/generate")
    public Result<Long> generate(@RequestBody PaperGenForm form) {
        return Result.ok(examService.autoGenerate(form));
    }

    @Operation(summary = "试卷分页")
    @GetMapping("/paper/page")
    public Result<PageResult<Map<String, Object>>> paperPage(@RequestParam(defaultValue = "1") long current,
                                                             @RequestParam(defaultValue = "10") long size,
                                                             @RequestParam(required = false) String keyword,
                                                             @RequestParam(required = false) Long planId) {
        return Result.ok(examService.paperPage(current, size, keyword, planId));
    }

    @Operation(summary = "取卷（考生视角，不含答案）")
    @GetMapping("/paper/{id}")
    public Result<Map<String, Object>> paper(@PathVariable Long id) {
        return Result.ok(examService.paperForExam(id));
    }

    @Operation(summary = "开始作答")
    @PostMapping("/paper/{id}/start")
    public Result<Void> start(@PathVariable Long id) {
        examService.startExam(id);
        return Result.ok();
    }

    @Operation(summary = "提交试卷并自动判分")
    @PostMapping("/paper/{id}/submit")
    public Result<Map<String, Object>> submit(@PathVariable Long id, @RequestBody SubmitForm form) {
        return Result.ok(examService.submit(id, form));
    }

    @Operation(summary = "我的考试成绩")
    @GetMapping("/record/my")
    public Result<List<Map<String, Object>>> myRecords() {
        return Result.ok(examService.myRecords());
    }

    @Operation(summary = "考试成绩分页（讲师端）")
    @GetMapping("/record/page")
    public Result<PageResult<Map<String, Object>>> recordPage(@RequestParam(defaultValue = "1") long current,
                                                              @RequestParam(defaultValue = "10") long size,
                                                              @RequestParam(required = false) Long planId,
                                                              @RequestParam(required = false) Long userId) {
        return Result.ok(examService.recordPage(current, size, planId, userId));
    }
}
