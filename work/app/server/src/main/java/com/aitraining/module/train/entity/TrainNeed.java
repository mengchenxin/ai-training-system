package com.aitraining.module.train.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * train_need
 */
@Data
@TableName("train_need")
public class TrainNeed implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 需求标题 */
    private String needTitle;

    private Long deptId;

    private Long postId;

    /** 对应技能点 */
    private Long skillId;

    /** 需求描述 */
    private String needDesc;

    private String source;

    /** 优先级 1高 2中 3低 */
    private Integer priority;

    /** 提报人 */
    private Long submitterId;

    private String auditStatus;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Integer delFlag;

}