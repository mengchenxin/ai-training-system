package com.aitraining.module.ai.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * ai_qa_log
 */
@Data
@TableName("ai_qa_log")
public class AiQaLog implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String question;

    private String answer;

    /** 引用的知识片段ID与相似度 */
    private String refChunks;

    /** 最高相似度，用于检索质量分析 */
    private BigDecimal hitScore;

    private Integer costMs;

    /** 用户反馈：1有帮助 0无帮助，用于答案准确率统计 */
    private Integer feedback;

    private LocalDateTime createTime;

}