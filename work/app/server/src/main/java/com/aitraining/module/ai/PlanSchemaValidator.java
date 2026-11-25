package com.aitraining.module.ai;

import com.aitraining.module.ai.dto.PlannedCourse;
import com.aitraining.module.train.entity.Course;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 大模型输出的结构化校验。
 *
 * <p>应对「模型幻觉」的核心手段：模型只能从规则引擎召回的候选课程中选择，
 * 凡超出候选集合、学时异常、字段缺失的输出一律判为不合规，
 * 由调用方触发重试或降级为规则方案。
 * 校验结果同时写入 ai_gen_record，作为「结构合规率」指标的统计口径。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlanSchemaValidator {

    public static class ValidationResult {
        public boolean valid;
        public String message;
        public String summary;
        public List<PlannedCourse> courses = new ArrayList<>();
        /** 宽松校验时：模型中出现的、课程库中不存在的课程名（幻觉） */
        public List<String> hallucinatedCourses = new ArrayList<>();

        public int hallucinationCount() {
            return hallucinatedCourses.size();
        }
    }

    public ValidationResult validate(Object parsed, List<PlannedCourse> candidates) {
        ValidationResult r = new ValidationResult();
        if (parsed == null) {
            r.message = "输出为空";
            return r;
        }
        if (!(parsed instanceof Map<?, ?> root)) {
            r.message = "输出不是 JSON 对象";
            return r;
        }
        Object summaryObj = root.get("summary");
        Object coursesObj = root.get("courses");
        if (!(coursesObj instanceof List<?> list) || list.isEmpty()) {
            r.message = "缺少 courses 数组或为空";
            return r;
        }
        r.summary = summaryObj == null ? null : String.valueOf(summaryObj);

        Map<Long, PlannedCourse> allowed = new ConcurrentHashMap<>();
        candidates.forEach(c -> allowed.put(c.getCourseId(), c));

        int seq = 1;
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m)) {
                r.message = "courses 元素不是对象";
                return r;
            }
            Long courseId = asLong(m.get("courseId"));
            if (courseId == null || !allowed.containsKey(courseId)) {
                r.message = "存在候选清单之外的课程 courseId=" + m.get("courseId");
                return r;
            }
            PlannedCourse base = allowed.get(courseId);
            if (r.courses.stream().anyMatch(x -> x.getCourseId().equals(courseId))) {
                r.message = "课程重复：" + courseId;
                return r;
            }
            PlannedCourse pc = new PlannedCourse();
            pc.setCourseId(courseId);
            pc.setCourseName(base.getCourseName());
            pc.setCoverSkill(base.getCoverSkill());
            pc.setCoverSkillId(base.getCoverSkillId());
            pc.setGapBefore(base.getGapBefore());
            pc.setCourseLevel(base.getCourseLevel());
            pc.setPriorityScore(base.getPriorityScore());

            Object hours = m.get("hours");
            if (hours instanceof Number n) {
                double h = n.doubleValue();
                if (h <= 0 || h > base.getHours().doubleValue() * 2) {
                    r.message = "课程 " + courseId + " 学时不符合要求：" + h;
                    return r;
                }
                pc.setHours(java.math.BigDecimal.valueOf(h));
            } else {
                pc.setHours(base.getHours());
            }
            Object s = m.get("seqNo");
            pc.setSeqNo(s instanceof Number n ? n.intValue() : seq);
            Object reason = m.get("reason");
            String rs = reason == null ? "" : String.valueOf(reason).trim();
            pc.setReason(rs.isEmpty() ? base.getReason() : rs);
            r.courses.add(pc);
            seq++;
        }
        // 学时不超预算的粗校验
        double total = r.courses.stream().mapToDouble(c -> c.getHours().doubleValue()).sum();
        if (total > 500) {
            r.message = "总学时不合理：" + total;
            return r;
        }
        r.valid = true;
        r.message = "ok";
        return r;
    }

    /**
     * 宽松校验 —— 用于「纯大模型」对照组。
     *
     * <p>与严格校验的区别：不再要求课程必须落在规则引擎的候选集内，
     * 只要求 JSON 结构可用、课程能在<b>完整课程库</b>中匹配上。
     * 匹配不上的课程不计为整体失败，而是计入 {@code hallucinatedCourses}，
     * 作为「幻觉课程数」这一量化指标——这正是约束（严格校验）价值的反面证据。
     *
     * <p>匹配顺序：courseId 优先，其次按课程名精确匹配（模型中常见只给名字的写法）。
     */
    public ValidationResult validateLenient(Object parsed, List<Course> catalog,
                                            Map<Long, String> skillNames) {
        ValidationResult r = new ValidationResult();
        if (parsed == null) {
            r.message = "输出为空";
            return r;
        }
        if (!(parsed instanceof Map<?, ?> root)) {
            r.message = "输出不是 JSON 对象";
            return r;
        }
        Object summaryObj = root.get("summary");
        Object coursesObj = root.get("courses");
        if (!(coursesObj instanceof List<?> list) || list.isEmpty()) {
            r.message = "缺少 courses 数组或为空";
            return r;
        }
        r.summary = summaryObj == null ? null : String.valueOf(summaryObj);

        Map<Long, Course> byId = new java.util.HashMap<>();
        Map<String, Course> byName = new java.util.HashMap<>();
        for (Course c : catalog) {
            byId.put(c.getId(), c);
            byName.put(normalize(c.getCourseName()), c);
        }

        int seq = 1;
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m)) {
                r.message = "courses 元素不是对象";
                return r;
            }
            String rawName = m.get("courseName") == null ? null : String.valueOf(m.get("courseName")).trim();
            Course matched = null;
            Long courseId = asLong(m.get("courseId"));
            if (courseId != null) {
                matched = byId.get(courseId);
            }
            if (matched == null && rawName != null && !rawName.isBlank()) {
                matched = byName.get(normalize(rawName));
            }
            if (matched == null) {
                // 课程库里没有 —— 记为幻觉，不中断整份方案
                r.hallucinatedCourses.add(rawName == null || rawName.isBlank()
                        ? "courseId=" + m.get("courseId") : rawName);
                continue;
            }
            final Course hit = matched;
            if (r.courses.stream().anyMatch(x -> x.getCourseId().equals(hit.getId()))) {
                continue;
            }
            PlannedCourse pc = new PlannedCourse();
            pc.setCourseId(matched.getId());
            pc.setCourseName(matched.getCourseName());
            pc.setCoverSkillId(matched.getSkillId());
            pc.setCoverSkill(skillNames == null ? null : skillNames.get(matched.getSkillId()));
            pc.setCourseLevel(matched.getLevel());
            pc.setGapBefore(0);

            java.math.BigDecimal base = matched.getDurationHours() == null
                    ? java.math.BigDecimal.valueOf(4) : matched.getDurationHours();
            Object hours = m.get("hours");
            if (hours instanceof Number n) {
                double h = n.doubleValue();
                // 学时给得离谱就回退到课程库标准学时，避免模型虚报
                pc.setHours(h > 0 && h <= 400 ? java.math.BigDecimal.valueOf(h) : base);
            } else {
                pc.setHours(base);
            }
            Object s = m.get("seqNo");
            pc.setSeqNo(s instanceof Number n ? n.intValue() : seq);
            Object reason = m.get("reason");
            String rs = reason == null ? "" : String.valueOf(reason).trim();
            pc.setReason(rs.isEmpty() ? "由大模型直接推荐（未经过规则候选集）" : rs);
            r.courses.add(pc);
            seq++;
        }

        if (r.courses.isEmpty()) {
            r.message = "全部输出均无法在课程库中匹配，幻觉课程 " + r.hallucinatedCourses.size() + " 门";
            return r;
        }
        double total = r.courses.stream().mapToDouble(c -> c.getHours().doubleValue()).sum();
        r.valid = true;
        r.message = "ok（幻觉课程 " + r.hallucinatedCourses.size() + " 门）";
        // 总学时超限不判失败，交由调用方在方案说明中提示；对照组本就允许不守预算
        if (total > 500) {
            r.message = r.message + "，总学时异常 " + total;
        }
        return r;
    }

    private static String normalize(String name) {
        if (name == null) {
            return "";
        }
        return name.replaceAll("[\\s《》\"'（）()、,，.。]", "").toLowerCase();
    }

    private Long asLong(Object o) {
        if (o instanceof Number n) {
            return n.longValue();
        }
        if (o instanceof String s && s.matches("\\d+")) {
            return Long.parseLong(s);
        }
        return null;
    }
}
