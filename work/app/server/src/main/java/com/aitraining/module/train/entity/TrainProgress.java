package com.aitraining.module.train.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * train_progress
 */
@Data
@TableName("train_progress")
public class TrainProgress implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long planId;

    /** 关联的计划课程行 */
    private Long planCourseId;

    private Long courseId;

    /** 上报人/学习人 */
    private Long userId;

    /** 完成百分比 */
    private BigDecimal progressPercent;

    private String status;

    /** 实际投入学时 */
    private BigDecimal spentHours;

    /** 最近上报时间 */
    private LocalDateTime reportTime;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}