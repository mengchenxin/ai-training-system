package com.aitraining.module.ai;

import com.aitraining.module.ai.dto.PlanGenResultVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 培训方案结果缓存。
 *
 * <p>解决的问题：同一员工、同一份技能差距反复点「生成方案」时，会重复调用大模型。
 * 单次约 1700 tokens，金额可忽略，但 3 秒等待与不可复现的输出对演示体验很不友好。
 *
 * <p>键 = 员工ID + 学时预算 + 生成模式 + 技能差距摘要哈希。
 * 差距变了、预算改了、换了模式都不会命中旧结果，避免张冠李戴。
 * 只缓存成功结果，降级结果不入缓存（否则一次接口抖动会把 10 分钟的演示期全钉在降级方案上）。
 *
 * <p>命中率通过 {@link #stats()} 暴露，作为「缓存有效性」这一工程指标。
 */
@Slf4j
@Component
public class PlanCacheService {

    /** 缓存存活时长，默认 30 分钟；演示场景下足够覆盖一轮完整走查 */
    @Value("${ai.plan.cache-ttl-seconds:1800}")
    private long ttlSeconds = 1800;

    /** 最多保留条数，超出按写入顺序淘汰最早过期的 */
    private static final int MAX_ENTRIES = 200;

    private final Map<String, Entry> store = new ConcurrentHashMap<>();
    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();

    public static class Entry {
        final PlanGenResultVO value;
        final long expireAt;

        Entry(PlanGenResultVO value, long expireAt) {
            this.value = value;
            this.expireAt = expireAt;
        }
    }

    public String buildKey(Long userId, Object budget, PlanMode mode, String gapHash) {
        return userId + "|" + budget + "|" + mode.name() + "|" + gapHash;
    }

    public PlanGenResultVO get(String key) {
        Entry e = store.get(key);
        if (e == null) {
            misses.incrementAndGet();
            return null;
        }
        if (e.expireAt <= System.currentTimeMillis()) {
            store.remove(key);
            misses.incrementAndGet();
            return null;
        }
        hits.incrementAndGet();
        return e.value;
    }

    public void put(String key, PlanGenResultVO value) {
        if (value == null) {
            return;
        }
        if (store.size() >= MAX_ENTRIES) {
            evictExpired();
            if (store.size() >= MAX_ENTRIES) {
                // 仍然满：清掉最早到期的一批（简单策略，够用）
                store.entrySet().stream()
                        .sorted((a, b) -> Long.compare(a.getValue().expireAt, b.getValue().expireAt))
                        .limit(MAX_ENTRIES / 4)
                        .map(Map.Entry::getKey)
                        .toList()
                        .forEach(store::remove);
            }
        }
        store.put(key, new Entry(value, System.currentTimeMillis() + ttlSeconds * 1000));
    }

    public void evictExpired() {
        long now = System.currentTimeMillis();
        store.entrySet().removeIf(e -> e.getValue().expireAt <= now);
    }

    /** 某员工数据变更（采纳、改差距、转计划）时主动失效，避免拿到过期方案 */
    public int invalidateUser(Long userId) {
        String prefix = userId + "|";
        int before = store.size();
        store.keySet().removeIf(k -> k.startsWith(prefix));
        return before - store.size();
    }

    public void clear() {
        store.clear();
    }

    public int size() {
        return store.size();
    }

    public Map<String, Object> stats() {
        long h = hits.get();
        long m = misses.get();
        long total = h + m;
        double rate = total == 0 ? 0 : Math.round(h * 10000.0 / total) / 100.0;
        return Map.of("size", store.size(), "hits", h, "misses", m, "hitRate", rate);
    }
}
