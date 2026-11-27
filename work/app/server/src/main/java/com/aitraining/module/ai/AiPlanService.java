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
import com.aitraining.module.skill.entity.Skill;
import com.aitraining.module.skill.mapper.SkillMapper;
import com.aitraining.module.train.entity.Course;
import com.aitraining.module.train.mapper.CourseMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
 * AI 个性化培训方案生成 —— 三模式主流程。
 *
 * <pre>
 *   RULE      技能差距 ──▶ RulePlanEngine 确定性选课           （对照组一：基线）
 *
 *   HYBRID    技能差距 ──▶ 规则召回候选集 ──▶ 模型在集合内排序/写依据 ──▶ 严格校验
 *                              │                                   └─ 失败 → 降级为 RULE
 *                              └── BM25 检索知识库补充依据
 *
 *   LLM_ONLY  技能差距 + 完整课程库 ──▶ 模型自由生成课程清单 ──▶ 宽松校验
 *                                                              └─ 记「幻觉课程数」
 * </pre>
 *
 * <p>三种模式回答两个实验问题：约束是否必要（HYBRID vs LLM_ONLY，看幻觉课程数与盲评分），
 * 模型是否带来增量（HYBRID vs RULE，看课程差异与盲评分）。
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
    private final CourseMapper courseMapper;
    private final SkillMapper skillMapper;
    private final PlanCacheService cache;

    /** 兼容旧调用：只传 useLlm 时按 HYBRID / RULE 处理 */
    public PlanGenResultVO generate(Long userId, BigDecimal budget, Boolean allowLlm) {
        return generate(userId, budget, allowLlm, null, false);
    }

    /**
     * @param mode    生成模式，为空时按 allowLlm 推导
     * @param refresh 为 true 时跳过缓存（实验采样用，保证每次都是真实调用）
     */
    public PlanGenResultVO generate(Long userId, BigDecimal budget, Boolean allowLlm,
                                    String mode, boolean refresh) {
        long start = System.currentTimeMillis();
        PlanMode pm = PlanMode.parse(mode, allowLlm);
        GapResultVO gap = gapService.analyze(userId);
        String gapHash = gapHash(gap);
        String cacheKey = cache.buildKey(userId, budget, pm, gapHash);

        // 纯规则模式仅 30 余毫秒，缓存收益为零，直接放行以保证实验耗时真实
        if (pm != PlanMode.RULE && !refresh) {
            PlanGenResultVO cached = cache.get(cacheKey);
            if (cached != null) {
                PlanGenResultVO copy = copyOf(cached);
                copy.setCached(true);
                copy.setCostMs((int) (System.currentTimeMillis() - start));
                return copy;
            }
        }

        PlanGenResultVO result = switch (pm) {
            case RULE -> ruleOnly(gap, budget);
            case HYBRID -> hybrid(gap, budget);
            case LLM_ONLY -> llmOnly(gap, budget);
        };

        result.setUserId(userId);
        result.setRealName(gap.getRealName());
        result.setPostName(gap.getPostName());
        result.setMode(pm.name());
        result.setCached(false);
        result.setBudget(budget == null ? RulePlanEngine.DEFAULT_BUDGET : budget);
        result.setCostMs((int) (System.currentTimeMillis() - start));

        if (pm != PlanMode.RULE && Boolean.TRUE.equals(result.getLlmUsed())
                && result.getFallbackReason() == null) {
            result.setCached(false);
            cache.put(cacheKey, copyOf(result));
        }
        return result;
    }

    // ---------------- 模式一：纯规则 ----------------

    private PlanGenResultVO ruleOnly(GapResultVO gap, BigDecimal budget) {
        long t0 = System.currentTimeMillis();
        List<PlannedCourse> courses = ruleEngine.plan(gap, budget);
        if (courses.isEmpty()) {
            throw new BizException("该员工技能已达标，或课程库中没有可覆盖其差距的课程，无需生成方案");
        }
        PlanGenResultVO r = new PlanGenResultVO();
        r.setGenType(PlanMode.RULE.code());
        r.setMode(PlanMode.RULE.name());
        r.setLlmUsed(false);
        r.setSchemaValid(true);
        r.setSummary(defaultSummary(gap, courses));
        r.setCourses(courses);
        r.setTotalHours(sumHours(courses));
        r.setCostMs((int) (System.currentTimeMillis() - t0));
        r.setRefs(List.of());
        r.setAiRecordId(writeRecord(gap, courses, List.of(), null, r, 1, "RULE_ONLY", PlanMode.RULE, 0));
        return r;
    }

    // ---------------- 模式二：规则 + 大模型（混合） ----------------

    private PlanGenResultVO hybrid(GapResultVO gap, BigDecimal budget) {
        List<PlannedCourse> ruleCourses = ruleEngine.plan(gap, budget);
        if (ruleCourses.isEmpty()) {
            throw new BizException("该员工技能已达标，或课程库中没有可覆盖其差距的课程，无需生成方案");
        }
        List<RagHit> hits = retrieve(gap);
        String ragContext = ragService.buildContext(hits);

        if (!llmClient.available()) {
            PlanGenResultVO r = fallback(gap, ruleCourses, "未配置大模型 API Key，已降级为规则方案", hits);
            r.setAiRecordId(writeRecord(gap, ruleCourses, hits, null, r, 1, "FALLBACK",
                    PlanMode.HYBRID, 0));
            return r;
        }

        AiPromptTemplate tpl = promptService.getByScene("PLAN_GEN");
        Map<String, Object> vars = baseVars(gap, budget);
        vars.put("courseCandidates", buildCandidateText(ruleCourses));
        vars.put("ragContext", ragContext);
        String prompt = tpl != null ? PromptService.render(tpl.getContent(), vars) : defaultPlanPrompt(vars);

        LlmResponse resp = llmClient.chat(
                promptService.systemPrompt("PLAN_GEN", hybridSystemPrompt()), prompt, true);
        if (!resp.isSuccess()) {
            PlanGenResultVO r = fallback(gap, ruleCourses, "大模型调用失败：" + resp.getError(), hits);
            fillTokens(r, resp);
            r.setAiRecordId(writeRecord(gap, ruleCourses, hits, resp, r, resp.getAttempts(), null,
                    PlanMode.HYBRID, 0));
            return r;
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(cleanJson(resp.getContent()));
        } catch (Exception e) {
            PlanGenResultVO r = fallback(gap, ruleCourses, "模型输出不是合法 JSON", hits);
            fillTokens(r, resp);
            r.setAiRecordId(writeRecord(gap, ruleCourses, hits, resp, r, resp.getAttempts(), null,
                    PlanMode.HYBRID, 0));
            return r;
        }

        PlanSchemaValidator.ValidationResult vr = validator.validate(toMap(root), ruleCourses);
        if (!vr.valid) {
            PlanGenResultVO r = fallback(gap, ruleCourses, "结构化校验未通过：" + vr.message, hits);
            r.setSchemaValid(false);
            fillTokens(r, resp);
            r.setAiRecordId(writeRecord(gap, ruleCourses, hits, resp, r, resp.getAttempts(), null,
                    PlanMode.HYBRID, 0));
            return r;
        }

        PlanGenResultVO result = new PlanGenResultVO();
        result.setGenType(PlanMode.HYBRID.code());
        result.setMode(PlanMode.HYBRID.name());
        result.setLlmUsed(true);
        result.setSchemaValid(true);
        result.setSummary(vr.summary != null && !vr.summary.isBlank()
                ? vr.summary : defaultSummary(gap, vr.courses));
        result.setCourses(vr.courses);
        result.setTotalHours(sumHours(vr.courses));
        result.setCostMs(resp.getCostMs());
        fillTokens(result, resp);
        result.setRefs(hits.stream().map(PlanGenResultVO.RefSource::of).toList());
        result.setAiRecordId(writeRecord(gap, ruleCourses, hits, resp, result, resp.getAttempts(),
                null, PlanMode.HYBRID, 0));
        return result;
    }

    // ---------------- 模式三：纯大模型（自由生成） ----------------

    /**
     * 对照组实现要点：
     * <ul>
     *   <li><b>不给候选集</b>——只给完整课程库与差距表，选哪几门完全由模型决定；</li>
     *   <li>允许它推荐课程库之外的课程（真实自由生成的必然结果），
     *       这类课程经宽松校验后计入幻觉课程数并剔除；</li>
     *   <li>不做降级：这一组的价值就是展示「不加约束会怎样」，
     *       一旦全部幻觉则如实记为失败，而不是偷偷退回规则方案。</li>
     * </ul>
     */
    private PlanGenResultVO llmOnly(GapResultVO gap, BigDecimal budget) {
        if (!llmClient.available()) {
            PlanGenResultVO r = new PlanGenResultVO();
            r.setGenType(PlanMode.LLM_ONLY.code());
            r.setMode(PlanMode.LLM_ONLY.name());
            r.setLlmUsed(false);
            r.setSchemaValid(false);
            r.setFallbackReason("未配置大模型 API Key，纯大模型模式不可用");
            r.setSummary("纯大模型模式需要可用的模型接口，当前环境未配置 API Key。");
            r.setCourses(List.of());
            r.setTotalHours(BigDecimal.ZERO);
            r.setHallucinationCount(0);
            r.setAiRecordId(writeRecord(gap, List.of(), List.of(), null, r, 1, "API_ERROR",
                    PlanMode.LLM_ONLY, 0));
            return r;
        }

        List<Course> catalog = courseMapper.selectList(
                new LambdaQueryWrapper<Course>().eq(Course::getStatus, 1).orderByAsc(Course::getId));
        if (catalog.isEmpty()) {
            throw new BizException("课程库为空，无法生成方案");
        }
        Map<Long, String> skillNames = skillNameMap(catalog);
        List<RagHit> hits = retrieve(gap);
        String ragContext = ragService.buildContext(hits);

        AiPromptTemplate tpl = promptService.getByScene("PLAN_GEN");
        Map<String, Object> vars = baseVars(gap, budget);
        // 注意：这里给的是「课程库全量」，不是规则引擎筛过的候选集
        vars.put("courseCandidates", buildCatalogText(catalog, skillNames));
        vars.put("ragContext", ragContext);
        String prompt = (tpl != null ? PromptService.render(tpl.getContent(), vars) : defaultPlanPrompt(vars))
                + "\n\n【本组实验的额外要求】\n"
                + "不要局限于上面列出的课程。请结合岗位要求与技能差距自主判断需要学什么、先学什么，"
                + "课程名称可以使用课程库中的课程，也可以按你的专业判断给出更合适的课程名称；"
                + "学时按经验估计即可。务必输出合法 JSON，courses 中每项包含 courseName、hours、reason，"
                + "若能对应到课程库中的课程，请同时给出 courseId。";

        // 纯大模型对照组使用独立场景 PLAN_GEN_FREE：它的系统提示词与混合模式不同
        // （不受候选集约束），若沿用 PLAN_GEN 会把两组的提示词混在一起，
        // 在模板表里改 PLAN_GEN 时本组行为也会跟着变，三组对比就失去独立性。
        LlmResponse resp = llmClient.chat(
                promptService.systemPrompt("PLAN_GEN_FREE", llmOnlySystemPrompt()), prompt, true);
        if (!resp.isSuccess()) {
            PlanGenResultVO r = new PlanGenResultVO();
            r.setGenType(PlanMode.LLM_ONLY.code());
            r.setMode(PlanMode.LLM_ONLY.name());
            r.setLlmUsed(false);
            r.setSchemaValid(false);
            r.setFallbackReason("大模型调用失败：" + resp.getError());
            r.setSummary("模型调用失败，本次未产出方案。");
            r.setCourses(List.of());
            r.setTotalHours(BigDecimal.ZERO);
            fillTokens(r, resp);
            r.setHallucinationCount(0);
            r.setAiRecordId(writeRecord(gap, List.of(), hits, resp, r, resp.getAttempts(), "API_ERROR",
                    PlanMode.LLM_ONLY, 0));
            return r;
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(cleanJson(resp.getContent()));
        } catch (Exception e) {
            PlanGenResultVO r = new PlanGenResultVO();
            r.setGenType(PlanMode.LLM_ONLY.code());
            r.setMode(PlanMode.LLM_ONLY.name());
            r.setLlmUsed(true);
            r.setSchemaValid(false);
            r.setFallbackReason("模型输出不是合法 JSON");
            r.setSummary("模型未按要求返回 JSON，本次无法解析。");
            r.setCourses(List.of());
            r.setTotalHours(BigDecimal.ZERO);
            fillTokens(r, resp);
            r.setHallucinationCount(0);
            r.setAiRecordId(writeRecord(gap, List.of(), hits, resp, r, resp.getAttempts(),
                    "SCHEMA_FAILED", PlanMode.LLM_ONLY, 0));
            return r;
        }

        PlanSchemaValidator.ValidationResult vr = validator.validateLenient(toMap(root), catalog, skillNames);
        PlanGenResultVO result = new PlanGenResultVO();
        result.setGenType(PlanMode.LLM_ONLY.code());
        result.setMode(PlanMode.LLM_ONLY.name());
        result.setLlmUsed(true);
        result.setSchemaValid(vr.valid);
        result.setHallucinationCount(vr.hallucinationCount());
        result.setHallucinatedCourses(vr.hallucinatedCourses);
        result.setSummary(vr.summary != null && !vr.summary.isBlank()
                ? vr.summary : (vr.valid ? defaultSummary(gap, vr.courses) : "模型输出无法解析为可执行方案。"));
        result.setCourses(vr.courses);
        result.setTotalHours(sumHours(vr.courses));
        result.setCostMs(resp.getCostMs());
        fillTokens(result, resp);
        result.setRefs(hits.stream().map(PlanGenResultVO.RefSource::of).toList());
        if (!vr.valid) {
            result.setFallbackReason(vr.message);
        }
        result.setAiRecordId(writeRecord(gap, List.of(), hits, resp, result, resp.getAttempts(),
                vr.valid ? "SUCCESS" : "SCHEMA_FAILED", PlanMode.LLM_ONLY, vr.hallucinationCount()));
        return result;
    }

    // ---------------- 盲评：同一员工三方案并排 ----------------

    /**
     * 生成三组方案供人工盲评（隐去模式标签、打乱顺序）。
     * 返回的 variants 中不含任何模式信息，评分完成后由 {@link #blindPick} 回填。
     */
    public Map<String, Object> compare(Long userId, BigDecimal budget) {
        List<PlanGenResultVO> variants = new ArrayList<>();
        for (PlanMode m : List.of(PlanMode.RULE, PlanMode.HYBRID, PlanMode.LLM_ONLY)) {
            try {
                // refresh=true：三组必须各自真实执行，不能拿缓存糊弄
                variants.add(generate(userId, budget, m != PlanMode.RULE, m.name(), true));
            } catch (BizException e) {
                log.warn("盲评生成 {} 失败：{}", m, e.getMessage());
            }
        }
        if (variants.size() < 2) {
            throw new BizException("可用方案不足两组，无法进行盲评（请检查课程库与模型配置）");
        }
        Collections.shuffle(variants, new Random());
        String blindId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Map<String, Long> mapping = new LinkedHashMap<>();
        List<Map<String, Object>> out = new ArrayList<>();
        int i = 0;
        for (PlanGenResultVO v : variants) {
            String label = String.valueOf((char) ('A' + i));
            mapping.put(label, v.getAiRecordId());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("label", label);
            item.put("summary", v.getSummary());
            item.put("courses", v.getCourses());
            item.put("totalHours", v.getTotalHours());
            item.put("costMs", v.getCostMs());
            item.put("totalTokens", v.getTotalTokens());
            out.add(item);
            i++;
        }
        blindSessions.put(blindId, new BlindSession(mapping, System.currentTimeMillis() + 2 * 3600_000L));
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("blindId", blindId);
        resp.put("userId", userId);
        resp.put("variants", out);
        return resp;
    }

    /**
     * 提交盲评结果：为每个标签打 1-5 分，最高分自动记为「首选」。
     * 分数写入 ai_gen_record.blind_score，作为「方案合理性」这一评估维度的数据来源。
     */
    public Map<String, Object> blindPick(String blindId, Map<String, Integer> scores) {
        BlindSession s = blindSessions.get(blindId);
        if (s == null || s.expireAt < System.currentTimeMillis()) {
            blindSessions.remove(blindId);
            throw new BizException("盲评会话已过期，请重新生成对比方案");
        }
        if (scores == null || scores.isEmpty()) {
            throw new BizException("请至少为一个方案打分");
        }
        // 只接受 1-5 的有效打分；0 或空视为「未评」，既不写库也不参与首选判定，
        // 避免把「没点星」误判成 1 分拉低平均分。
        Map<String, Integer> effective = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : scores.entrySet()) {
            if (!s.mapping.containsKey(e.getKey()) || e.getValue() == null || e.getValue() <= 0) {
                continue;
            }
            effective.put(e.getKey(), Math.max(1, Math.min(5, e.getValue())));
        }
        if (effective.isEmpty()) {
            throw new BizException("请至少为一个方案打分");
        }
        for (Map.Entry<String, Integer> e : effective.entrySet()) {
            AiGenRecord upd = new AiGenRecord();
            upd.setId(s.mapping.get(e.getKey()));
            upd.setBlindScore(e.getValue());
            genRecordMapper.updateById(upd);
        }
        List<String> tops = topLabels(effective);
        int bestScore = effective.get(tops.get(0));
        // 并列最高时不再按遍历顺序随便挑一个，统一标记 TIE，避免「获首选次数」被随机抬高
        boolean tie = tops.size() > 1;
        for (Map.Entry<String, Long> e : s.mapping.entrySet()) {
            if (!effective.containsKey(e.getKey())) {
                continue; // 未打分的方案保持原状态
            }
            AiGenRecord upd = new AiGenRecord();
            upd.setId(e.getValue());
            if (tops.contains(e.getKey())) {
                upd.setAdoptStatus(tie ? "TIE" : "ADOPTED");
                upd.setRejectReason(tie ? "盲评并列最高（" + blindId + "）" : null);
            } else {
                upd.setAdoptStatus("REJECTED");
                upd.setRejectReason("盲评未获首选（" + blindId + "）");
            }
            genRecordMapper.updateById(upd);
        }
        blindSessions.remove(blindId);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("blindId", blindId);
        r.put("bestLabel", tops.get(0));
        r.put("bestLabels", tops);
        r.put("tie", tie);
        r.put("bestScore", bestScore);
        r.put("scored", effective.size());
        return r;
    }

    /** 取最高分对应的标签，可能并列（按传入 Map 的标签顺序返回） */
    static List<String> topLabels(Map<String, Integer> scores) {
        int max = Integer.MIN_VALUE;
        for (Integer v : scores.values()) {
            max = Math.max(max, v == null ? 0 : v);
        }
        List<String> tops = new ArrayList<>();
        for (Map.Entry<String, Integer> e : scores.entrySet()) {
            int v = e.getValue() == null ? 0 : e.getValue();
            if (v == max) {
                tops.add(e.getKey());
            }
        }
        return tops;
    }

    private record BlindSession(Map<String, Long> mapping, long expireAt) {
    }

    private final Map<String, BlindSession> blindSessions = new java.util.concurrent.ConcurrentHashMap<>();

    /** 盲评结果汇总：按模式给出平均分、样本数与首选次数（模式在此处才解盲） */
    public List<Map<String, Object>> blindSummary() {
        List<AiGenRecord> records = genRecordMapper.selectList(new LambdaQueryWrapper<AiGenRecord>()
                .eq(AiGenRecord::getBizType, "PLAN_GEN")
                .isNotNull(AiGenRecord::getBlindScore));
        Map<String, List<AiGenRecord>> byMode = records.stream()
                .collect(Collectors.groupingBy(r -> r.getGenMode() == null ? "UNKNOWN" : r.getGenMode()));
        List<Map<String, Object>> out = new ArrayList<>();
        for (PlanMode m : PlanMode.values()) {
            List<AiGenRecord> list = byMode.getOrDefault(m.name(), List.of());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("mode", m.name());
            item.put("label", m.label());
            item.put("samples", list.size());
            item.put("avgScore", list.isEmpty() ? null : round2(list.stream()
                    .mapToInt(AiGenRecord::getBlindScore).average().orElse(0)));
            item.put("firstChoice", list.stream()
                    .filter(r -> "ADOPTED".equals(r.getAdoptStatus())).count());
            item.put("tieCount", list.stream()
                    .filter(r -> "TIE".equals(r.getAdoptStatus())).count());
            item.put("avgHallucination", list.isEmpty() ? null : round2(list.stream()
                    .mapToInt(r -> r.getHallucinationCount() == null ? 0 : r.getHallucinationCount())
                    .average().orElse(0)));
            item.put("avgCostMs", list.isEmpty() ? null : Math.round(list.stream()
                    .mapToInt(r -> r.getCostMs() == null ? 0 : r.getCostMs()).average().orElse(0)));
            out.add(item);
        }
        return out;
    }

    // ---------------- 辅助 ----------------

    private Map<String, Object> baseVars(GapResultVO gap, BigDecimal budget) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("realName", gap.getRealName());
        vars.put("postName", gap.getPostName());
        vars.put("deptName", Objects.requireNonNullElse(gap.getDeptName(), "-"));
        vars.put("gapTable", gap.getGapSummary());
        vars.put("totalHours", budget == null ? RulePlanEngine.DEFAULT_BUDGET : budget);
        return vars;
    }

    /** 规则方案（降级路径） */
    private PlanGenResultVO fallback(GapResultVO gap, List<PlannedCourse> ruleCourses,
                                     String reason, List<RagHit> hits) {
        PlanGenResultVO r = new PlanGenResultVO();
        r.setGenType("AI_RULE");
        r.setLlmUsed(false);
        r.setSchemaValid(false);
        r.setFallbackReason(reason);
        r.setSummary(defaultSummary(gap, ruleCourses));
        r.setCourses(ruleCourses);
        r.setTotalHours(sumHours(ruleCourses));
        if (hits != null) {
            r.setRefs(hits.stream().map(PlanGenResultVO.RefSource::of).toList());
        }
        return r;
    }

    private List<RagHit> retrieve(GapResultVO gap) {
        StringBuilder q = new StringBuilder();
        q.append(gap.getPostName() == null ? "" : gap.getPostName()).append(" ");
        gap.getItems().stream().filter(i -> i.getGap() != null && i.getGap() > 0)
                .limit(5).forEach(i -> q.append(i.getSkillName()).append(" "));
        q.append("技能标准 培训建议 达标要求");
        return ragService.search(q.toString(), 5);
    }

    private Map<Long, String> skillNameMap(List<Course> catalog) {
        Set<Long> ids = catalog.stream().map(Course::getSkillId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> m = new HashMap<>();
        for (Skill s : skillMapper.selectBatchIds(ids)) {
            m.put(s.getId(), s.getSkillName());
        }
        return m;
    }

    private String buildCandidateText(List<PlannedCourse> courses) {
        return courses.stream().map(c -> String.format(
                "courseId=%d｜课程：%s｜覆盖技能：%s｜课程等级：%d｜建议学时：%.1f｜对应差距：%d 级｜优先级得分：%.1f",
                c.getCourseId(), c.getCourseName(), c.getCoverSkill(), c.getCourseLevel(),
                c.getHours().doubleValue(), c.getGapBefore(),
                c.getPriorityScore() == null ? 0 : c.getPriorityScore().doubleValue()))
                .collect(Collectors.joining("\n"));
    }

    /** 课程库全量清单（纯大模型组用） */
    private String buildCatalogText(List<Course> catalog, Map<Long, String> skillNames) {
        return catalog.stream().map(c -> String.format(
                "courseId=%d｜课程：%s｜覆盖技能：%s｜等级：%d｜标准学时：%.1f｜类别：%s",
                c.getId(), c.getCourseName(),
                skillNames.getOrDefault(c.getSkillId(), "-"), c.getLevel(),
                c.getDurationHours() == null ? 4.0 : c.getDurationHours().doubleValue(),
                c.getCategory() == null ? "-" : c.getCategory()))
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
        return courses.stream().map(PlannedCourse::getHours).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(1, RoundingMode.HALF_UP);
    }

    private void fillTokens(PlanGenResultVO r, LlmResponse resp) {
        if (resp == null) {
            return;
        }
        r.setPromptTokens(resp.getPromptTokens());
        r.setCompletionTokens(resp.getCompletionTokens());
        int p = resp.getPromptTokens() == null ? 0 : resp.getPromptTokens();
        int c = resp.getCompletionTokens() == null ? 0 : resp.getCompletionTokens();
        r.setTotalTokens(p + c);
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

    private String gapHash(GapResultVO gap) {
        StringBuilder sb = new StringBuilder();
        gap.getItems().stream()
                .filter(i -> i.getGap() != null && i.getGap() > 0)
                .sorted(Comparator.comparing(GapItemVO::getSkillId))
                .forEach(i -> sb.append(i.getSkillId()).append(':').append(i.getGap()).append(';'));
        sb.append("mr=").append(gap.getMatchRate());
        return Integer.toHexString(sb.toString().hashCode());
    }

    private PlanGenResultVO copyOf(PlanGenResultVO src) {
        try {
            return objectMapper.readValue(objectMapper.writeValueAsString(src), PlanGenResultVO.class);
        } catch (Exception e) {
            // 序列化失败不该打断主流程，退化为返回原对象（只读使用）
            log.warn("方案缓存序列化失败，直接复用对象：{}", e.getMessage());
            return src;
        }
    }

    /**
     * 写入 AI 生成记录，返回记录 ID（论文实验数据来源）。
     *
     * @param statusOverride 显式指定状态；为 null 时按实际执行情况推导。
     *                       用于区分「主动选择纯规则（RULE_ONLY）」与「调用失败降级（FALLBACK）」，
     *                       否则统计降级率会把前者一并算进去。
     */
    private Long writeRecord(GapResultVO gap, List<PlannedCourse> ruleCourses, List<RagHit> hits,
                             LlmResponse resp, PlanGenResultVO result, Integer attempts,
                             String statusOverride, PlanMode mode, int hallucinationCount) {
        AiGenRecord rec = new AiGenRecord();
        rec.setBizType("PLAN_GEN");
        rec.setUserId(gap.getUserId());
        AiPromptTemplate tpl = promptService.getByScene(
                mode == PlanMode.LLM_ONLY ? "PLAN_GEN_FREE" : "PLAN_GEN");
        rec.setTemplateId(tpl == null ? null : tpl.getId());
        rec.setGenMode(mode == null ? null : mode.name());
        rec.setHallucinationCount(hallucinationCount);
        try {
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("userId", gap.getUserId());
            snapshot.put("realName", gap.getRealName());
            snapshot.put("postName", gap.getPostName());
            snapshot.put("matchRate", gap.getMatchRate());
            snapshot.put("mode", mode == null ? null : mode.name());
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
        rec.setStatus(statusOverride != null ? statusOverride
                : (Boolean.TRUE.equals(result.getLlmUsed()) ? "SUCCESS"
                : (resp != null && resp.isSuccess() ? "SCHEMA_FAILED" : "FALLBACK")));
        if (resp != null && !resp.isSuccess()) {
            rec.setStatus("API_ERROR");
        }
        rec.setAdoptStatus("PENDING");
        genRecordMapper.insert(rec);
        return rec.getId();
    }

    private String hybridSystemPrompt() {
        return "你是企业培训方案设计专家。你只能从给定的候选课程中选择，输出必须是合法 JSON，"
                + "不要输出任何解释性文字、Markdown 代码块或注释。";
    }

    private String llmOnlySystemPrompt() {
        return "你是企业培训方案设计专家。请根据员工的岗位要求与技能差距，自主设计一份培训方案，"
                + "包括课程组合、学习顺序与学时安排，并为每门课程说明安排依据。"
                + "输出必须是合法 JSON，不要输出任何解释性文字、Markdown 代码块或注释。";
    }

    private String defaultPlanPrompt(Map<String, Object> vars) {
        return "员工：" + vars.get("realName") + "，岗位：" + vars.get("postName")
                + "\n技能差距：\n" + vars.get("gapTable")
                + "\n候选课程：\n" + vars.get("courseCandidates")
                + "\n知识依据：\n" + vars.get("ragContext")
                + "\n请输出 JSON：{\"summary\":\"\",\"courses\":[{\"courseId\":1,\"seqNo\":1,\"hours\":8,"
                + "\"reason\":\"\"}]}";
    }

    private static BigDecimal round2(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }
}
