package com.aitraining.module.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.aitraining.module.ai.dto.PlannedCourse;
import com.aitraining.module.skill.dto.GapItemVO;
import com.aitraining.module.skill.dto.GapResultVO;
import com.aitraining.module.train.entity.Course;
import com.aitraining.module.train.mapper.CourseMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

/**
 * 规则排期引擎 —— 混合策略中「可解释、可保证合规」的那一半。
 *
 * <p>职责：把技能差距表确定性地映射为「候选课程 + 学习顺序 + 学时」，
 * 大模型只负责在此基础上润色方案说明与安排理由，不得凭空增加课程。
 * 这样即使模型输出的说明再自由，课程集合与优先级仍由量化规则保证，
 * 且模型完全不可用时系统仍能给出可用方案（降级路径）。
 *
 * <p>选课规则：
 * <ol>
 *   <li>按加权差距降序遍历技能项（已在差距分析中排好序）；</li>
 *   <li>对每个技能项，从其课程库中挑选「课程等级最接近岗位目标等级」的课程；</li>
 *   <li>若差距 ≥ 2 级且该技能有 2 门以上课程，则同时安排基础课与进阶课；</li>
 *   <li>累计学时超出预算即停止，保证方案可执行。</li>
 * </ol>
 */
@Component
@RequiredArgsConstructor
public class RulePlanEngine {

    private final CourseMapper courseMapper;

    /** 默认学时预算 */
    public static final BigDecimal DEFAULT_BUDGET = BigDecimal.valueOf(40);

    public List<PlannedCourse> plan(GapResultVO gap, BigDecimal budget) {
        if (budget == null || budget.compareTo(BigDecimal.ZERO) <= 0) {
            budget = DEFAULT_BUDGET;
        }
        List<GapItemVO> gapItems = gap.getItems().stream()
                .filter(i -> i.getGap() != null && i.getGap() > 0)
                .toList();
        if (gapItems.isEmpty()) {
            return List.of();
        }
        Set<Long> skillIds = gapItems.stream().map(GapItemVO::getSkillId).collect(java.util.stream.Collectors.toSet());
        List<Course> all = courseMapper.selectList(new LambdaQueryWrapper<Course>()
                .in(Course::getSkillId, skillIds).eq(Course::getStatus, 1));
        Map<Long, List<Course>> bySkill = new HashMap<>();
        for (Course c : all) {
            bySkill.computeIfAbsent(c.getSkillId(), k -> new ArrayList<>()).add(c);
        }

        List<PlannedCourse> out = new ArrayList<>();
        BigDecimal used = BigDecimal.ZERO;
        Set<Long> picked = new HashSet<>();
        int seq = 1;

        for (GapItemVO item : gapItems) {
            List<Course> candidates = bySkill.get(item.getSkillId());
            if (candidates == null || candidates.isEmpty()) {
                continue;
            }
            candidates.sort(Comparator
                    .comparingInt((Course c) -> Math.abs(c.getLevel() - item.getTargetLevel()))
                    .thenComparing(Course::getLevel));
            int want = item.getGap() >= 2 && candidates.size() > 1 ? 2 : 1;
            int added = 0;
            for (Course c : candidates) {
                if (added >= want) {
                    break;
                }
                if (picked.contains(c.getId())) {
                    continue;
                }
                BigDecimal hours = c.getDurationHours() == null ? BigDecimal.valueOf(4) : c.getDurationHours();
                BigDecimal next = used.add(hours);
                if (next.compareTo(budget) > 0) {
                    continue;
                }
                used = next;
                PlannedCourse pc = new PlannedCourse();
                pc.setCourseId(c.getId());
                pc.setCourseName(c.getCourseName());
                pc.setSeqNo(seq++);
                pc.setHours(hours);
                pc.setCoverSkillId(item.getSkillId());
                pc.setCoverSkill(item.getSkillName());
                pc.setGapBefore(item.getGap());
                pc.setCourseLevel(c.getLevel());
                pc.setPriorityScore(item.getPriorityScore());
                pc.setReason(buildReason(item, c, added + 1, want));
                out.add(pc);
                picked.add(c.getId());
                added++;
            }
        }
        return out;
    }

    private String buildReason(GapItemVO item, Course c, int idx, int total) {
        String stage = total > 1 ? (idx == 1 ? "基础补齐" : "进阶强化") : "针对性补齐";
        return String.format("该技能当前 %d 级、岗位要求 %d 级，差 %d 级，权重 %.2f，"
                        + "加权差距 %.2f，在岗位差距中优先级排第 %d 位；"
                        + "《%s》覆盖等级 %d，与目标等级最接近，作为%s课程。",
                item.getCurrentLevel(), item.getTargetLevel(), item.getGap(),
                item.getWeight().doubleValue(), item.getWeightedGap().doubleValue(),
                item.getPriorityRank(), c.getCourseName(), c.getLevel(), stage);
    }
}
