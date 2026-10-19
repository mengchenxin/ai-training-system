package com.aitraining.module.train;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aitraining.common.BizException;
import com.aitraining.common.PageResult;
import com.aitraining.common.Result;
import com.aitraining.module.skill.entity.Skill;
import com.aitraining.module.skill.mapper.SkillMapper;
import com.aitraining.module.train.entity.Course;
import com.aitraining.module.train.entity.TrainNeed;
import com.aitraining.module.train.mapper.CourseMapper;
import com.aitraining.module.train.mapper.TrainNeedMapper;
import com.aitraining.security.LoginHelper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 课程库 与 培训需求
 */
@Tag(name = "05-课程与培训需求")
@RestController
@RequestMapping("/train")
@RequiredArgsConstructor
public class TrainBasicController {

    private final CourseMapper courseMapper;
    private final TrainNeedMapper needMapper;
    private final SkillMapper skillMapper;

    // ---------------- 课程 ----------------

    @Operation(summary = "课程分页")
    @GetMapping("/course/page")
    public Result<PageResult<Map<String, Object>>> coursePage(@RequestParam(defaultValue = "1") long current,
                                                              @RequestParam(defaultValue = "10") long size,
                                                              @RequestParam(required = false) String keyword,
                                                              @RequestParam(required = false) Long skillId) {
        LambdaQueryWrapper<Course> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            w.and(q -> q.like(Course::getCourseName, keyword).or().like(Course::getCourseCode, keyword));
        }
        if (skillId != null) {
            w.eq(Course::getSkillId, skillId);
        }
        w.orderByAsc(Course::getId);
        Page<Course> p = courseMapper.selectPage(new Page<>(current, size), w);
        Map<Long, String> skillNames = skillNameMap(p.getRecords().stream()
                .map(Course::getSkillId).filter(Objects::nonNull).collect(Collectors.toSet()));
        List<Map<String, Object>> rows = p.getRecords().stream().map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getId());
            m.put("courseCode", c.getCourseCode());
            m.put("courseName", c.getCourseName());
            m.put("skillId", c.getSkillId());
            m.put("skillName", skillNames.get(c.getSkillId()));
            m.put("category", c.getCategory());
            m.put("level", c.getLevel());
            m.put("durationHours", c.getDurationHours());
            m.put("intro", c.getIntro());
            m.put("resourceUrl", c.getResourceUrl());
            m.put("status", c.getStatus());
            return m;
        }).toList();
        return Result.ok(PageResult.of(rows, p.getTotal(), current, size));
    }

    @Operation(summary = "课程列表（下拉用）")
    @GetMapping("/course/list")
    public Result<List<Course>> courseList(@RequestParam(required = false) Long skillId) {
        LambdaQueryWrapper<Course> w = new LambdaQueryWrapper<>();
        if (skillId != null) {
            w.eq(Course::getSkillId, skillId);
        }
        w.eq(Course::getStatus, 1).orderByAsc(Course::getId);
        return Result.ok(courseMapper.selectList(w));
    }

    @Operation(summary = "课程新增或修改")
    @PostMapping("/course")
    public Result<Long> saveCourse(@RequestBody Course course) {
        LoginHelper.requireManager();
        if (course.getId() == null) {
            courseMapper.insert(course);
        } else {
            courseMapper.updateById(course);
        }
        return Result.ok(course.getId());
    }

    @Operation(summary = "删除课程")
    @DeleteMapping("/course/{id}")
    public Result<Void> removeCourse(@PathVariable Long id) {
        LoginHelper.requireAdmin();
        courseMapper.deleteById(id);
        return Result.ok();
    }

    // ---------------- 培训需求 ----------------

    @Operation(summary = "需求分页")
    @GetMapping("/need/page")
    public Result<PageResult<Map<String, Object>>> needPage(@RequestParam(defaultValue = "1") long current,
                                                            @RequestParam(defaultValue = "10") long size,
                                                            @RequestParam(required = false) String auditStatus,
                                                            @RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<TrainNeed> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(auditStatus)) {
            w.eq(TrainNeed::getAuditStatus, auditStatus);
        }
        if (StringUtils.hasText(keyword)) {
            w.like(TrainNeed::getNeedTitle, keyword);
        }
        // 员工只能看到自己提报的需求
        if (LoginHelper.isEmployee()) {
            w.eq(TrainNeed::getSubmitterId, LoginHelper.userId());
        }
        w.orderByDesc(TrainNeed::getId);
        Page<TrainNeed> p = needMapper.selectPage(new Page<>(current, size), w);
        Map<Long, String> skillNames = skillNameMap(p.getRecords().stream()
                .map(TrainNeed::getSkillId).filter(Objects::nonNull).collect(Collectors.toSet()));
        List<Map<String, Object>> rows = p.getRecords().stream().map(n -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", n.getId());
            m.put("needTitle", n.getNeedTitle());
            m.put("deptId", n.getDeptId());
            m.put("postId", n.getPostId());
            m.put("skillId", n.getSkillId());
            m.put("skillName", skillNames.get(n.getSkillId()));
            m.put("needDesc", n.getNeedDesc());
            m.put("source", n.getSource());
            m.put("priority", n.getPriority());
            m.put("submitterId", n.getSubmitterId());
            m.put("auditStatus", n.getAuditStatus());
            m.put("createTime", n.getCreateTime());
            return m;
        }).toList();
        return Result.ok(PageResult.of(rows, p.getTotal(), current, size));
    }

    @Operation(summary = "登记培训需求")
    @PostMapping("/need")
    public Result<Long> saveNeed(@RequestBody TrainNeed need) {
        if (need.getId() == null) {
            if (need.getSubmitterId() == null) {
                need.setSubmitterId(LoginHelper.userId());
            }
            if (!StringUtils.hasText(need.getSource())) {
                need.setSource("EMP");
            }
            need.setAuditStatus("PENDING");
            needMapper.insert(need);
        } else {
            TrainNeed db = needMapper.selectById(need.getId());
            if (db == null) {
                throw new BizException("需求不存在");
            }
            if (!LoginHelper.isManager() && !LoginHelper.userId().equals(db.getSubmitterId())) {
                throw new BizException(403, "只能修改自己提报的需求");
            }
            if (!"PENDING".equals(db.getAuditStatus()) && !LoginHelper.isManager()) {
                throw new BizException("已审核的需求不能再修改");
            }
            needMapper.updateById(need);
        }
        return Result.ok(need.getId());
    }

    @Operation(summary = "审核培训需求")
    @PostMapping("/need/{id}/audit")
    public Result<Void> auditNeed(@PathVariable Long id, @RequestParam String status) {
        LoginHelper.requireManager();
        if (!List.of("APPROVED", "REJECTED", "PENDING").contains(status)) {
            throw new BizException("非法的审核状态");
        }
        TrainNeed n = new TrainNeed();
        n.setId(id);
        n.setAuditStatus(status);
        needMapper.updateById(n);
        return Result.ok();
    }

    @Operation(summary = "删除培训需求")
    @DeleteMapping("/need/{id}")
    public Result<Void> removeNeed(@PathVariable Long id) {
        TrainNeed db = needMapper.selectById(id);
        if (db == null) {
            return Result.ok();
        }
        if (!LoginHelper.isManager() && !LoginHelper.userId().equals(db.getSubmitterId())) {
            throw new BizException(403, "只能删除自己提报的需求");
        }
        needMapper.deleteById(id);
        return Result.ok();
    }

    private Map<Long, String> skillNameMap(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return skillMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Skill::getId, Skill::getSkillName));
    }
}
