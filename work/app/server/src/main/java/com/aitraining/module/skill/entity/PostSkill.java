package com.aitraining.module.skill.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * post_skill
 */
@Data
@TableName("post_skill")
public class PostSkill implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 岗位ID */
    private Long postId;

    /** 技能ID */
    private Long skillId;

    /** 岗位要求等级 */
    private Integer targetLevel;

    /** 该技能对本岗位的重要度权重 */
    private BigDecimal weight;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Integer delFlag;

}