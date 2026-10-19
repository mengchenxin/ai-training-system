package com.aitraining.module.skill.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * skill
 */
@Data
@TableName("skill")
public class Skill implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 技能编码 */
    private String skillCode;

    /** 技能名称 */
    private String skillName;

    /** 技能类别：安全/技术/通用/管理 */
    private String category;

    /** 等级上限（默认5级） */
    private Integer maxLevel;

    /** 技能说明，用于知识库与Prompt上下文 */
    private String skillDesc;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Integer delFlag;

}