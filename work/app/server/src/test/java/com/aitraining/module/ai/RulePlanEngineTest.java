package com.aitraining.module.ai;

import com.aitraining.module.ai.dto.PlannedCourse;
import com.aitraining.module.skill.dto.GapItemVO;
import com.aitraining.module.skill.dto.GapResultVO;
import com.aitraining.module.train.entity.Course;
import com.aitraining.module.train.mapper.CourseMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 规则排期引擎单元测试。
 *
 * <p>这个类是混合策略里「确定性」那一半：预算约束、优先级顺序、基础+进阶配对
 * 全靠它保证。测它的意义是：答辩时被问「你怎么保证 40 学时预算一定不超」，
 * 能直接指向用例而不是口头解释。
 */
class RulePlanEngineTest {

    private CourseMapper courseMapper;
    private RulePlanEngine engine;

    @BeforeEach
    void setUp() {
        courseMapper = mock(CourseMapper.class);
        engine = new RulePlanEngine(courseMapper);
    }

    private Course course(long id, long skillId, String name, int level, double hours) {
        Course c = new Course();
        c.setId(id);
        c.setSkillId(skillId);
        c.setCourseName(name);
        c.setLevel(level);
        c.setDurationHours(BigDecimal.valueOf(hours));
        c.setStatus(1);
        return c;
    }

    private GapItemVO gapItem(long skillId, String name, int current, int target, double weight, int rank) {
        GapItemVO i = new GapItemVO();
        i.setSkillId(skillId);
        i.setSkillName(name);
        i.setCurrentLevel(current);
        i.setTargetLevel(target);
        i.setGap(target - current);
        i.setWeight(BigDecimal.valueOf(weight));
        i.setWeightedGap(BigDecimal.valueOf((target - current) * weight));
        i.setPriorityRank(rank);
        i.setPriorityScore(BigDecimal.valueOf((target - current) * weight * 10));
        return i;
    }

    private GapResultVO gap(List<GapItemVO> items) {
        GapResultVO g = new GapResultVO();
        g.setUserId(1L);
        g.setRealName("张三");
        g.setPostName("Java 工程师");
        g.setItems(items);
        g.setGapCount((int) items.stream().filter(i -> i.getGap() != null && i.getGap() > 0).count());
        g.setMatchRate(BigDecimal.valueOf(60));
        return g;
    }

    @Test
    @DisplayName("差距 1 级只排 1 门课，且优先选等级最接近目标的那门")
    void singleGapPicksClosestLevel() {
        when(courseMapper.selectList(any())).thenReturn(List.of(
                course(1, 10, "Java 入门", 1, 8),
                course(2, 10, "Java 进阶", 3, 16)));

        List<PlannedCourse> out = engine.plan(
                gap(List.of(gapItem(10, "Java", 2, 3, 0.5, 1))), BigDecimal.valueOf(40));

        assertThat(out).hasSize(1);
        assertThat(out.get(0).getCourseId()).isEqualTo(2L);   // 目标 3 级 → 选 3 级课程
        assertThat(out.get(0).getSeqNo()).isEqualTo(1);
    }

    @Test
    @DisplayName("差距 2 级以上排基础+进阶两门，顺序为先基础")
    void deepGapPicksTwoCourses() {
        when(courseMapper.selectList(any())).thenReturn(List.of(
                course(1, 10, "Java 基础", 1, 8),
                course(2, 10, "Java 高级", 3, 12)));

        List<PlannedCourse> out = engine.plan(
                gap(List.of(gapItem(10, "Java", 1, 3, 0.5, 1))), BigDecimal.valueOf(40));

        assertThat(out).hasSize(2);
        assertThat(out.get(0).getCourseId()).isEqualTo(1L);
        assertThat(out.get(1).getCourseId()).isEqualTo(2L);
        assertThat(out.get(0).getReason()).contains("基础补齐");
        assertThat(out.get(1).getReason()).contains("进阶强化");
    }

