package com.aitraining.module.ai.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 计划中的一门课（规则引擎产出，可被大模型改写说明）
 */
@Data
public class PlannedCourse {
    private Long courseId;
    private String courseName;
    private Integer seqNo;
    private BigDecimal hours;
    private Long coverSkillId;
    private String coverSkill;
    private Integer gapBefore;
    private Integer courseLevel;
    private BigDecimal priorityScore;
    private String reason;
}
