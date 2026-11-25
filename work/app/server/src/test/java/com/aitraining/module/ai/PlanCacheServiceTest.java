package com.aitraining.module.ai;

import com.aitraining.module.ai.dto.PlanGenResultVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 方案缓存测试。
 *
 * <p>缓存本身不值钱，值钱的是「不会串味」：不同模式、不同预算、差距变了都必须不命中，
 * 否则答辩演示时会拿上次的结果冒充这次生成的，属于事故级问题。
 */
class PlanCacheServiceTest {

    private PlanCacheService cache;

    @BeforeEach
    void setUp() {
        cache = new PlanCacheService();
        ReflectionTestUtils.setField(cache, "ttlSeconds", 1800L);
    }

    private PlanGenResultVO vo(String summary) {
        PlanGenResultVO v = new PlanGenResultVO();
        v.setSummary(summary);
        v.setTotalHours(BigDecimal.valueOf(40));
        v.setGenType("AI_HYBRID");
        return v;
    }

    @Test
    @DisplayName("写入后能取到，值一致")
    void putThenGet() {
        String key = cache.buildKey(1L, 40, PlanMode.HYBRID, "abc");
        cache.put(key, vo("第一次"));
        assertThat(cache.get(key)).isNotNull();
        assertThat(cache.get(key).getSummary()).isEqualTo("第一次");
    }

    @Test
    @DisplayName("模式不同不串味：同一员工同一差距，HYBRID 的结果不会给 RULE")
    void differentModeDifferentKey() {
        String hybrid = cache.buildKey(1L, 40, PlanMode.HYBRID, "abc");
        String rule = cache.buildKey(1L, 40, PlanMode.RULE, "abc");
        cache.put(hybrid, vo("混合方案"));
        assertThat(cache.get(rule)).isNull();
    }

    @Test
    @DisplayName("预算或差距变化后不命中")
    void budgetAndGapAffectKey() {
        String k1 = cache.buildKey(1L, 40, PlanMode.HYBRID, "gap-a");
        String k2 = cache.buildKey(1L, 60, PlanMode.HYBRID, "gap-a");
        String k3 = cache.buildKey(1L, 40, PlanMode.HYBRID, "gap-b");
        cache.put(k1, vo("40学时"));
        assertThat(cache.get(k2)).isNull();
        assertThat(cache.get(k3)).isNull();
    }

    @Test
    @DisplayName("按员工失效：采纳后不会再把旧方案给出去")
    void invalidateUser() {
        cache.put(cache.buildKey(1L, 40, PlanMode.HYBRID, "x"), vo("旧"));
        cache.put(cache.buildKey(2L, 40, PlanMode.HYBRID, "x"), vo("别人的"));
        int removed = cache.invalidateUser(1L);
        assertThat(removed).isEqualTo(1);
        assertThat(cache.get(cache.buildKey(1L, 40, PlanMode.HYBRID, "x"))).isNull();
        assertThat(cache.get(cache.buildKey(2L, 40, PlanMode.HYBRID, "x"))).isNotNull();
    }

    @Test
    @DisplayName("过期后不命中")
    void expires() {
        ReflectionTestUtils.setField(cache, "ttlSeconds", 0L);
        String key = cache.buildKey(1L, 40, PlanMode.HYBRID, "x");
        cache.put(key, vo("瞬时"));
        assertThat(cache.get(key)).isNull();
    }

    @Test
    @DisplayName("命中率统计正确（1 中 1 失 = 50%）")
    void stats() {
        String key = cache.buildKey(1L, 40, PlanMode.HYBRID, "x");
        cache.get(key);          // miss
        cache.put(key, vo("a"));
        cache.get(key);          // hit
        Map<String, Object> s = cache.stats();
        assertThat(s.get("hits")).isEqualTo(1L);
        assertThat(s.get("misses")).isEqualTo(1L);
        assertThat(s.get("hitRate")).isEqualTo(50.0);
    }
}
