package com.aitraining.module.train;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aitraining.common.BizException;
import com.aitraining.common.PageResult;
import com.aitraining.common.Result;
import com.aitraining.module.skill.mapper.SkillMapper;
import com.aitraining.module.system.entity.SysDept;
import com.aitraining.module.system.entity.SysUser;
import com.aitraining.module.system.mapper.SysDeptMapper;
import com.aitraining.module.system.mapper.SysUserMapper;
import com.aitraining.module.train.entity.Course;
import com.aitraining.module.train.entity.TrainPlan;
import com.aitraining.module.train.entity.TrainProgress;
import com.aitraining.module.train.mapper.CourseMapper;
import com.aitraining.module.train.mapper.TrainPlanMapper;
import com.aitraining.module.train.mapper.TrainProgressMapper;
import com.aitraining.security.LoginHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TrainProgressService {

    private final TrainProgressMapper progressMapper;
    private final TrainPlanMapper planMapper;
    private final CourseMapper courseMapper;
    private final SysUserMapper userMapper;
    private final SysDeptMapper deptMapper;
    private final TrainPlanService planService;

    /** 我的培训任务（员工端） */
    public List<Map<String, Object>> myTasks(String status) {
        Long uid = LoginHelper.userId();
        LambdaQueryWrapper<TrainProgress> w = new LambdaQueryWrapper<>();
        w.eq(TrainProgress::getUserId, uid);
        if (status != null && !status.isBlank()) {
            w.eq(TrainProgress::getStatus, status);
        }
        w.orderByAsc(TrainProgress::getPlanId).orderByAsc(TrainProgress::getCourseId);
        List<TrainProgress> list = progressMapper.selectList(w);
        if (list.isEmpty()) {
            return List.of();
        }
        Map<Long, TrainPlan> plans = planMapper.selectBatchIds(
                        list.stream().map(TrainProgress::getPlanId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(TrainPlan::getId, p -> p));
        Map<Long, Course> courses = courseMapper.selectBatchIds(
                        list.stream().map(TrainProgress::getCourseId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Course::getId, c -> c));

        List<Map<String, Object>> out = new ArrayList<>();
        for (TrainProgress p : list) {
            TrainPlan plan = plans.get(p.getPlanId());
            Course c = courses.get(p.getCourseId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("planId", p.getPlanId());
            m.put("planName", plan == null ? "" : plan.getPlanName());
            m.put("planStatus", plan == null ? "" : plan.getStatus());
            m.put("endDate", plan == null ? null : plan.getEndDate());
            m.put("courseId", p.getCourseId());
            m.put("courseName", c == null ? "" : c.getCourseName());
            m.put("durationHours", c == null ? null : c.getDurationHours());
            m.put("progressPercent", p.getProgressPercent());
            m.put("status", p.getStatus());
            m.put("spentHours", p.getSpentHours());
            m.put("reportTime", p.getReportTime());
            boolean overdue = plan != null && plan.getEndDate() != null
                    && plan.getEndDate().isBefore(LocalDate.now())
                    && !"FINISHED".equals(p.getStatus());
            m.put("overdue", overdue);
            out.add(m);
        }
        return out;
    }

    /** 进度上报 */
    @Transactional
    public void report(Long progressId, BigDecimal percent, BigDecimal spentHours, String remark) {
        TrainProgress p = progressMapper.selectById(progressId);
        if (p == null) {
            throw new BizException("进度记录不存在");
        }
        if (!p.getUserId().equals(LoginHelper.userId())) {
            throw new BizException(403, "只能上报自己的学习进度");
        }
        TrainPlan plan = planMapper.selectById(p.getPlanId());
        if (plan == null || !List.of("PUBLISHED", "RUNNING").contains(plan.getStatus())) {
            throw new BizException("该计划当前不可上报进度（状态：" + (plan == null ? "-" : plan.getStatus()) + "）");
        }
        BigDecimal pct = percent == null ? BigDecimal.ZERO
                : percent.max(BigDecimal.ZERO).min(BigDecimal.valueOf(100));
        p.setProgressPercent(pct);
        p.setStatus(pct.compareTo(BigDecimal.valueOf(100)) >= 0 ? "FINISHED"
                : pct.compareTo(BigDecimal.ZERO) > 0 ? "LEARNING" : "NOT_STARTED");
        if (spentHours != null) {
            p.setSpentHours(spentHours);
        }
        p.setReportTime(LocalDateTime.now());
        p.setRemark(remark);
        progressMapper.updateById(p);

        // 全部课程完成则计划推进为进行中/已完成
        refreshPlanStatus(plan);
    }

    /** 根据进度刷新计划状态：有进度则 RUNNING，全员全课程完成则 FINISHED */
    private void refreshPlanStatus(TrainPlan plan) {
        List<TrainProgress> all = progressMapper.selectList(
                new LambdaQueryWrapper<TrainProgress>().eq(TrainProgress::getPlanId, plan.getId()));
        if (all.isEmpty()) {
            return;
        }
        boolean anyStarted = all.stream().anyMatch(p -> p.getProgressPercent() != null
                && p.getProgressPercent().compareTo(BigDecimal.ZERO) > 0);
        boolean allFinished = all.stream().allMatch(p -> "FINISHED".equals(p.getStatus()));
        if (allFinished && !"FINISHED".equals(plan.getStatus())) {
            plan.setStatus("FINISHED");
            planMapper.updateById(plan);
        } else if (anyStarted && "PUBLISHED".equals(plan.getStatus())) {
            plan.setStatus("RUNNING");
            planMapper.updateById(plan);
        }
    }

    /** 某计划的进度明细（讲师端） */
    public PageResult<Map<String, Object>> planProgress(Long planId, long current, long size) {
        LoginHelper.requireManager();
        TrainPlan plan = planMapper.selectById(planId);
        if (plan == null) {
            throw new BizException("计划不存在");
        }
        LambdaQueryWrapper<TrainProgress> w = new LambdaQueryWrapper<>();
        w.eq(TrainProgress::getPlanId, planId)
                .orderByAsc(TrainProgress::getUserId).orderByAsc(TrainProgress::getCourseId);
        Page<TrainProgress> p = progressMapper.selectPage(new Page<>(current, size), w);

        Set<Long> userIds = p.getRecords().stream().map(TrainProgress::getUserId).collect(Collectors.toSet());
        Map<Long, SysUser> users = userIds.isEmpty() ? Map.of() : userMapper.selectBatchIds(userIds)
                .stream().collect(Collectors.toMap(SysUser::getId, u -> u));
        Set<Long> courseIds = p.getRecords().stream().map(TrainProgress::getCourseId).collect(Collectors.toSet());
        Map<Long, Course> courses = courseIds.isEmpty() ? Map.of() : courseMapper.selectBatchIds(courseIds)
                .stream().collect(Collectors.toMap(Course::getId, c -> c));
        Map<Long, String> depts = deptMapper.selectList(new LambdaQueryWrapper<>()).stream()
                .collect(Collectors.toMap(SysDept::getId, SysDept::getDeptName));

        List<Map<String, Object>> rows = new ArrayList<>();
        for (TrainProgress tp : p.getRecords()) {
            SysUser u = users.get(tp.getUserId());
            Course c = courses.get(tp.getCourseId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", tp.getId());
            m.put("userId", tp.getUserId());
            m.put("realName", u == null ? "" : u.getRealName());
            m.put("deptName", u == null ? "" : depts.get(u.getDeptId()));
            m.put("courseId", tp.getCourseId());
            m.put("courseName", c == null ? "" : c.getCourseName());
            m.put("progressPercent", tp.getProgressPercent());
            m.put("status", tp.getStatus());
            m.put("spentHours", tp.getSpentHours());
            m.put("reportTime", tp.getReportTime());
            m.put("remark", tp.getRemark());
            rows.add(m);
        }
        return PageResult.of(rows, p.getTotal(), current, size);
    }

    /** 统计看板 */
    public Map<String, Object> dashboard(Long deptId) {
        LoginHelper.requireManager();
        List<TrainPlan> plans = planMapper.selectList(new LambdaQueryWrapper<>());
        List<TrainProgress> all = progressMapper.selectList(new LambdaQueryWrapper<>());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("planTotal", plans.size());
        out.put("planRunning", plans.stream().filter(p -> "RUNNING".equals(p.getStatus())).count());
        out.put("planFinished", plans.stream().filter(p -> "FINISHED".equals(p.getStatus())).count());
        out.put("planDraft", plans.stream().filter(p -> "DRAFT".equals(p.getStatus())).count());

        double avg = all.isEmpty() ? 0 : all.stream()
                .mapToDouble(x -> x.getProgressPercent() == null ? 0 : x.getProgressPercent().doubleValue())
                .average().orElse(0);
        out.put("overallFinishRate", BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP));

        long finishedCnt = all.stream().filter(x -> "FINISHED".equals(x.getStatus())).count();
        out.put("courseRecordTotal", all.size());
        out.put("courseRecordFinished", finishedCnt);
        out.put("learnHours", all.stream().map(x -> x.getSpentHours() == null ? BigDecimal.ZERO : x.getSpentHours())
                .reduce(BigDecimal.ZERO, BigDecimal::add));

        // 部门完成率
        Map<Long, SysUser> userMap = userMapper.selectList(new LambdaQueryWrapper<>()).stream()
                .collect(Collectors.toMap(SysUser::getId, u -> u));
        Map<Long, String> deptNames = deptMapper.selectList(new LambdaQueryWrapper<>()).stream()
                .collect(Collectors.toMap(SysDept::getId, SysDept::getDeptName));
        Map<Long, List<TrainProgress>> byDept = new HashMap<>();
        for (TrainProgress p : all) {
            SysUser u = userMap.get(p.getUserId());
            if (u == null || u.getDeptId() == null) {
                continue;
            }
            byDept.computeIfAbsent(u.getDeptId(), k -> new ArrayList<>()).add(p);
        }
        List<Map<String, Object>> deptStats = new ArrayList<>();
        for (Map.Entry<Long, List<TrainProgress>> e : byDept.entrySet()) {
            double d = e.getValue().stream()
                    .mapToDouble(x -> x.getProgressPercent() == null ? 0 : x.getProgressPercent().doubleValue())
                    .average().orElse(0);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("deptId", e.getKey());
            m.put("deptName", deptNames.getOrDefault(e.getKey(), "-"));
            m.put("finishRate", BigDecimal.valueOf(d).setScale(2, RoundingMode.HALF_UP));
            m.put("recordCount", e.getValue().size());
            deptStats.add(m);
        }
        deptStats.sort((a, b) -> ((BigDecimal) b.get("finishRate")).compareTo((BigDecimal) a.get("finishRate")));
        out.put("deptStats", deptStats);

        // 完成率分布
        long notStarted = all.stream().filter(x -> "NOT_STARTED".equals(x.getStatus())).count();
        long learning = all.stream().filter(x -> "LEARNING".equals(x.getStatus())).count();
        out.put("distribution", List.of(
                Map.of("name", "未开始", "value", notStarted),
                Map.of("name", "学习中", "value", learning),
                Map.of("name", "已完成", "value", finishedCnt)));
        return out;
    }

    /** 拖期风险清单：按计划维度列出完成率低且接近截止的计划 */
    public List<Map<String, Object>> riskPlans() {
        LoginHelper.requireManager();
        List<TrainPlan> plans = planMapper.selectList(new LambdaQueryWrapper<TrainPlan>()
                .in(TrainPlan::getStatus, List.of("PUBLISHED", "RUNNING")));
        List<Map<String, Object>> out = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (TrainPlan p : plans) {
            BigDecimal rate = planService.finishRate(p.getId());
            if (p.getEndDate() == null || p.getStartDate() == null) {
                continue;
            }
            long total = p.getEndDate().toEpochDay() - p.getStartDate().toEpochDay();
            long passed = today.toEpochDay() - p.getStartDate().toEpochDay();
            if (total <= 0) {
                continue;
            }
            double elapsed = Math.min(1.0, Math.max(0.0, passed * 1.0 / total));
            double gapRisk = rate.doubleValue() - elapsed * 100;
            if (gapRisk < -20) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("planId", p.getId());
                m.put("planName", p.getPlanName());
                m.put("endDate", p.getEndDate());
                m.put("finishRate", rate);
                m.put("timeProgress", BigDecimal.valueOf(elapsed * 100).setScale(1, RoundingMode.HALF_UP));
                m.put("riskLevel", gapRisk < -40 ? "高" : "中");
                m.put("riskValue", BigDecimal.valueOf(-gapRisk).setScale(1, RoundingMode.HALF_UP));
                out.add(m);
            }
        }
        out.sort((a, b) -> ((BigDecimal) b.get("riskValue")).compareTo((BigDecimal) a.get("riskValue")));
        return out;
    }
}
