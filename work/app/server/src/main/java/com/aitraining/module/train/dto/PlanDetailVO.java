package com.aitraining.module.train.dto;

import com.aitraining.module.train.entity.PlanCourse;
import com.aitraining.module.train.entity.TrainPlan;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class PlanDetailVO {
    private TrainPlan plan;
    private String targetUserName;
    private String targetDeptName;
    private String targetPostName;
    private String creatorName;
    private List<PlanCourse> courses;
    /** 每门课程的名称、技能与完成情况 */
    private List<Map<String, Object>> courseStats;
    /** 计划整体完成率 */
    private BigDecimal finishRate;
    /** 目标人数 */
    private Integer userCount;
    /** 状态为已完成的人数 */
    private Integer finishedUserCount;
}
