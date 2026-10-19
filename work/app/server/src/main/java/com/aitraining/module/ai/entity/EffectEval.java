package com.aitraining.module.ai.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * effect_eval
 */
@Data
@TableName("effect_eval")
public class EffectEval implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long planId;

    private Long userId;

    /** 培训前测成绩 */
    private BigDecimal preScore;

    /** 培训后测成绩 */
    private BigDecimal postScore;

    /** 成绩提升值 */
    private BigDecimal scoreImprove;

    /** 培训前技能差距快照 */
    private String gapBefore;

    /** 培训后技能差距快照 */
    private String gapAfter;

    /** 差距缩小率（%） */
    private BigDecimal gapReduce;

    /** 计划完成率（%） */
    private BigDecimal finishRate;

    /** AI 效果诊断结论 */
    private String diagnosis;

    /** AI 生成的补训方案 */
    private String retrainPlan;

    /** 结论：EXCELLENT优秀/PASS达标/FAIL未达标 */
    private String evalResult;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}