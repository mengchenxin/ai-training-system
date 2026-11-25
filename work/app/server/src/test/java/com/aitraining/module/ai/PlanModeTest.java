package com.aitraining.module.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 生成模式解析测试。
 *
 * <p>兼容性是这里的重点：老前端只传 useLlm（true/false），新前端传 mode，
 * 两种入参必须落到同一套模式上，否则「三组对比」里会混进语义不明的样本。
 */
class PlanModeTest {

    @Test
    @DisplayName("显式 mode 优先，忽略 useLlm")
    void explicitModeWins() {
        assertThat(PlanMode.parse("RULE", true)).isEqualTo(PlanMode.RULE);
        assertThat(PlanMode.parse("LLM_ONLY", false)).isEqualTo(PlanMode.LLM_ONLY);
        assertThat(PlanMode.parse("HYBRID", false)).isEqualTo(PlanMode.HYBRID);
    }

    @Test
    @DisplayName("大小写与旧别名都能识别")
    void aliases() {
        assertThat(PlanMode.parse("llm_only", null)).isEqualTo(PlanMode.LLM_ONLY);
        assertThat(PlanMode.parse("LLM", null)).isEqualTo(PlanMode.LLM_ONLY);
        assertThat(PlanMode.parse("FREE", null)).isEqualTo(PlanMode.LLM_ONLY);
        assertThat(PlanMode.parse("AI_HYBRID", null)).isEqualTo(PlanMode.HYBRID);
    }

    @Test
    @DisplayName("不传 mode 时按 useLlm 推导，保持老前端行为")
    void backwardCompatibleWithUseLlm() {
        assertThat(PlanMode.parse(null, Boolean.FALSE)).isEqualTo(PlanMode.RULE);
        assertThat(PlanMode.parse(null, Boolean.TRUE)).isEqualTo(PlanMode.HYBRID);
        assertThat(PlanMode.parse(null, null)).isEqualTo(PlanMode.HYBRID);
        assertThat(PlanMode.parse("  ", Boolean.FALSE)).isEqualTo(PlanMode.RULE);
    }

    @Test
    @DisplayName("无法识别的模式回退到 useLlm 推导，不抛异常")
    void unknownFallsBack() {
        assertThat(PlanMode.parse("随便写的", Boolean.FALSE)).isEqualTo(PlanMode.RULE);
        assertThat(PlanMode.parse("whatever", null)).isEqualTo(PlanMode.HYBRID);
    }

    @Test
    @DisplayName("每个模式的落库编码互不相同")
    void codesAreUnique() {
        assertThat(PlanMode.values()).extracting(PlanMode::code)
                .containsExactly("AI_RULE", "AI_HYBRID", "AI_LLM_ONLY");
    }
}
