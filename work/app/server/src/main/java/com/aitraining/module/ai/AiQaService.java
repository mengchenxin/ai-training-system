package com.aitraining.module.ai;

import com.aitraining.module.ai.entity.AiPromptTemplate;
import com.aitraining.module.ai.entity.AiQaLog;
import com.aitraining.module.ai.llm.LlmClient;
import com.aitraining.module.ai.llm.LlmResponse;
import com.aitraining.module.ai.mapper.AiQaLogMapper;
import com.aitraining.module.ai.rag.RagHit;
import com.aitraining.module.ai.rag.RagService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 知识库问答：先检索、后生成，答案必须基于检索片段（检索增强生成）。
 * 模型不可用时退化为「返回检索到的原文片段」，仍然可用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiQaService {

    private final RagService ragService;
    private final LlmClient llmClient;
    private final PromptService promptService;
    private final AiQaLogMapper qaLogMapper;
    private final ObjectMapper objectMapper;

    public Map<String, Object> ask(Long userId, String question) {
        Map<String, Object> out = new LinkedHashMap<>();
        long start = System.currentTimeMillis();
        List<RagHit> hits = ragService.search(question, 5);
        out.put("refs", hits.stream().map(h -> Map.of(
                "docName", h.getDocName(),
                "score", h.getScore(),
                "snippet", h.getContent().length() > 100
                        ? h.getContent().substring(0, 100) + "…" : h.getContent())).toList());

        String answer;
        boolean llmUsed = false;
        if (hits.isEmpty()) {
            answer = "知识库中未收录与该问题相关的内容。可以尝试换个说法，或联系讲师补充知识库文档。";
        } else if (llmClient.available()) {
            AiPromptTemplate tpl = promptService.getByScene("QA");
            Map<String, Object> vars = new HashMap<>();
            vars.put("ragContext", ragService.buildContext(hits));
            vars.put("question", question);
            String prompt = tpl == null
                    ? "根据以下资料回答问题：\n" + ragService.buildContext(hits) + "\n问题：" + question
                    : PromptService.render(tpl.getContent(), vars);
            LlmResponse resp = llmClient.chat("你是企业培训知识助手，回答必须基于给定资料，不得编造。", prompt, false);
            if (resp.isSuccess()) {
                answer = resp.getContent().trim();
                llmUsed = true;
            } else {
                answer = "（大模型暂不可用，以下为检索到的原文，供参考）\n\n"
                        + hits.get(0).getContent();
            }
        } else {
            answer = "（未配置大模型，以下为检索到的原文，供参考）\n\n" + hits.get(0).getContent();
        }
        long cost = System.currentTimeMillis() - start;

        AiQaLog logRow = new AiQaLog();
        logRow.setUserId(userId);
        logRow.setQuestion(question.length() > 1000 ? question.substring(0, 1000) : question);
        logRow.setAnswer(answer);
        logRow.setCostMs((int) cost);
        logRow.setHitScore(hits.isEmpty() ? null
                : java.math.BigDecimal.valueOf(hits.get(0).getScore()));
        try {
            logRow.setRefChunks(objectMapper.writeValueAsString(hits.stream().map(h -> Map.of(
                    "chunkId", h.getChunkId(), "docName", h.getDocName(), "score", h.getScore())).toList()));
        } catch (Exception ignore) {
        }
        qaLogMapper.insert(logRow);

        out.put("answer", answer);
        out.put("llmUsed", llmUsed);
        out.put("costMs", cost);
        out.put("logId", logRow.getId());
        return out;
    }

    public void feedback(Long logId, Integer feedback) {
        AiQaLog l = new AiQaLog();
        l.setId(logId);
        l.setFeedback(feedback);
        qaLogMapper.updateById(l);
    }

    /** 检索预览：不加模型，直接看命中片段与得分，用于演示 RAG 效果 */
    public List<Map<String, Object>> preview(String query) {
        return ragService.search(query, 5).stream().map(h -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("chunkId", h.getChunkId());
            m.put("docName", h.getDocName());
            m.put("docType", h.getDocType());
            m.put("score", h.getScore());
            m.put("content", h.getContent());
            return m;
        }).toList();
    }
}
