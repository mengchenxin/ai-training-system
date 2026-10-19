package com.aitraining.module.train;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aitraining.common.BizException;
import com.aitraining.common.PageResult;
import com.aitraining.module.skill.entity.Skill;
import com.aitraining.module.skill.mapper.SkillMapper;
import com.aitraining.module.system.entity.Post;
import com.aitraining.module.system.entity.SysDept;
import com.aitraining.module.system.entity.SysUser;
import com.aitraining.module.system.mapper.PostMapper;
import com.aitraining.module.system.mapper.SysDeptMapper;
import com.aitraining.module.system.mapper.SysUserMapper;
import com.aitraining.module.train.dto.PlanDetailVO;
import com.aitraining.module.train.dto.PlanForm;
import com.aitraining.module.train.entity.Course;
import com.aitraining.module.train.entity.PlanCourse;
import com.aitraining.module.train.entity.TrainPlan;
import com.aitraining.module.train.entity.TrainProgress;
import com.aitraining.module.train.mapper.CourseMapper;
import com.aitraining.module.train.mapper.PlanCourseMapper;
import com.aitraining.module.train.mapper.TrainPlanMapper;
import com.aitraining.module.train.mapper.TrainProgressMapper;
import com.aitraining.security.LoginHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TrainPlanService {

    private final TrainPlanMapper planMapper;
    private final PlanCourseMapper planCourseMapper;
    private final CourseMapper courseMapper;
    private final TrainProgressMapper progressMapper;
    private final SysUserMapper userMapper;
    private final SysDeptMapper deptMapper;
    private final PostMapper postMapper;
    private final SkillMapper skillMapper;

    /** 状态流转白名单 */
    private static final Map<String, List<String>> TRANSITIONS = Map.of(
            "DRAFT", List.of("PENDING", "CANCELED"),
            "PENDING", List.of("PUBLISHED", "DRAFT", "CANCELED"),
            "PUBLISHED", List.of("RUNNING", "CANCELED"),
            "RUNNING", List.of("FINISHED", "CANCELED"),
            "FINISHED", List.of(),
            "CANCELED", List.of()
    );

    public PageResult<Map<String, Object>> page(long current, long size, String status,
                                                String keyword, String planType) {
        LambdaQueryWrapper<TrainPlan> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(status)) {
            w.eq(TrainPlan::getStatus, status);
        }
        if (StringUtils.hasText(planType)) {
            w.eq(TrainPlan::getPlanType, planType);
        }
        if (StringUtils.hasText(keyword)) {
            w.and(q -> q.like(TrainPlan::getPlanName, keyword).or().like(TrainPlan::getPlanNo, keyword));
        }
        // 员工只能看到与自身相关的计划
        if (LoginHelper.isEmployee()) {
            Long uid = LoginHelper.userId();
            SysUser me = userMapper.selectById(uid);
            w.and(q -> q.eq(TrainPlan::getTargetUserId, uid)
                    .or(x -> x.eq(TrainPlan::getPlanType, "DEPT").eq(TrainPlan::getTargetDeptId, me.getDeptId()))
                    .or(x -> x.eq(TrainPlan::getPlanType, "POST").eq(TrainPlan::getTargetPostId, me.getPostId()))
                    .or(x -> x.eq(TrainPlan::getPlanType, "BATCH")
                            .eq(TrainPlan::getTargetDeptId, me.getDeptId())
                            .eq(TrainPlan::getTargetPostId, me.getPostId())));
        }
        w.orderByDesc(TrainPlan::getId);
        Page<TrainPlan> p = planMapper.selectPage(new Page<>(current, size), w);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (TrainPlan plan : p.getRecords()) {
            rows.add(toRow(plan));
        }
        return PageResult.of(rows, p.getTotal(), current, size);
    }

    private Map<String, Object> toRow(TrainPlan plan) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", plan.getId());
        m.put("planNo", plan.getPlanNo());
        m.put("planName", plan.getPlanName());
        m.put("planType", plan.getPlanType());
        m.put("status", plan.getStatus());
        m.put("genType", plan.getGenType());
        m.put("startDate", plan.getStartDate());
        m.put("endDate", plan.getEndDate());
        m.put("createTime", plan.getCreateTime());
        m.put("targetUserName", plan.getTargetUserId() == null ? null
                : Optional.ofNullable(userMapper.selectById(plan.getTargetUserId()))
                .map(SysUser::getRealName).orElse(null));
        m.put("targetDeptName", plan.getTargetDeptId() == null ? null
                : Optional.ofNullable(deptMapper.selectById(plan.getTargetDeptId()))
                .map(SysDept::getDeptName).orElse(null));
        m.put("targetPostName", plan.getTargetPostId() == null ? null
                : Optional.ofNullable(postMapper.selectById(plan.getTargetPostId()))
                .map(Post::getPostName).orElse(null));
        m.put("courseCount", planCourseMapper.selectCount(
                new LambdaQueryWrapper<PlanCourse>().eq(PlanCourse::getPlanId, plan.getId())));
        BigDecimal rate = finishRate(plan.getId());
        m.put("finishRate", rate);
        m.put("risk", isRisk(plan, rate));
        return m;
    }

    /** 拖期风险判定：未结束、完成率低于 60% 且已过计划周期 70% */
    private boolean isRisk(TrainPlan plan, BigDecimal rate) {
        if (!"RUNNING".equals(plan.getStatus()) && !"PUBLISHED".equals(plan.getStatus())) {
            return false;
        }
        if (plan.getStartDate() == null || plan.getEndDate() == null) {
            return false;
        }
        long total = plan.getEndDate().toEpochDay() - plan.getStartDate().toEpochDay();
        long passed = java.time.LocalDate.now().toEpochDay() - plan.getStartDate().toEpochDay();
        if (total <= 0) {
            return false;
        }
        double elapsed = Math.min(1.0, Math.max(0.0, passed * 1.0 / total));
        return rate.doubleValue() < 60 && elapsed >= 0.7;
    }

    /** 计划完成率 = 所有进度记录完成百分比的平均值 */
    public BigDecimal finishRate(Long planId) {
        List<TrainProgress> list = progressMapper.selectList(
                new LambdaQueryWrapper<TrainProgress>().eq(TrainProgress::getPlanId, planId));
        if (list.isEmpty()) {
            return BigDecimal.ZERO;
        }
        double avg = list.stream().mapToDouble(x -> x.getProgressPercent() == null
                ? 0 : x.getProgressPercent().doubleValue()).average().orElse(0);
        return BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP);
    }

    public PlanDetailVO detail(Long id) {
        TrainPlan plan = planMapper.selectById(id);
        if (plan == null) {
            throw new BizException("培训计划不存在");
        }
        if (LoginHelper.isEmployee()) {
            List<SysUser> targets = resolveTargetUsers(plan);
            boolean related = targets.stream().anyMatch(u -> u.getId().equals(LoginHelper.userId()));
            if (!related) {
                throw new BizException(403, "无权查看该计划");
            }
        }
        PlanDetailVO vo = new PlanDetailVO();
        vo.setPlan(plan);
        if (plan.getTargetUserId() != null) {
            SysUser u = userMapper.selectById(plan.getTargetUserId());
            vo.setTargetUserName(u == null ? null : u.getRealName());
        }
        if (plan.getTargetDeptId() != null) {
            SysDept d = deptMapper.selectById(plan.getTargetDeptId());
            vo.setTargetDeptName(d == null ? null : d.getDeptName());
        }
        if (plan.getTargetPostId() != null) {
            Post p = postMapper.selectById(plan.getTargetPostId());
            vo.setTargetPostName(p == null ? null : p.getPostName());
        }
        if (plan.getCreatorId() != null) {
            SysUser c = userMapper.selectById(plan.getCreatorId());
            vo.setCreatorName(c == null ? null : c.getRealName());
        }
        List<PlanCourse> courses = planCourseMapper.selectList(
                new LambdaQueryWrapper<PlanCourse>().eq(PlanCourse::getPlanId, id)
                        .orderByAsc(PlanCourse::getSeqNo));
        vo.setCourses(courses);

        Set<Long> courseIds = courses.stream().map(PlanCourse::getCourseId).collect(Collectors.toSet());
        Map<Long, Course> courseMap = courseIds.isEmpty() ? Map.of()
                : courseMapper.selectBatchIds(courseIds).stream()
                .collect(Collectors.toMap(Course::getId, c -> c));
        Set<Long> skillIds = courses.stream().map(PlanCourse::getCoverSkillId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> skillNames = skillIds.isEmpty() ? Map.of()
                : skillMapper.selectBatchIds(skillIds).stream()
                .collect(Collectors.toMap(Skill::getId, Skill::getSkillName));

        List<TrainProgress> progresses = progressMapper.selectList(
                new LambdaQueryWrapper<TrainProgress>().eq(TrainProgress::getPlanId, id));
        Map<Long, List<TrainProgress>> byCourse = progresses.stream()
                .collect(Collectors.groupingBy(TrainProgress::getCourseId));

        List<Map<String, Object>> stats = new ArrayList<>();
        for (PlanCourse pc : courses) {
            Course c = courseMap.get(pc.getCourseId());
            List<TrainProgress> list = byCourse.getOrDefault(pc.getCourseId(), List.of());
            double avg = list.isEmpty() ? 0 : list.stream()
                    .mapToDouble(x -> x.getProgressPercent() == null ? 0 : x.getProgressPercent().doubleValue())
                    .average().orElse(0);
            long finished = list.stream().filter(x -> "FINISHED".equals(x.getStatus())).count();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("planCourseId", pc.getId());
            m.put("courseId", pc.getCourseId());
            m.put("courseName", c == null ? "" : c.getCourseName());
            m.put("durationHours", c == null ? null : c.getDurationHours());
            m.put("seqNo", pc.getSeqNo());
            m.put("expectHours", pc.getExpectHours());
            m.put("coverSkillName", skillNames.get(pc.getCoverSkillId()));
            m.put("gapBefore", pc.getGapBefore());
            m.put("planReason", pc.getPlanReason());
            m.put("avgProgress", BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP));
            m.put("finishedCount", finished);
            m.put("totalCount", list.size());
            stats.add(m);
        }
        vo.setCourseStats(stats);
        vo.setFinishRate(finishRate(id));
        vo.setUserCount(progresses.stream().map(TrainProgress::getUserId).distinct().toList().size());
        vo.setFinishedUserCount((int) progresses.stream()
                .filter(p -> "FINISHED".equals(p.getStatus()))
                .map(TrainProgress::getUserId).distinct().count());
        return vo;
    }

    @Transactional
    public Long save(PlanForm form, Long creatorId) {
        LoginHelper.requireManager();
        boolean isNew = form.getId() == null;
        TrainPlan plan;
        if (isNew) {
            plan = new TrainPlan();
            plan.setPlanNo(StringUtils.hasText(form.getPlanNo()) ? form.getPlanNo()
                    : "TP" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
            plan.setStatus("DRAFT");
            plan.setCreatorId(creatorId);
        } else {
            plan = planMapper.selectById(form.getId());
            if (plan == null) {
                throw new BizException("计划不存在");
            }
            if (!List.of("DRAFT", "PENDING").contains(plan.getStatus())) {
                throw new BizException("仅草稿或待审状态的计划可编辑");
            }
        }
        plan.setPlanName(form.getPlanName());
        plan.setPlanType(StringUtils.hasText(form.getPlanType()) ? form.getPlanType() : "PERSONAL");
        plan.setTargetUserId(form.getTargetUserId());
        plan.setTargetDeptId(form.getTargetDeptId());
        plan.setTargetPostId(form.getTargetPostId());
        plan.setStartDate(form.getStartDate());
        plan.setEndDate(form.getEndDate());
        plan.setGenType(StringUtils.hasText(form.getGenType()) ? form.getGenType() : "MANUAL");
        plan.setAiRecordId(form.getAiRecordId());
        validateTarget(plan);

        if (isNew) {
            planMapper.insert(plan);
        } else {
            planMapper.updateById(plan);
        }

        if (form.getCourses() != null) {
            planCourseMapper.delete(new LambdaQueryWrapper<PlanCourse>()
                    .eq(PlanCourse::getPlanId, plan.getId()));
            int seq = 1;
            for (PlanForm.PlanCourseForm cf : form.getCourses()) {
                PlanCourse pc = new PlanCourse();
                pc.setPlanId(plan.getId());
                pc.setCourseId(cf.getCourseId());
                pc.setSeqNo(cf.getSeqNo() == null ? seq : cf.getSeqNo());
                pc.setExpectHours(cf.getExpectHours());
                pc.setCoverSkillId(cf.getCoverSkillId());
                pc.setGapBefore(cf.getGapBefore());
                pc.setPlanReason(cf.getPlanReason());
                planCourseMapper.insert(pc);
                seq++;
            }
        }
        return plan.getId();
    }

    private void validateTarget(TrainPlan plan) {
        switch (plan.getPlanType()) {
            case "PERSONAL" -> {
                if (plan.getTargetUserId() == null) {
                    throw new BizException("个人计划必须指定目标员工");
                }
            }
            case "DEPT" -> {
                if (plan.getTargetDeptId() == null) {
                    throw new BizException("部门计划必须指定目标部门");
                }
            }
            case "POST" -> {
                if (plan.getTargetPostId() == null) {
                    throw new BizException("岗位计划必须指定目标岗位");
                }
            }
            case "BATCH" -> {
                if (plan.getTargetDeptId() == null || plan.getTargetPostId() == null) {
                    throw new BizException("批量计划需同时指定部门与岗位");
                }
            }
            default -> throw new BizException("非法的计划类型");
        }
    }

    /** 解析计划覆盖的员工 */
    public List<SysUser> resolveTargetUsers(TrainPlan plan) {
        LambdaQueryWrapper<SysUser> w = new LambdaQueryWrapper<>();
        w.eq(SysUser::getStatus, 1);
        switch (plan.getPlanType()) {
            case "PERSONAL" -> w.eq(SysUser::getId, plan.getTargetUserId());
            case "DEPT" -> w.eq(SysUser::getDeptId, plan.getTargetDeptId());
            case "POST" -> w.eq(SysUser::getPostId, plan.getTargetPostId());
            case "BATCH" -> w.eq(SysUser::getDeptId, plan.getTargetDeptId())
                    .eq(SysUser::getPostId, plan.getTargetPostId());
            default -> {
                return List.of();
            }
        }
        return userMapper.selectList(w);
    }

    @Transactional
    public void changeStatus(Long id, String target) {
        TrainPlan plan = planMapper.selectById(id);
        if (plan == null) {
            throw new BizException("计划不存在");
        }
        LoginHelper.requireManager();
        List<String> allowed = TRANSITIONS.getOrDefault(plan.getStatus(), List.of());
        if (!allowed.contains(target)) {
            throw new BizException(String.format("状态不能从 %s 直接流转到 %s", plan.getStatus(), target));
        }
        if ("PENDING".equals(target) || "CANCELED".equals(target)) {
            // 提交审核与取消需要计划有内容
            Long cnt = planCourseMapper.selectCount(
                    new LambdaQueryWrapper<PlanCourse>().eq(PlanCourse::getPlanId, id));
            if (cnt == null || cnt == 0) {
                throw new BizException("计划尚未编排课程，无法提交");
            }
        }
        plan.setStatus(target);
        if ("PUBLISHED".equals(target)) {
            plan.setAuditorId(LoginHelper.userId());
            plan.setAuditTime(LocalDateTime.now());
            planMapper.updateById(plan);
            initProgress(plan);
            return;
        }
        planMapper.updateById(plan);
    }

    /** 发布时按目标人群 × 计划课程生成进度记录 */
    @Transactional
    public void initProgress(TrainPlan plan) {
        List<SysUser> users = resolveTargetUsers(plan);
        List<PlanCourse> courses = planCourseMapper.selectList(
                new LambdaQueryWrapper<PlanCourse>().eq(PlanCourse::getPlanId, plan.getId()));
        for (SysUser u : users) {
            for (PlanCourse pc : courses) {
                Long exists = progressMapper.selectCount(new LambdaQueryWrapper<TrainProgress>()
                        .eq(TrainProgress::getPlanId, plan.getId())
                        .eq(TrainProgress::getCourseId, pc.getCourseId())
                        .eq(TrainProgress::getUserId, u.getId()));
                if (exists != null && exists > 0) {
                    continue;
                }
                TrainProgress tp = new TrainProgress();
                tp.setPlanId(plan.getId());
                tp.setPlanCourseId(pc.getId());
                tp.setCourseId(pc.getCourseId());
                tp.setUserId(u.getId());
                tp.setProgressPercent(BigDecimal.ZERO);
                tp.setStatus("NOT_STARTED");
                tp.setSpentHours(BigDecimal.ZERO);
                progressMapper.insert(tp);
            }
        }
    }

    @Transactional
    public void remove(Long id) {
        LoginHelper.requireManager();
        TrainPlan plan = planMapper.selectById(id);
        if (plan == null) {
            return;
        }
        if (!List.of("DRAFT", "CANCELED").contains(plan.getStatus()) && !LoginHelper.isAdmin()) {
            throw new BizException("仅草稿或已取消的计划可删除");
        }
        planMapper.deleteById(id);
        planCourseMapper.delete(new LambdaQueryWrapper<PlanCourse>().eq(PlanCourse::getPlanId, id));
        progressMapper.delete(new LambdaQueryWrapper<TrainProgress>().eq(TrainProgress::getPlanId, id));
    }
}
