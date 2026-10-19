package com.aitraining.module.exam.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * exam_record
 */
@Data
@TableName("exam_record")
public class ExamRecord implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long paperId;

    private Long userId;

    /** 关联培训计划，用于前后测对比 */
    private Long planId;

    private String examStage;

    /** 客观题得分（自动判分） */
    private BigDecimal objectiveScore;

    /** 主观题得分 */
    private BigDecimal subjectiveScore;

    private BigDecimal totalScore;

    /** 是否达标 */
    private Integer passed;

    private LocalDateTime submitTime;

    /** AI评语（主观题辅助评分与评语） */
    private String aiComment;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}