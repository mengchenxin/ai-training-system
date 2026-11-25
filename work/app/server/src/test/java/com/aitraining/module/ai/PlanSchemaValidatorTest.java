package com.aitraining.module.ai;

import com.aitraining.module.ai.dto.PlannedCourse;
import com.aitraining.module.train.entity.Course;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 结构化校验单元测试。
 *
 * <p>严格校验（混合模式）与宽松校验（纯大模型对照组的幻觉统计）是两种相反的策略：
 * 前者宁可判失败也不接受越界课程，后者放行但把越界课程计入幻觉数。
 * 这两条路径的正确性直接决定「约束有没有用」这组实验能否成立，必须逐条测。
 */
class PlanSchemaValidatorTest {

    private final PlanSchemaValidator validator = new PlanSchemaValidator();

    private PlannedCourse candidate(long id, String name, int level, double hours) {
        PlannedCourse c = new PlannedCourse();
        c.setCourseId(id);
        c.setCourseName(name);
        c.setCoverSkill("Java");
        c.setCoverSkillId(100L);
        c.setCourseLevel(level);
        c.setGapBefore(2);
        c.setHours(BigDecimal.valueOf(hours));
        c.setPriorityScore(BigDecimal.valueOf(9.5));
        c.setReason("规则理由");
        return c;
    }

    private Course course(long id, String name, int level, double hours) {
        Course c = new Course();
        c.setId(id);
        c.setCourseName(name);
        c.setSkillId(100L);
        c.setLevel(level);
        c.setDurationHours(BigDecimal.valueOf(hours));
        c.setCategory("技术");
        c.setStatus(1);
        return c;
    }

