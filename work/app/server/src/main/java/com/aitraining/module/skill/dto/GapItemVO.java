package com.aitraining.module.skill.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 单项技能差距
 */
@Data
public class GapItemVO {
    private Long skillId;
    private String skillName;
    private String category;
    /** 岗位目标等级 */
    private Integer targetLevel;
    /** 员工当前等级 */
    private Integer currentLevel;
    /** 等级差距 = max(0, target - current) */
    private Integer gap;
    /** 技能对本岗位的重要度权重 */
    private BigDecimal weight;
    /** 加权差距 = gap × weight */
    private BigDecimal weightedGap;
    /** 归一化到 0-100 的优先级得分 */
    private BigDecimal priorityScore;
    /** 优先级排名，1 为最优先 */
    private Integer priorityRank;
    /** 优先级分档：高 / 中 / 低 / 已达标 */
    private String priorityLevel;
    /** 是否已评估过该技能 */
    private Boolean assessed;
}
