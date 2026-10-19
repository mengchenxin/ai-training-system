package com.aitraining.module.exam.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * exam_answer
 */
@Data
@TableName("exam_answer")
public class ExamAnswer implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long recordId;

    private Long questionId;

    private String userAnswer;

    /** 客观题是否正确 */
    private Integer isCorrect;

    private BigDecimal score;

    /** 大模型建议分（主观题） */
    private BigDecimal aiScore;

    /** 人工复核分（用于一致性实验） */
    private BigDecimal manualScore;

    /** 评分理由 */
    private String aiReason;

    private LocalDateTime createTime;

}