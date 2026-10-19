package com.aitraining.module.ai.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * ai_gen_record
 */
@Data
@TableName("ai_gen_record")
public class AiGenRecord implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String bizType;

    /** 关联业务ID（如计划ID、考试记录ID） */
    private Long bizId;

    /** 触发用户 */
    private Long userId;

    /** 使用的 Prompt 模板 */
    private Long templateId;

    /** 输入快照（差距表等），保证可复现 */
    private String inputSnapshot;

    /** 检索命中的知识片段ID与相似度，用于有/无RAG对比 */
    private String retrievedChunks;

    /** 模型原始输出 */
    private String outputContent;

    /** 解析校验后的结构化结果 */
    private String parsedOutput;

    private String model;

    private Integer promptTokens;

    private Integer completionTokens;

    /** 耗时（毫秒），性能指标 */
    private Integer costMs;

    /** 尝试次数（含重试） */
    private Integer attemptCount;

    private String status;

    private String adoptStatus;

    private String rejectReason;

    private LocalDateTime createTime;

}