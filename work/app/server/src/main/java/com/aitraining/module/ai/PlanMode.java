package com.aitraining.module.ai;

/**
 * 培训方案生成策略 —— 三组对比实验的三种模式。
 *
 * <pre>
 *   RULE      纯规则：全部由 RulePlanEngine 确定性生成，不调用大模型（对照组一，基线）
 *   HYBRID    混合   ：规则召回候选集 → 模型在候选集内挑选/排序/写依据 → 结构化校验（本文方案）
 *   LLM_ONLY  纯大模型：不设候选集，把完整课程库与差距表一起交给模型自由生成（对照组二）
 * </pre>
 *
 * <p>三组实验用于回答两个问题：约束是否必要（HYBRID vs LLM_ONLY）、
 * 模型是否带来增量（HYBRID vs RULE）。LLM_ONLY 的产出允许包含课程库之外的课程，
 * 这类输出会被记录为「幻觉课程」而不是直接判失败，否则无法量化约束的价值。
 */
public enum PlanMode {

    RULE("AI_RULE", "纯规则"),
    HYBRID("AI_HYBRID", "规则+大模型混合"),
    LLM_ONLY("AI_LLM_ONLY", "纯大模型");

    /** 写入 ai_gen_record.gen_mode 与 PlanGenResultVO.genType 的取值 */
    private final String code;
    private final String label;

    PlanMode(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String code() {
        return code;
    }

    public String label() {
        return label;
    }

    /**
     * 解析外部传入的模式。缺省时按旧参数 useLlm 推导，
     * 保证老前端（只传 useLlm）行为不变。
     */
    public static PlanMode parse(String mode, Boolean useLlm) {
        if (mode != null && !mode.isBlank()) {
            String m = mode.trim().toUpperCase();
            for (PlanMode v : values()) {
                if (v.name().equals(m) || v.code.equalsIgnoreCase(m)) {
                    return v;
                }
            }
            // 兼容旧值
            if ("LLM".equals(m) || "FREE".equals(m)) {
                return LLM_ONLY;
            }
        }
        return Boolean.FALSE.equals(useLlm) ? RULE : HYBRID;
    }
}
