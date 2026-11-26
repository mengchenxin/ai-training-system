package com.aitraining.module.train;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aitraining.common.BizException;
import com.aitraining.common.PageResult;
import com.aitraining.common.Result;
import com.aitraining.module.skill.entity.Skill;
import com.aitraining.module.skill.mapper.SkillMapper;
import com.aitraining.module.system.entity.Post;
import com.aitraining.module.system.entity.SysDept;
import com.aitraining.module.system.mapper.PostMapper;
import com.aitraining.module.system.mapper.SysDeptMapper;
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
    private final SysDeptMapper deptMapper;
    private final PostMapper postMapper;

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
            // 员工只能以自己名义提报：忽略前端传来的 submitterId，避免冒用他人名义
            // 讲师与管理员可代提（此时保留传入值，未传则落到本人）
            if (LoginHelper.isEmployee()) {
                need.setSubmitterId(LoginHelper.userId());
            } else if (need.getSubmitterId() == null) {
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
            if (LoginHelper.isEmployee()) {
                // 员工改自己的需求时不允许把提交人改成别人（updateById 跳过 null 字段）
                need.setSubmitterId(null);
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

    /**
     * 培训需求汇总 —— 按部门 / 岗位 / 技能 / 优先级 / 状态多维聚合。
     *
     * <p>补齐「需求汇总」的聚合维度：需求列表只能逐条看，
     * 计划编制前需要先回答「哪个部门缺口最多、哪类技能被反复提报」，
     * 因此这里一次性返回可直接渲染看板的分组结果，避免前端拉全量再自己算。
     */
    @Operation(summary = "培训需求汇总（部门/岗位/技能/优先级聚合）")
    @GetMapping("/need/summary")
    public Result<Map<String, Object>> needSummary(@RequestParam(required = false) String auditStatus,
                                                   @RequestParam(required = false) Long deptId) {
        LoginHelper.requireManager();
        LambdaQueryWrapper<TrainNeed> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(auditStatus)) {
            w.eq(TrainNeed::getAuditStatus, auditStatus);
        }
        if (deptId != null) {
            w.eq(TrainNeed::getDeptId, deptId);
        }
        List<TrainNeed> all = needMapper.selectList(w);

        Map<Long, String> deptNames = deptNameMap(all.stream()
                .map(TrainNeed::getDeptId).filter(Objects::nonNull).collect(Collectors.toSet()));
        Map<Long, String> postNames = postNameMap(all.stream()
                .map(TrainNeed::getPostId).filter(Objects::nonNull).collect(Collectors.toSet()));
        Map<Long, String> skillNames = skillNameMap(all.stream()
                .map(TrainNeed::getSkillId).filter(Objects::nonNull).collect(Collectors.toSet()));

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", all.size());
        m.put("byStatus", groupCount(all, n -> Objects.requireNonNullElse(n.getAuditStatus(), "UNKNOWN")));
        m.put("byPriority", groupCount(all, n -> String.valueOf(Objects.requireNonNullElse(n.getPriority(), 3))));
        m.put("byDept", groupCount(all, n -> n.getDeptId() == null ? "未归属"
                : deptNames.getOrDefault(n.getDeptId(), "部门#" + n.getDeptId())));
        m.put("byPost", groupCount(all, n -> n.getPostId() == null ? "未指定"
                : postNames.getOrDefault(n.getPostId(), "岗位#" + n.getPostId())));
        m.put("bySkill", groupCount(all, n -> n.getSkillId() == null ? "未指定"
                : skillNames.getOrDefault(n.getSkillId(), "技能#" + n.getSkillId())));

        // 高优先级需求清单（priority=1），计划编制时优先排
        List<Map<String, Object>> urgent = all.stream()
                .filter(n -> n.getPriority() != null && n.getPriority() == 1)
                .sorted(Comparator.comparing(TrainNeed::getId).reversed())
                .limit(10)
                .map(n -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", n.getId());
                    item.put("needTitle", n.getNeedTitle());
                    item.put("deptName", n.getDeptId() == null ? null : deptNames.get(n.getDeptId()));
                    item.put("postName", n.getPostId() == null ? null : postNames.get(n.getPostId()));
                    item.put("skillName", n.getSkillId() == null ? null : skillNames.get(n.getSkillId()));
                    item.put("auditStatus", n.getAuditStatus());
                    item.put("createTime", n.getCreateTime());
                    return item;
                }).toList();
        m.put("urgent", urgent);
        return Result.ok(m);
    }

    private Map<Long, String> skillNameMap(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return skillMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Skill::getId, Skill::getSkillName));
    }

    /** 按分组键统计条数，并按数量倒序，前端直接当图表数据用 */
    private List<Map<String, Object>> groupCount(List<TrainNeed> list,
                                                 java.util.function.Function<TrainNeed, String> key) {
        Map<String, Long> counts = list.stream()
                .collect(Collectors.groupingBy(key, LinkedHashMap::new, Collectors.counting()));
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("name", e.getKey());
                    item.put("value", e.getValue());
                    return item;
                }).toList();
    }

    private Map<Long, String> deptNameMap(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return deptMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(SysDept::getId, SysDept::getDeptName));
    }

    private Map<Long, String> postNameMap(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return postMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Post::getId, Post::getPostName));
    }
}