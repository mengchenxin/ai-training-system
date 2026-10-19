package com.aitraining.module.skill.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 员工技能差距分析结果
 */
@Data
public class GapResultVO {
    private Long userId;
    private String realName;
    private String postName;
    private String deptName;
    /** 加权总差距 */
    private BigDecimal totalWeightedGap;
    /** 岗位要求加权总量 */
    private BigDecimal totalWeightedTarget;
    /** 岗位达标率（%） */
    private BigDecimal matchRate;
    /** 存在差距的技能项数 */
    private Integer gapCount;
    /** 差距明细，按优先级降序 */
    private List<GapItemVO> items;
    /** 雷达图：技能名 */
    private List<String> radarIndicators;
    /** 雷达图：当前等级 */
    private List<Integer> radarCurrent;
    /** 雷达图：目标等级 */
    private List<Integer> radarTarget;
    /** 文字化差距摘要，可直接拼入 Prompt */
    private String gapSummary;
}
