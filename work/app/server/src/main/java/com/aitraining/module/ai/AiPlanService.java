package com.aitraining.module.ai;

import com.aitraining.common.BizException;
import com.aitraining.module.ai.dto.PlanGenResultVO;
import com.aitraining.module.ai.dto.PlannedCourse;
import com.aitraining.module.ai.entity.AiGenRecord;
import com.aitraining.module.ai.entity.AiPromptTemplate;
import com.aitraining.module.ai.llm.LlmClient;
import com.aitraining.module.ai.llm.LlmResponse;
import com.aitraining.module.ai.mapper.AiGenRecordMapper;
import com.aitraining.module.ai.rag.RagHit;
import com.aitraining.module.ai.rag.RagService;
import com.aitraining.module.skill.SkillGapService;
import com.aitraining.module.skill.dto.GapItemVO;
import com.aitraining.module.skill.dto.GapResultVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AI 个性化培训方案生成 —— 混合策略主流程。
 *
 * <pre>
 *  技能差距表 ──▶ 规则引擎召回候选课程（确定性、可解释）
 *                      │
 *                      ├── 检索知识库（BM25/RAG）补充依据
 *                      │
 *                      ▼
 *              大模型生成方案说明与安排理由（受候选集合约束）
 *                      │
 *                      ├─ 结构化校验通过 ──▶ AI_HYBRID 方案
 *                      └─ 校验失败/接口异常 ──▶ 降级为纯规则方案（AI_RULE）
 * </pre>
 *
 * 无论哪条路径，最终产出的课程集合都由规则引擎确定，模型只影响「说明与顺序」，
 * 从根本上避免模型编造课程与学时失控。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiPlanService {

    private final SkillGapService gapService;
    private final RulePlanEngine ruleEngine;
    private final RagService ragService;
    private final LlmClient llmClient;
    private final PromptService promptService;
    private final PlanSchemaValidator validator;
    private final AiGenRecordMapper genRecordMapper;
    private final ObjectMapper objectMapper;

    public PlanGenResultVO generate(Long userId, BigDecimal budget, Boolean allowLlm) {
        long start = System.currentTimeMillis();
        GapResultVO gap = gapService.analyze(userId);
        List<PlannedCourse> ruleCourses = ruleEngine.plan(gap, budget);
        if (ruleCourses.isEmpty()) {
            throw new BizException("该员工技能已达标，或课程库中没有可覆盖其差距的课程，无需生成方案");
        }

        List<RagHit> hits = retrieve(gap);
        String ragContext = ragService.buildContext(hits);

        boolean wantLlm = allowLlm == null || allowLlm;
        PlanGenResultVO result;
        if (wantLlm && llmClient.available()) {
            result = generateWithLlm(gap, ruleCourses, ragContext, hits, budget);
        } else {
            result = fallback(gap, ruleCourses,
                    wantLlm ? "未配置大模型 API Key，已降级为规则方案" : "调用方要求使用纯规则方案");
            // 耗时必须在写记录前落到 VO 上，否则 ai_gen_record.cost_ms 会被写成 null
            result.setCostMs((int) (System.currentTimeMillis() - start));
            result.setAiRecordId(writeRecord(gap, ruleCourses, hits, null, result, 1));
        }

        result.setUserId(userId);
        result.setRealName(gap.getRealName());
        result.setPostName(gap.getPostName());
        result.setRefs(hits.stream().map(PlanGenResultVO.RefSource::of).toList());
        result.setCostMs((int) (System.currentTimeMillis() - start));
        return result;
    }

    // ---------------- 大模型路径 ----------------

    private PlanGenResultVO generateWithLlm(GapResultVO gap, List<PlannedCourse> ruleCourses,
                                            String ragContext, List<RagHit> hits, BigDecimal budget) {
        AiPromptTemplate tpl = promptService.getByScene("PLAN_GEN");
        Map<String, Object> vars = new HashMap<>();
        vars.put("realName", gap.getRealName());
        vars.put("postName", gap.getPostName());
        vars.put("deptName", Objects.requireNonNullElse(gap.getDeptName(), "-"));
        vars.put("gapTable", gap.getGapSummary());
        vars.put("courseCandidates", buildCandidateText(ruleCourses));
        vars.put("ragContext", ragContext);
        vars.put("totalHours", budget == null ? RulePlanEngine.DEFAULT_BUDGET : budget);

        String prompt = tpl != null
                ? PromptService.render(tpl.getContent(), vars)
                : defaultPlanPrompt(vars);

        String system = "你是企业培训方案设计专家。你只能从给定的候选课程中选择，输出必须是合法 JSON，"
                + "不要输出任何解释性文字、Markdown 代码块或注释。";

        LlmResponse resp = llmClient.chat(system, prompt, true);
        if (!resp.isSuccess()) {
            PlanGenResultVO r = fallback(gap, ruleCourses, "大模型调用失败：" + resp.getError());
            r.setCostMs(resp.getCostMs());
            r.setAiRecordId(writeRecord(gap, ruleCourses, hits, resp, r, resp.getAttempts()));
            return r;
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(cleanJson(resp.getContent()));
        } catch (Exception e) {
            PlanGenResultVO r = fallback(gap, ruleCourses, "模型输出不是合法 JSON");
            r.setCostMs(resp.getCostMs());
            r.setAiRecordId(writeRecord(gap, ruleCourses, hits, resp, r, resp.getAttempts()));
            return r;
        }

        PlanSchemaValidator.ValidationResult vr = validator.validate(toMap(root), ruleCourses);
        if (!vr.valid) {
            PlanGenResultVO r = fallback(gap, ruleCourses, "结构化校验未通过：" + vr.message);
            r.setSchemaValid(false);
            r.setCostMs(resp.getCostMs());
            r.setAiRecordId(writeRecord(gap, ruleCourses, hits, resp, r, resp.getAttempts()));
            return r;
        }

        PlanGenResultVO result = new PlanGenResultVO();
        result.setGenType("AI_HYBRID");
        result.setLlmUsed(true);
        result.setSchemaValid(true);
        result.setSummary(vr.summary != null && !vr.summary.isBlank()
                ? vr.summary : defaultSummary(gap, vr.courses));
        result.setCourses(vr.courses);
        result.setTotalHours(sumHours(vr.courses));
        result.setCostMs(resp.getCostMs());
        result.setAiRecordId(writeRecord(gap, ruleCourses, hits, resp, result, resp.getAttempts()));
        return result;
    }

    /** 规则方案（降级路径） */
    private PlanGenResultVO fallback(GapResultVO gap, List<PlannedCourse> ruleCourses, String reason) {
        PlanGenResultVO r = new PlanGenResultVO();
        r.setGenType("AI_RULE");
        r.setLlmUsed(false);
        r.setSchemaValid(false);
        r.setFallbackReason(reason);
        r.setSummary(defaultSummary(gap, ruleCourses));
        r.setCourses(ruleCourses);
        r.setTotalHours(sumHours(ruleCourses));
        return r;
    }

    // ---------------- 辅助 ----------------

    private List<RagHit> retrieve(GapResultVO gap) {
        StringBuilder q = new StringBuilder();
        q.append(gap.getPostName() == null ? "" : gap.getPostName()).append(" ");
        gap.getItems().stream().filter(i -> i.getGap() != null && i.getGap() > 0)
                .limit(5).forEach(i -> q.append(i.getSkillName()).append(" "));
        q.append("技能标准 培训建议 达标要求");
        return ragService.search(q.toString(), 5);
    }

    private String buildCandidateText(List<PlannedCourse> courses) {
        return courses.stream().map(c -> String.format(
                "courseId=%d｜课程：%s｜覆盖技能：%s｜课程等级：%d｜建议学时：%.1f｜对应差距：%d 级｜优先级得分：%.1f",
                c.getCourseId(), c.getCourseName(), c.getCoverSkill(), c.getCourseLevel(),
                c.getHours().doubleValue(), c.getGapBefore(),
                c.getPriorityScore() == null ? 0 : c.getPriorityScore().doubleValue()))
                .collect(Collectors.joining("\n"));
    }

    private String defaultSummary(GapResultVO gap, List<PlannedCourse> courses) {
        List<GapItemVO> tops = gap.getItems().stream()
                .filter(i -> i.getGap() != null && i.getGap() > 0).limit(2).toList();
        String focus = tops.stream().map(i -> i.getSkillName() + "（差 " + i.getGap() + " 级）")
                .collect(Collectors.joining("、"));
        return String.format("针对%s（%s）的 %d 项技能差距，按「加权差距 = 等级差 × 技能权重」排序后，"
                        + "优先补齐%s，共安排 %d 门课程、合计 %.1f 学时。",
                gap.getRealName(), Objects.requireNonNullElse(gap.getPostName(), "岗位"),
                gap.getGapCount(), focus, courses.size(), sumHours(courses).doubleValue());
    }

    private BigDecimal sumHours(List<PlannedCourse> courses) {
        return courses.stream().map(PlannedCourse::getHours)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(1, RoundingMode.HALF_UP);
    }

    private String cleanJson(String s) {
        if (s == null) {
            return "{}";
        }
        String t = s.trim();
        if (t.startsWith("```")) {
            int i = t.indexOf('\n');
            t = i > 0 ? t.substring(i + 1) : t;
            int j = t.lastIndexOf("```");
            if (j >= 0) {
                t = t.substring(0, j);
            }
        }
        int l = t.indexOf('{');
        int r = t.lastIndexOf('}');
        if (l >= 0 && r > l) {
            t = t.substring(l, r + 1);
        }
        return t.trim();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(JsonNode node) {
        return objectMapper.convertValue(node, Map.class);
    }

    /** 写入 AI 生成记录，返回记录 ID（论文实验数据来源） */
    private Long writeRecord(GapResultVO gap, List<PlannedCourse> ruleCourses, List<RagHit> hits,
                             LlmResponse resp, PlanGenResultVO result, Integer attempts) {
        AiGenRecord rec = new AiGenRecord();
        rec.setBizType("PLAN_GEN");
        rec.setUserId(gap.getUserId());
        AiPromptTemplate tpl = promptService.getByScene("PLAN_GEN");
        rec.setTemplateId(tpl == null ? null : tpl.getId());
        try {
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("userId", gap.getUserId());
            snapshot.put("realName", gap.getRealName());
            snapshot.put("postName", gap.getPostName());
            snapshot.put("matchRate", gap.getMatchRate());
            snapshot.put("items", gap.getItems());
            snapshot.put("ruleCourses", ruleCourses);
            rec.setInputSnapshot(objectMapper.writeValueAsString(snapshot));
            rec.setRetrievedChunks(objectMapper.writeValueAsString(hits));
            rec.setParsedOutput(objectMapper.writeValueAsString(result));
        } catch (Exception e) {
            log.warn("序列化 AI 记录失败：{}", e.getMessage());
        }
        if (resp != null) {
            rec.setOutputContent(resp.getContent());
            rec.setModel(resp.getModel());
            rec.setPromptTokens(resp.getPromptTokens());
            rec.setCompletionTokens(resp.getCompletionTokens());
            rec.setCostMs(resp.getCostMs());
            rec.setAttemptCount(attempts == null ? Math.max(1, resp.getAttempts()) : attempts);
        } else {
            rec.setCostMs(result.getCostMs());
            rec.setAttemptCount(1);
        }
        rec.setStatus(Boolean.TRUE.equals(result.getLlmUsed()) ? "SUCCESS"
                : (resp != null && resp.isSuccess() ? "SCHEMA_FAILED" : "FALLBACK"));
        if (resp != null && !resp.isSuccess()) {
            rec.setStatus("API_ERROR");
        }
        rec.setAdoptStatus("PENDING");
        genRecordMapper.insert(rec);
        return rec.getId();
    }

    private String defaultPlanPrompt(Map<String, Object> vars) {
        return "员工：" + vars.get("realName") + "，岗位：" + vars.get("postName")
                + "\n技能差距：\n" + vars.get("gapTable")
                + "\n候选课程：\n" + vars.get("courseCandidates")
                + "\n知识依据：\n" + vars.get("ragContext")
                + "\n请输出 JSON：{\"summary\":\"\",\"courses\":[{\"courseId\":1,\"seqNo\":1,\"hours\":8,"
                + "\"reason\":\"\"}]}";
    }
}
