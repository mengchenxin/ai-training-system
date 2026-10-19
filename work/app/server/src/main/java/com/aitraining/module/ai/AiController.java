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

    // ---------------- 运行状态 ----------------

    @Operation(summary = "AI 模块状态（是否接了大模型、索引规模）")
    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("llmAvailable", llmClient.available());
        m.put("model", llmClient.modelName());
        m.put("ragIndexSize", ragService.indexSize());
        m.put("mode", llmClient.available() ? "规则+大模型混合" : "纯规则降级模式（未配置 API Key）");
        return Result.ok(m);
    }

    // ---------------- 个性化方案生成 ----------------

    @Operation(summary = "生成个性化培训方案（规则+大模型混合）")
    @PostMapping("/plan/generate")
    public Result<PlanGenResultVO> generate(@RequestParam Long userId,
                                            @RequestParam(required = false) BigDecimal budget,
                                            @RequestParam(required = false) Boolean useLlm) {
        if (LoginHelper.isEmployee() && !LoginHelper.userId().equals(userId)) {
            throw new BizException(403, "只能为自己生成方案");
        }
        return Result.ok(planService.generate(userId, budget, useLlm));
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
                "FALLBACK", fallback,
                "SCHEMA_FAILED", planRecords.stream().filter(r -> "SCHEMA_FAILED".equals(r.getStatus())).count(),
                "API_ERROR", planRecords.stream().filter(r -> "API_ERROR".equals(r.getStatus())).count()));
        m.put("adoptDist", Map.of(
                "PENDING", planRecords.stream().filter(r -> "PENDING".equals(r.getAdoptStatus())).count(),
                "ADOPTED", planRecords.stream().filter(r -> "ADOPTED".equals(r.getAdoptStatus())).count(),
                "MODIFIED", planRecords.stream().filter(r -> "MODIFIED".equals(r.getAdoptStatus())).count(),
                "REJECTED", planRecords.stream().filter(r -> "REJECTED".equals(r.getAdoptStatus())).count()));
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
