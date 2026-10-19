package com.aitraining.module.ai.llm;

import lombok.Data;

@Data
public class LlmResponse {

    private boolean success;
    private String content;
    private String model;
    private Integer promptTokens;
    private Integer completionTokens;
    private int costMs;
    private int attempts;
    /** 失败原因 */
    private String error;

    public static LlmResponse fail(String error) {
        LlmResponse r = new LlmResponse();
        r.success = false;
        r.error = error;
        return r;
    }
}
