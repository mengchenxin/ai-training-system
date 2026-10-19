package com.aitraining.module.ai.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * ai_prompt_template
 */
@Data
@TableName("ai_prompt_template")
public class AiPromptTemplate implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 模板编码 */
    private String templateCode;

    private String templateName;

    private String scene;

    /** Prompt 模板正文，用 {{变量}} 占位 */
    private String content;

    /** 期望输入的变量说明与校验规则 */
    private String inputSchema;

    /** 期望输出的 JSON 结构，用于校验 */
    private String outputSchema;

    private String model;

    private BigDecimal temperature;

    private Integer maxTokens;

    /** 版本号，便于版本对比实验 */
    private Integer version;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Integer delFlag;

}