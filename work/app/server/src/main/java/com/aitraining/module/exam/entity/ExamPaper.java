package com.aitraining.module.exam.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * exam_paper
 */
@Data
@TableName("exam_paper")
public class ExamPaper implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String paperName;

    /** 关联培训计划 */
    private Long planId;

    private BigDecimal totalScore;

    private BigDecimal passScore;

    private String genType;

    /** 组卷规则：知识点与难度分布 */
    private String ruleJson;

    /** 1启用 0停用 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Integer delFlag;

}