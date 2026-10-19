package com.aitraining.module.ai.llm;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    private Llm llm = new Llm();
    private Rag rag = new Rag();

    @Data
    public static class Llm {
        private boolean enabled = true;
        private String baseUrl = "https://api.deepseek.com";
        private String apiKey = "";
        private String model = "deepseek-chat";
        private double temperature = 0.3;
        private int maxTokens = 2000;
        private int timeoutSeconds = 90;
        private int maxRetry = 2;

        public boolean available() {
            return enabled && apiKey != null && !apiKey.isBlank();
        }
    }

    @Data
    public static class Rag {
        private boolean enabled = true;
        private int topK = 5;
        private double minScore = 0.15;
        private int maxContextChars = 3000;
    }
}
