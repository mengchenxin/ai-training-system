package com.aitraining.module.ai.rag;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.aitraining.module.ai.entity.KbChunk;
import com.aitraining.module.ai.entity.KbDoc;
import com.aitraining.module.ai.llm.AiProperties;
import com.aitraining.module.ai.mapper.KbChunkMapper;
import com.aitraining.module.ai.mapper.KbDocMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 轻量级检索增强（RAG）实现。
 *
 * <p>选型说明：知识片段规模在千级以内，引入向量数据库（Milvus/Elasticsearch）
 * 与外部 Embedding 服务会显著增加部署复杂度，而收益有限。
 * 因此在本地实现 <b>BM25 检索</b>：中文按 2-gram 切分、英文数字按词切分，
 * 全量片段常驻内存构建倒排索引，查询时按 BM25 打分取 Top-K。
 * 该方案离线可用、零外部依赖，且检索过程可解释（可回溯命中片段的得分）。
 *
 * <p>若后续需要语义匹配，可在 {@link #search} 中叠加向量余弦相似度做混合排序，
 * 索引结构已预留扩展位。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagService {

    private final KbChunkMapper chunkMapper;
    private final KbDocMapper docMapper;
    private final AiProperties props;

    /** BM25 参数 */
    private static final double K1 = 1.5;
    private static final double B = 0.75;

    private final List<Doc> docs = new ArrayList<>();
    private final Map<String, Integer> df = new HashMap<>();
    private double avgDocLen = 1;

    /** 内存索引条目 */
    private static class Doc {
        KbChunk chunk;
        KbDoc doc;
        Map<String, Integer> tf;
        int len;
    }

    @PostConstruct
    public void init() {
        reload();
    }

    public synchronized int reload() {
        docs.clear();
        df.clear();
        List<KbChunk> chunks = chunkMapper.selectList(new LambdaQueryWrapper<>());
        Map<Long, KbDoc> docMap = new HashMap<>();
        if (!chunks.isEmpty()) {
            Set<Long> docIds = chunks.stream().map(KbChunk::getDocId).collect(Collectors.toSet());
            docMapper.selectBatchIds(docIds).forEach(d -> docMap.put(d.getId(), d));
        }
        long totalLen = 0;
        for (KbChunk c : chunks) {
            Doc d = new Doc();
            d.chunk = c;
            d.doc = docMap.get(c.getDocId());
            d.tf = termFreq(tokenize(c.getContent()));
            d.len = d.tf.values().stream().mapToInt(Integer::intValue).sum();
            totalLen += d.len;
            for (String term : d.tf.keySet()) {
                df.merge(term, 1, Integer::sum);
            }
            docs.add(d);
        }
        avgDocLen = docs.isEmpty() ? 1 : Math.max(1, totalLen * 1.0 / docs.size());
        log.info("RAG 索引构建完成：文档片段 {} 条，词表 {} 个", docs.size(), df.size());
        return docs.size();
    }

    /**
     * 中文按 2-gram + 单字切分，英文/数字按词切分
     */
    static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        if (text == null) {
            return tokens;
        }
        StringBuilder en = new StringBuilder();
        char[] arr = text.toCharArray();
        for (int i = 0; i < arr.length; i++) {
            char ch = arr[i];
            if (isCjk(ch)) {
                if (!en.isEmpty()) {
                    tokens.add(en.toString().toLowerCase());
                    en.setLength(0);
                }
                tokens.add(String.valueOf(ch));
                if (i + 1 < arr.length && isCjk(arr[i + 1])) {
                    tokens.add("" + ch + arr[i + 1]);
                }
            } else if (Character.isLetterOrDigit(ch)) {
                en.append(ch);
            } else {
                if (!en.isEmpty()) {
                    tokens.add(en.toString().toLowerCase());
                    en.setLength(0);
                }
            }
        }
        if (!en.isEmpty()) {
            tokens.add(en.toString().toLowerCase());
        }
        return tokens;
    }

    private static boolean isCjk(char c) {
        Character.UnicodeScript s = Character.UnicodeScript.of(c);
        return s == Character.UnicodeScript.HAN;
    }

    private static Map<String, Integer> termFreq(List<String> tokens) {
        Map<String, Integer> tf = new HashMap<>();
        for (String t : tokens) {
            tf.merge(t, 1, Integer::sum);
        }
        return tf;
    }

    /**
     * 检索 Top-K 片段
     */
    public List<RagHit> search(String query, int topK) {
        if (!props.getRag().isEnabled() || query == null || query.isBlank() || docs.isEmpty()) {
            return List.of();
        }
        List<String> qTokens = tokenize(query).stream().distinct().toList();
        int n = docs.size();
        List<RagHit> hits = new ArrayList<>();
        double maxScore = 0;

        for (Doc d : docs) {
            double score = 0;
            for (String term : qTokens) {
                int dfv = df.getOrDefault(term, 0);
                if (dfv == 0) {
                    continue;
                }
                double idf = Math.log(1 + (n - dfv + 0.5) / (dfv + 0.5));
                int tf = d.tf.getOrDefault(term, 0);
                if (tf == 0) {
                    continue;
                }
                double denom = tf + K1 * (1 - B + B * d.len / avgDocLen);
                score += idf * (tf * (K1 + 1)) / denom;
            }
            if (score <= 0) {
                continue;
            }
            maxScore = Math.max(maxScore, score);
            RagHit hit = new RagHit();
            hit.setChunkId(d.chunk.getId());
            hit.setDocId(d.chunk.getDocId());
            hit.setDocName(d.doc == null ? "" : d.doc.getDocName());
            hit.setDocType(d.doc == null ? "" : d.doc.getDocType());
            hit.setSource(d.doc == null ? "" : d.doc.getSource());
            hit.setContent(d.chunk.getContent());
            hit.setScore(score);
            hits.add(hit);
        }
        if (maxScore <= 0) {
            return List.of();
        }
        // 归一到 0-1，便于设置阈值与展示
        for (RagHit h : hits) {
            h.setScore(Math.round(h.getScore() / maxScore * 10000) / 10000.0);
        }
        hits.sort(Comparator.comparingDouble(RagHit::getScore).reversed());
        int k = Math.min(topK <= 0 ? props.getRag().getTopK() : topK, hits.size());
        List<RagHit> out = new ArrayList<>();
        for (int i = 0; i < k; i++) {
            if (hits.get(i).getScore() >= props.getRag().getMinScore()) {
                out.add(hits.get(i));
            }
        }
        return out;
    }

    /**
     * 拼装为 Prompt 上下文，带来源标注（支撑可解释性与引用溯源）
     */
    public String buildContext(List<RagHit> hits) {
        if (hits == null || hits.isEmpty()) {
            return "（未检索到相关知识片段）";
        }
        StringBuilder sb = new StringBuilder();
        int i = 1;
        for (RagHit h : hits) {
            String block = String.format("[片段%d｜来源：%s｜相关度：%.2f]%n%s%n%n",
                    i++, h.getDocName(), h.getScore(), h.getContent());
            if (sb.length() + block.length() > props.getRag().getMaxContextChars()) {
                break;
            }
            sb.append(block);
        }
        return sb.toString();
    }

    public int indexSize() {
        return docs.size();
    }
}
