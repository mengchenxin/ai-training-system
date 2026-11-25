package com.aitraining.module.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aitraining.common.BizException;
import com.aitraining.common.PageResult;
import com.aitraining.common.Result;
import com.aitraining.module.ai.dto.PlanGenResultVO;
import com.aitraining.module.ai.entity.AiGenRecord;
import com.aitraining.module.ai.entity.AiPromptTemplate;
import com.aitraining.module.ai.entity.AiQaLog;
import com.aitraining.module.ai.entity.EffectEval;
import com.aitraining.module.ai.llm.LlmClient;
import com.aitraining.module.ai.mapper.AiGenRecordMapper;
import com.aitraining.module.ai.mapper.AiPromptTemplateMapper;
import com.aitraining.module.ai.mapper.AiQaLogMapper;
import com.aitraining.module.ai.rag.RagService;
import com.aitraining.security.LoginHelper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "10-AI 智能模块")
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiPlanService planService;
    private final AiQaService qaService;
    private final EffectService effectService;
    private final RagService ragService;
    private final LlmClient llmClient;
    private final PromptService promptService;
    private final AiGenRecordMapper genRecordMapper;
    private final AiPromptTemplateMapper templateMapper;
    private final AiQaLogMapper qaLogMapper;
    private final PlanCacheService planCache;

    // ---------------- 运行状态 ----------------

    @Operation(summary = "AI 模块状态（是否接了大模型、索引规模）")
    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("llmAvailable", llmClient.available());
        m.put("model", llmClient.modelName());
        m.put("ragIndexSize", ragService.indexSize());
        m.put("mode", llmClient.available() ? "规则+大模型混合" : "纯规则降级模式（未配置 API Key）");
        m.put("modes", java.util.Arrays.stream(PlanMode.values()).map(pm -> Map.of(
                "value", pm.name(),
                "label", pm.label(),
                "needLlm", pm != PlanMode.RULE,
                "available", pm == PlanMode.RULE || llmClient.available())).toList());
        m.put("cacheTtlSeconds", 1800);
        return Result.ok(m);
    }

    // ---------------- 个性化方案生成 ----------------

    @Operation(summary = "生成个性化培训方案（三模式：RULE / HYBRID / LLM_ONLY）")
    @PostMapping("/plan/generate")
    public Result<PlanGenResultVO> generate(@RequestParam Long userId,
                                            @RequestParam(required = false) BigDecimal budget,
                                            @RequestParam(required = false) Boolean useLlm,
                                            @RequestParam(required = false) String mode,
                                            @RequestParam(required = false, defaultValue = "false") boolean refresh) {
        if (LoginHelper.isEmployee() && !LoginHelper.userId().equals(userId)) {
            throw new BizException(403, "只能为自己生成方案");
        }
        return Result.ok(planService.generate(userId, budget, useLlm, mode, refresh));
    }

    @Operation(summary = "三组方案匿名对比（盲评材料，隐去模式标签）")
    @PostMapping("/plan/compare")
    public Result<Map<String, Object>> compare(@RequestParam Long userId,
                                               @RequestParam(required = false) BigDecimal budget) {
        LoginHelper.requireManager();
        return Result.ok(planService.compare(userId, budget));
    }

    @Operation(summary = "提交盲评打分（1-5 分，最高分为首选）")
    @PostMapping("/plan/blind-pick")
    public Result<Map<String, Object>> blindPick(@RequestBody Map<String, Object> body) {
        LoginHelper.requireManager();
        String blindId = body.get("blindId") == null ? null : String.valueOf(body.get("blindId"));
        if (blindId == null || blindId.isBlank()) {
            throw new BizException("缺少 blindId");
        }
        Object raw = body.get("scores");
        Map<String, Integer> scores = new LinkedHashMap<>();
        if (raw instanceof Map<?, ?> m) {
            m.forEach((k, v) -> scores.put(String.valueOf(k),
                    v instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(v))));
        }
        return Result.ok(planService.blindPick(blindId, scores));
    }

    @Operation(summary = "盲评结果汇总（按模式解盲，论文表格数据源）")
    @GetMapping("/plan/blind-summary")
    public Result<List<Map<String, Object>>> blindSummary() {
        LoginHelper.requireManager();
        return Result.ok(planService.blindSummary());
    }

    @Operation(summary = "采纳或驳回方案（写入实验指标）")
    @PostMapping("/plan/{recordId}/adopt")
    public Result<Void> adopt(@PathVariable Long recordId,
                              @RequestParam String status,
                              @RequestParam(required = false) String reason) {
        if (!List.of("ADOPTED", "MODIFIED", "REJECTED", "PENDING").contains(status)) {
            throw new BizException("非法的采纳状态");
        }
        AiGenRecord r = new AiGenRecord();
        r.setId(recordId);
        r.setAdoptStatus(status);
        r.setRejectReason(reason);
        genRecordMapper.updateById(r);
        // 采纳/驳回意味着该员工的结果已被消费，缓存的旧方案不再有意义
        AiGenRecord db = genRecordMapper.selectById(recordId);
        if (db != null && db.getUserId() != null) {
            planCache.invalidateUser(db.getUserId());
        }
        return Result.ok();
    }

    @Operation(summary = "AI 生成记录分页")
    @GetMapping("/record/page")
    public Result<PageResult<AiGenRecord>> recordPage(@RequestParam(defaultValue = "1") long current,
                                                      @RequestParam(defaultValue = "10") long size,
                                                      @RequestParam(required = false) String bizType,
                                                      @RequestParam(required = false) String status) {
        LoginHelper.requireManager();
        LambdaQueryWrapper<AiGenRecord> w = new LambdaQueryWrapper<>();
        if (bizType != null && !bizType.isBlank()) {
            w.eq(AiGenRecord::getBizType, bizType);
        }
        if (status != null && !status.isBlank()) {
            w.eq(AiGenRecord::getStatus, status);
        }
        w.orderByDesc(AiGenRecord::getId);
        Page<AiGenRecord> p = genRecordMapper.selectPage(new Page<>(current, size), w);
        return Result.ok(PageResult.of(p));
    }

    @Operation(summary = "AI 效果指标（论文实验数据）")
    @GetMapping("/metrics")
    public Result<Map<String, Object>> metrics() {
        LoginHelper.requireManager();
        List<AiGenRecord> planRecords = genRecordMapper.selectList(
                new LambdaQueryWrapper<AiGenRecord>().eq(AiGenRecord::getBizType, "PLAN_GEN"));
        Map<String, Object> m = new LinkedHashMap<>();
        long total = planRecords.size();
        m.put("total", total);
        if (total == 0) {
            m.put("schemaPassRate", null);
            m.put("adoptRate", BigDecimal.ZERO);
            m.put("avgCostMs", 0);
            m.put("avgAttempts", 0);
            m.put("ragHitRate", BigDecimal.ZERO);
            m.put("llmShare", BigDecimal.ZERO);
            m.put("llmCalls", 0);
            m.put("fallbackRate", BigDecimal.ZERO);
            m.put("modeDist", Map.of());
            m.put("modeStats", List.of());
            m.put("cache", planCache.stats());
            return Result.ok(m);
        }
        // 结构合规率只统计真正发起过大模型调用的记录：
        // 纯规则降级（FALLBACK）没有 LLM 输出可校验，计入分母会把指标压成假的 0%
        long llmSuccess = planRecords.stream().filter(r -> "SUCCESS".equals(r.getStatus())).count();
        long llmFailed = planRecords.stream()
                .filter(r -> "SCHEMA_FAILED".equals(r.getStatus()) || "API_ERROR".equals(r.getStatus()))
                .count();
        long llmCalls = llmSuccess + llmFailed;
        long adopted = planRecords.stream()
                .filter(r -> "ADOPTED".equals(r.getAdoptStatus()) || "MODIFIED".equals(r.getAdoptStatus())).count();
        long ragHit = planRecords.stream().filter(r -> r.getRetrievedChunks() != null
                && r.getRetrievedChunks().length() > 5).count();
        long fallback = planRecords.stream().filter(r -> "FALLBACK".equals(r.getStatus())).count();
        long llmUsed = planRecords.stream().filter(r -> r.getPromptTokens() != null
                && r.getPromptTokens() > 0).count();
        double avgCost = planRecords.stream().filter(r -> r.getCostMs() != null)
                .mapToInt(AiGenRecord::getCostMs).average().orElse(0);
        double avgAttempt = planRecords.stream().filter(r -> r.getAttemptCount() != null)
                .mapToInt(AiGenRecord::getAttemptCount).average().orElse(0);

        m.put("llmCalls", llmCalls);
        m.put("schemaPassRate", llmCalls == 0 ? null : pct(llmSuccess, llmCalls));
        m.put("adoptRate", pct(adopted, total));
        m.put("avgCostMs", (int) avgCost);
        m.put("avgAttempts", BigDecimal.valueOf(avgAttempt).setScale(2, RoundingMode.HALF_UP));
        m.put("ragHitRate", pct(ragHit, total));
        m.put("llmShare", pct(llmUsed, total));
        m.put("fallbackRate", pct(fallback, total));
        m.put("statusDist", Map.of(
                "SUCCESS", llmSuccess,
                "RULE_ONLY", planRecords.stream().filter(r -> "RULE_ONLY".equals(r.getStatus())).count(),
                "FALLBACK", fallback,
                "SCHEMA_FAILED", planRecords.stream().filter(r -> "SCHEMA_FAILED".equals(r.getStatus())).count(),
                "API_ERROR", planRecords.stream().filter(r -> "API_ERROR".equals(r.getStatus())).count()));
        m.put("adoptDist", Map.of(
                "PENDING", planRecords.stream().filter(r -> "PENDING".equals(r.getAdoptStatus())).count(),
                "ADOPTED", planRecords.stream().filter(r -> "ADOPTED".equals(r.getAdoptStatus())).count(),
                "MODIFIED", planRecords.stream().filter(r -> "MODIFIED".equals(r.getAdoptStatus())).count(),
                "REJECTED", planRecords.stream().filter(r -> "REJECTED".equals(r.getAdoptStatus())).count()));

        // ---- 三组对比实验口径：按生成模式分组统计（论文表格直接取这一块）----
        m.put("modeDist", planRecords.stream().filter(r -> r.getGenMode() != null)
                .collect(java.util.stream.Collectors.groupingBy(AiGenRecord::getGenMode,
                        java.util.stream.Collectors.counting())));
        List<Map<String, Object>> modeStats = new java.util.ArrayList<>();
        for (PlanMode pm : PlanMode.values()) {
            List<AiGenRecord> list = planRecords.stream()
                    .filter(r -> pm.name().equals(r.getGenMode())).toList();
            if (list.isEmpty()) {
                continue;
            }
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("mode", pm.name());
            s.put("label", pm.label());
            s.put("samples", list.size());
            s.put("avgCostMs", Math.round(list.stream()
                    .mapToInt(r -> r.getCostMs() == null ? 0 : r.getCostMs()).average().orElse(0)));
            s.put("avgTotalTokens", Math.round(list.stream()
                    .mapToInt(r -> (r.getPromptTokens() == null ? 0 : r.getPromptTokens())
                            + (r.getCompletionTokens() == null ? 0 : r.getCompletionTokens()))
                    .average().orElse(0)));
            s.put("avgHallucination", BigDecimal.valueOf(list.stream()
                            .mapToInt(r -> r.getHallucinationCount() == null ? 0 : r.getHallucinationCount())
                            .average().orElse(0))
                    .setScale(2, RoundingMode.HALF_UP));
            // 纯规则模式的正常状态是 RULE_ONLY（不是 SUCCESS、也不是降级），
            // 两者都算「按预期产出」，否则规则组成功率会显示成 0%
            s.put("successRate", pct(list.stream()
                    .filter(r -> "SUCCESS".equals(r.getStatus()) || "RULE_ONLY".equals(r.getStatus()))
                    .count(), list.size()));
            modeStats.add(s);
        }
        m.put("modeStats", modeStats);
        m.put("cache", planCache.stats());
        return Result.ok(m);
    }

    private BigDecimal pct(long part, long total) {
        return BigDecimal.valueOf(part * 100.0 / total).setScale(2, RoundingMode.HALF_UP);
    }

    // ---------------- 知识问答 ----------------

    @Operation(summary = "知识库问答（RAG）")
    @PostMapping("/qa")
    public Result<Map<String, Object>> qa(@RequestBody Map<String, String> body) {
        String q = body.get("question");
        if (q == null || q.isBlank()) {
            throw new BizException("问题不能为空");
        }
        return Result.ok(qaService.ask(LoginHelper.userId(), q));
    }

    @Operation(summary = "问答反馈")
    @PostMapping("/qa/{logId}/feedback")
    public Result<Void> qaFeedback(@PathVariable Long logId, @RequestParam Integer feedback) {
        qaService.feedback(logId, feedback);
        return Result.ok();
    }

    @Operation(summary = "检索预览（演示 RAG 命中效果）")
    @GetMapping("/rag/search")
    public Result<List<Map<String, Object>>> ragSearch(@RequestParam String q) {
        return Result.ok(qaService.preview(q));
    }

    @Operation(summary = "重建向量/索引")
    @PostMapping("/rag/reload")
    public Result<Integer> ragReload() {
        LoginHelper.requireManager();
        return Result.ok(ragService.reload());
    }

    @Operation(summary = "问答日志分页")
    @GetMapping("/qa/log/page")
    public Result<PageResult<AiQaLog>> qaLogPage(@RequestParam(defaultValue = "1") long current,
                                                 @RequestParam(defaultValue = "10") long size) {
        LoginHelper.requireManager();
        Page<AiQaLog> p = qaLogMapper.selectPage(new Page<>(current, size),
                new LambdaQueryWrapper<AiQaLog>().orderByDesc(AiQaLog::getId));
        return Result.ok(PageResult.of(p));
    }

    // ---------------- 效果诊断与补训 ----------------

    @Operation(summary = "生成效果诊断（含大模型增强）")
    @PostMapping("/effect/{planId}/{userId}")
    public Result<EffectEval> effect(@PathVariable Long planId, @PathVariable Long userId) {
        LoginHelper.requireSelfOrManager(userId);
        return Result.ok(effectService.evaluateWithLlm(planId, userId));
    }

    @Operation(summary = "查看计划下所有效果诊断")
    @GetMapping("/effect/{planId}")
    public Result<List<Map<String, Object>>> effectList(@PathVariable Long planId) {
        LoginHelper.requireManager();
        return Result.ok(effectService.listByPlan(planId));
    }

    @Operation(summary = "重新生成补训方案")
    @PostMapping("/effect/{planId}/{userId}/retrain")
    public Result<String> retrain(@PathVariable Long planId, @PathVariable Long userId) {
        LoginHelper.requireManager();
        EffectEval e = effectService.evaluate(planId, userId);
        return Result.ok(e.getRetrainPlan());
    }

    // ---------------- Prompt 模板 ----------------

    @Operation(summary = "Prompt 模板列表")
    @GetMapping("/prompt/list")
    public Result<List<AiPromptTemplate>> promptList() {
        LoginHelper.requireManager();
        return Result.ok(templateMapper.selectList(new LambdaQueryWrapper<AiPromptTemplate>()
                .orderByAsc(AiPromptTemplate::getScene).orderByDesc(AiPromptTemplate::getVersion)));
    }

    @Operation(summary = "修改 Prompt 模板")
    @PostMapping("/prompt")
    public Result<Long> savePrompt(@RequestBody AiPromptTemplate tpl) {
        LoginHelper.requireManager();
        if (tpl.getId() == null) {
            templateMapper.insert(tpl);
        } else {
            templateMapper.updateById(tpl);
        }
        promptService.clearCache();
        return Result.ok(tpl.getId());
    }
}
