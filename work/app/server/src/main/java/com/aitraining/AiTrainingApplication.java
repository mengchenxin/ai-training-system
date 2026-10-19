package com.aitraining;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 基于 Spring Boot 的 AI 培训计划管理系统 启动类
 */
@SpringBootApplication
@MapperScan("com.aitraining.module.**.mapper")
public class AiTrainingApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiTrainingApplication.class, args);
    }
}