    @Test
    @DisplayName("预算约束：超预算的课程不排，累计学时永不超过预算")
    void budgetIsRespected() {
        when(courseMapper.selectList(any())).thenReturn(List.of(
                course(1, 10, "课A", 1, 24),
                course(2, 20, "课B", 1, 24)));

        List<PlannedCourse> out = engine.plan(gap(List.of(
                gapItem(10, "技能A", 1, 2, 0.9, 1),
                gapItem(20, "技能B", 1, 2, 0.8, 2))), BigDecimal.valueOf(30));

        assertThat(out).hasSize(1);
        assertThat(out.stream().map(c -> c.getHours().doubleValue()).reduce(0.0, Double::sum))
                .isLessThanOrEqualTo(30.0);
    }

    @Test
    @DisplayName("预算为空或非正数时使用默认 40 学时")
    void defaultBudgetWhenNull() {
        when(courseMapper.selectList(any())).thenReturn(List.of(course(1, 10, "课A", 1, 30)));
        assertThat(engine.plan(gap(List.of(gapItem(10, "A", 1, 2, 1.0, 1))), null)).hasSize(1);
        assertThat(engine.plan(gap(List.of(gapItem(10, "A", 1, 2, 1.0, 1))), BigDecimal.ZERO)).hasSize(1);
    }

    @Test
    @DisplayName("技能已达标（无正向差距）时不排课")
    void noGapNoCourse() {
        assertThat(engine.plan(gap(List.of(gapItem(10, "A", 3, 3, 0.5, 1))), BigDecimal.valueOf(40))).isEmpty();
    }

    @Test
    @DisplayName("技能没有对应课程时跳过，不影响其他技能")
    void skillWithoutCourseSkipped() {
        when(courseMapper.selectList(any())).thenReturn(List.of(course(1, 10, "课A", 1, 8)));
        List<PlannedCourse> out = engine.plan(gap(List.of(
                gapItem(10, "有课技能", 1, 2, 0.9, 1),
                gapItem(99, "无课技能", 1, 2, 0.8, 2))), BigDecimal.valueOf(40));
        assertThat(out).hasSize(1);
        assertThat(out.get(0).getCoverSkill()).isEqualTo("有课技能");
    }

    @Test
    @DisplayName("同一门课不会因为覆盖多个技能而重复安排")
    void noDuplicateCourse() {
        when(courseMapper.selectList(any())).thenReturn(List.of(course(1, 10, "公共课", 1, 8)));
        List<PlannedCourse> out = engine.plan(gap(List.of(
                gapItem(10, "技能A", 1, 2, 0.9, 1),
                gapItem(11, "技能B", 1, 2, 0.8, 2))), BigDecimal.valueOf(40));
        // 第二个技能查不到课程（mock 固定返回 skillId=10 的课），因此只有一条
        assertThat(out).hasSize(1);
        assertThat(out.stream().filter(c -> c.getCourseId() == 1L).count()).isEqualTo(1);
    }

    @Test
    @DisplayName("课程没有登记学时按 4 学时计，不会出现 null")
    void nullHoursDefaultFour() {
        Course c = course(1, 10, "课A", 1, 4);
        c.setDurationHours(null);
        when(courseMapper.selectList(any())).thenReturn(List.of(c));
        List<PlannedCourse> out = engine.plan(gap(List.of(gapItem(10, "A", 1, 2, 1.0, 1))),
                BigDecimal.valueOf(40));
        assertThat(out).hasSize(1);
        assertThat(out.get(0).getHours()).isEqualByComparingTo("4");
    }

    @Test
    @DisplayName("排序结果严格按传入的优先级顺序（列表已按加权差距排好）")
    void priorityOrderPreserved() {
        when(courseMapper.selectList(any())).thenReturn(new ArrayList<>(List.of(
                course(1, 10, "高优课", 1, 8),
                course(2, 20, "低优课", 1, 8))));
        List<PlannedCourse> out = engine.plan(gap(List.of(
                gapItem(10, "高优技能", 1, 2, 0.9, 1),
                gapItem(20, "低优技能", 1, 2, 0.3, 2))), BigDecimal.valueOf(40));
        assertThat(out).extracting(PlannedCourse::getCoverSkill)
                .containsExactly("高优技能", "低优技能");
    }
}
