package com.aitraining.module.skill.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * emp_skill
 */
@Data
@TableName("emp_skill")
public class EmpSkill implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 员工ID */
    private Long userId;

    /** 技能ID */
    private Long skillId;

    /** 当前等级 */
    private Integer currentLevel;

    private String assessSource;

    /** 评定时间 */
    private LocalDateTime assessTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Integer delFlag;

}