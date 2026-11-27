package com.aitraining.module.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 盲评首选判定测试。
 *
 * <p>背景：早期实现用「严格大于」找最高分，三份方案同分时谁是首选完全取决于
 * 遍历顺序（等价于「方案 A 永远赢」），导致「获首选次数」这一列失去意义。
 * 现在并列一律返回全部最高分标签，由调用方标记为 TIE。
 */
class BlindPickTieTest {

    private static Map<String, Integer> scores(Object... kv) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], (Integer) kv[i + 1]);
        }
        return m;
    }

    @Test
    @DisplayName("唯一最高分只返回一个标签")
    void singleWinner() {
        assertThat(AiPlanService.topLabels(scores("A", 3, "B", 5, "C", 4)))
                .containsExactly("B");
    }

    @Test
    @DisplayName("三份同分时全部并列，不能只挑第一个")
    void tripleTie() {
        assertThat(AiPlanService.topLabels(scores("A", 4, "B", 4, "C", 4)))
                .containsExactly("A", "B", "C");
    }

    @Test
    @DisplayName("两份并列最高时都返回，排除较低的一份")
    void doubleTie() {
        assertThat(AiPlanService.topLabels(scores("A", 5, "B", 3, "C", 5)))
                .containsExactly("A", "C");
    }

    @Test
    @DisplayName("并列结果与标签顺序无关，只取决于分数")
    void orderIndependent() {
        assertThat(AiPlanService.topLabels(scores("C", 4, "A", 4, "B", 3)))
                .containsExactly("C", "A");
        assertThat(AiPlanService.topLabels(scores("B", 3, "C", 4, "A", 4)))
                .containsExactly("C", "A");
    }

    @Test
    @DisplayName("空打分不会抛异常，返回空列表")
    void emptyScores() {
        assertThat(AiPlanService.topLabels(new LinkedHashMap<>())).isEmpty();
    }
}
