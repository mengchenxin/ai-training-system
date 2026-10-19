package com.aitraining.module.train.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * train_plan
 */
@Data
@TableName("train_plan")
public class TrainPlan implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 计划编号 */
    private String planNo;

    /** 计划名称 */
    private String planName;

    private String planType;

    /** 个人计划的目标员工 */
    private Long targetUserId;

    /** 部门计划的目标部门 */
    private Long targetDeptId;

    /** 岗位计划的目标岗位 */
    private Long targetPostId;

    private LocalDate startDate;

    private LocalDate endDate;

    private String status;

    private String genType;

    /** AI 生成记录ID（可解释性溯源） */
    private Long aiRecordId;

    private Long creatorId;

    /** 审核人 */
    private Long auditorId;

    private LocalDateTime auditTime;

    private String auditRemark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Integer delFlag;

}