    private Map<String, Object> payload(String summary, List<Map<String, Object>> courses) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("summary", summary);
        m.put("courses", courses);
        return m;
    }

    private Map<String, Object> item(Object courseId, Object hours, String reason) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (courseId != null) {
            m.put("courseId", courseId);
        }
        if (hours != null) {
            m.put("hours", hours);
        }
        if (reason != null) {
            m.put("reason", reason);
        }
        return m;
    }

    // ---------------- 严格校验 ----------------

    @Test
    @DisplayName("合法输出：通过，且课程名等属性以候选集为准而不是模型给的")
    void strictValid() {
        List<PlannedCourse> candidates = List.of(candidate(1, "Java 基础", 1, 8), candidate(2, "Java 进阶", 2, 12));
        PlanSchemaValidator.ValidationResult r = validator.validate(
                payload("方案说明", List.of(item(1, 8, "先补基础"), item(2, 12, "再补进阶"))), candidates);

        assertThat(r.valid).isTrue();
        assertThat(r.courses).hasSize(2);
        assertThat(r.courses.get(0).getCourseName()).isEqualTo("Java 基础");
        assertThat(r.courses.get(0).getReason()).isEqualTo("先补基础");
        assertThat(r.summary).isEqualTo("方案说明");
    }

    @Test
    @DisplayName("模型漏给学时：回退到候选集的标准学时")
    void strictMissingHoursFallsBack() {
        PlanSchemaValidator.ValidationResult r = validator.validate(
                payload(null, List.of(item(1, null, null))), List.of(candidate(1, "Java 基础", 1, 8)));
        assertThat(r.valid).isTrue();
        assertThat(r.courses.get(0).getHours()).isEqualByComparingTo("8");
        assertThat(r.courses.get(0).getReason()).isEqualTo("规则理由");
    }

    @Test
    @DisplayName("课程不在候选集内：判失败并指出 courseId")
    void strictRejectsOutsideCandidate() {
        PlanSchemaValidator.ValidationResult r = validator.validate(
                payload(null, List.of(item(99, 8, "编造的课"))), List.of(candidate(1, "Java 基础", 1, 8)));
        assertThat(r.valid).isFalse();
        assertThat(r.message).contains("候选清单之外").contains("99");
    }

    @Test
    @DisplayName("课程重复：判失败")
    void strictRejectsDuplicate() {
        PlanSchemaValidator.ValidationResult r = validator.validate(
                payload(null, List.of(item(1, 8, null), item(1, 8, null))),
                List.of(candidate(1, "Java 基础", 1, 8)));
        assertThat(r.valid).isFalse();
        assertThat(r.message).contains("重复");
    }

    @Test
    @DisplayName("学时超过两倍标准学时：判失败，防止模型虚报")
    void strictRejectsUnreasonableHours() {
        PlanSchemaValidator.ValidationResult r = validator.validate(
                payload(null, List.of(item(1, 100, null))), List.of(candidate(1, "Java 基础", 1, 8)));
        assertThat(r.valid).isFalse();
        assertThat(r.message).contains("学时不符合要求");
    }

    @Test
    @DisplayName("courses 缺失或为空：判失败")
    void strictRejectsEmptyCourses() {
        assertThat(validator.validate(payload(null, List.of()), List.of(candidate(1, "A", 1, 8))).valid).isFalse();
        Map<String, Object> noCourses = new LinkedHashMap<>();
        noCourses.put("summary", "只有说明");
        assertThat(validator.validate(noCourses, List.of(candidate(1, "A", 1, 8))).valid).isFalse();
    }

    @Test
    @DisplayName("输出不是 JSON 对象或为 null：判失败而不是抛异常")
    void strictRejectsNonObject() {
        assertThat(validator.validate(null, List.of()).valid).isFalse();
        assertThat(validator.validate("不是对象", List.of()).valid).isFalse();
    }

    @Test
    @DisplayName("总学时超过 500：判失败")
    void strictRejectsHugeTotal() {
        List<PlannedCourse> candidates = new ArrayList<>();
        List<Map<String, Object>> items = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            candidates.add(candidate(i, "课" + i, 1, 100));
            items.add(item(i, 200, null));
        }
        PlanSchemaValidator.ValidationResult r = validator.validate(payload(null, items), candidates);
        assertThat(r.valid).isFalse();
        assertThat(r.message).contains("总学时不合理");
    }

    // ---------------- 宽松校验（纯大模型组） ----------------

    @Test
    @DisplayName("宽松校验：课程库外的课程计入幻觉数并被剔除，其余照常返回")
    void lenientCountsHallucination() {
        List<Course> catalog = List.of(course(1, "Java 基础", 1, 8), course(2, "Java 进阶", 2, 12));
        Map<String, Object> row1 = item(1, 8, "打基础");
        Map<String, Object> row2 = new LinkedHashMap<>();
        row2.put("courseName", "量子计算实战");  // 课程库里没有
        row2.put("hours", 10);
        List<Map<String, Object>> rows = List.of(row1, row2);

        PlanSchemaValidator.ValidationResult r = validator.validateLenient(payload("自由方案", rows), catalog, Map.of(100L, "Java"));

        assertThat(r.valid).isTrue();
        assertThat(r.courses).hasSize(1);
        assertThat(r.hallucinationCount()).isEqualTo(1);
        assertThat(r.hallucinatedCourses).containsExactly("量子计算实战");
        assertThat(r.message).contains("幻觉课程 1 门");
    }

    @Test
    @DisplayName("宽松校验：只给课程名也能匹配上课程库")
    void lenientMatchesByName() {
        List<Course> catalog = List.of(course(7, "数据结构与算法", 2, 16));
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("courseName", "《数据结构与算法》");
        row.put("hours", 12);
        row.put("reason", "补齐算法能力");
        PlanSchemaValidator.ValidationResult r = validator.validateLenient(
                payload(null, List.of(row)), catalog, Map.of(100L, "算法"));

        assertThat(r.valid).isTrue();
        assertThat(r.courses).hasSize(1);
        assertThat(r.courses.get(0).getCourseId()).isEqualTo(7L);
        assertThat(r.courses.get(0).getCourseName()).isEqualTo("数据结构与算法");
        assertThat(r.courses.get(0).getCoverSkill()).isEqualTo("算法");
        assertThat(r.hallucinationCount()).isZero();
    }

    @Test
    @DisplayName("宽松校验：学时给得离谱时回退到标准学时")
    void lenientClampsHours() {
        List<Course> catalog = List.of(course(1, "Java 基础", 1, 8));
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("courseId", 1);
        row.put("hours", 9999);
        PlanSchemaValidator.ValidationResult r = validator.validateLenient(
                payload(null, List.of(row)), catalog, Map.of());
        assertThat(r.valid).isTrue();
        assertThat(r.courses.get(0).getHours()).isEqualByComparingTo("8");
    }

    @Test
    @DisplayName("宽松校验：全部幻觉时判失败，不硬凑方案")
    void lenientAllHallucinatedFails() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("courseName", "火星殖民实务");
        row.put("hours", 6);
        PlanSchemaValidator.ValidationResult r = validator.validateLenient(
                payload(null, List.of(row)), List.of(course(1, "Java 基础", 1, 8)), Map.of());

        assertThat(r.valid).isFalse();
        assertThat(r.courses).isEmpty();
        assertThat(r.hallucinationCount()).isEqualTo(1);
        assertThat(r.message).contains("幻觉课程 1 门");
    }

    @Test
    @DisplayName("宽松校验：重复课程只保留一次，且不计入幻觉")
    void lenientDeduplicates() {
        List<Course> catalog = List.of(course(1, "Java 基础", 1, 8));
        PlanSchemaValidator.ValidationResult r = validator.validateLenient(
                payload(null, List.of(item(1, 8, null), item(1, 8, null))), catalog, Map.of());
        assertThat(r.courses).hasSize(1);
        assertThat(r.hallucinationCount()).isZero();
    }
}
