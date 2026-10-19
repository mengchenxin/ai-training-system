package com.aitraining.module.train.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * plan_course
 */
@Data
@TableName("plan_course")
public class PlanCourse implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 计划ID */
    private Long planId;

    /** 课程ID */
    private Long courseId;

    /** 建议学习顺序 */
    private Integer seqNo;

    /** 计划学时 */
    private BigDecimal expectHours;

    /** 本课程解决的目标技能点 */
    private Long coverSkillId;

    /** 安排时的差距值（等级差） */
    private Integer gapBefore;

    /** 安排依据（AI生成时的说明，支撑可解释性） */
    private String planReason;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}