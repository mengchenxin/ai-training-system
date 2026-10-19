package com.aitraining.module.ai.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 大语言模型客户端（OpenAI 兼容协议，默认对接 DeepSeek）。
 *
 * <p>设计要点：
 * <ul>
 *   <li>未配置 apiKey 时快速失败，由上层降级为规则方案，不阻塞业务流程；</li>
 *   <li>失败自动重试 {@code ai.llm.max-retry} 次，指数退避；</li>
 *   <li>强制 {@code response_format=json_object}，提高结构化输出合规率。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmClient {

    private final AiProperties props;
    private final ObjectMapper objectMapper;

    private RestClient client;

    private RestClient client() {
        if (client == null) {
            client = RestClient.builder()
                    .baseUrl(props.getLlm().getBaseUrl())
                    .build();
        }
        return client;
    }

    public boolean available() {
        return props.getLlm().available();
    }

    public String modelName() {
        return props.getLlm().getModel();
    }

    /**
     * 单轮对话，要求 JSON 输出
     */
    public LlmResponse chat(String systemPrompt, String userPrompt, boolean jsonMode) {
        long start = System.currentTimeMillis();
        if (!available()) {
            LlmResponse r = LlmResponse.fail("未配置大模型 API Key");
            r.setCostMs((int) (System.currentTimeMillis() - start));
            return r;
        }
        int maxRetry = Math.max(0, props.getLlm().getMaxRetry());
        String lastError = null;
        for (int attempt = 1; attempt <= maxRetry + 1; attempt++) {
            try {
                LlmResponse resp = doCall(systemPrompt, userPrompt, jsonMode);
                resp.setAttempts(attempt);
                resp.setCostMs((int) (System.currentTimeMillis() - start));
                return resp;
            } catch (Exception e) {
                lastError = e.getClass().getSimpleName() + ": " + e.getMessage();
                log.warn("LLM 调用失败（第 {} 次）：{}", attempt, lastError);
                if (attempt <= maxRetry) {
                    try {
                        Thread.sleep(800L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        LlmResponse r = LlmResponse.fail(lastError == null ? "调用失败" : lastError);
        r.setAttempts(maxRetry + 1);
        r.setCostMs((int) (System.currentTimeMillis() - start));
        return r;
    }

    private LlmResponse doCall(String systemPrompt, String userPrompt, boolean jsonMode) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", props.getLlm().getModel());
        body.put("temperature", props.getLlm().getTemperature());
        body.put("max_tokens", props.getLlm().getMaxTokens());
        if (jsonMode) {
            body.put("response_format", Map.of("type", "json_object"));
        }
        List<Map<String, String>> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(Map.of("role", "system", "content", systemPrompt));
        }
        messages.add(Map.of("role", "user", "content", userPrompt));
        body.put("messages", messages);

        String raw = client().post()
                .uri("/chat/completions")
                .header("Authorization", "Bearer " + props.getLlm().getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);

        JsonNode root = objectMapper.readTree(raw);
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            throw new IllegalStateException("响应中无 choices：" + preview(raw));
        }
        String content = choices.get(0).path("message").path("content").asText();
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("模型返回内容为空");
        }
        LlmResponse resp = new LlmResponse();
        resp.setSuccess(true);
        resp.setContent(content);
        resp.setModel(root.path("model").asText(props.getLlm().getModel()));
        resp.setPromptTokens(root.path("usage").path("prompt_tokens").asInt(0));
        resp.setCompletionTokens(root.path("usage").path("completion_tokens").asInt(0));
        return resp;
    }

    private String preview(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 200 ? s.substring(0, 200) + "..." : s;
    }
}
