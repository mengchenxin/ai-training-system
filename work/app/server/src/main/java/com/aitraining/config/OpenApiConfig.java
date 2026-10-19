package com.aitraining.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI().info(new Info()
                .title("AI 培训计划管理系统 接口文档")
                .description("基于 Spring Boot 的 AI 培训计划管理系统 —— 六大业务模块 + AI 增强模块")
                .version("1.0.0"));
    }
}
