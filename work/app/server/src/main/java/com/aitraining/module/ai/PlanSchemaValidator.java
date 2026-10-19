package com.aitraining.module.ai;

import com.aitraining.module.ai.dto.PlanGenResultVO;
import com.aitraining.module.ai.dto.PlannedCourse;
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
