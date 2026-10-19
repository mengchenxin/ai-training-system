package com.aitraining.module.exam.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * question
 */
@Data
@TableName("question")
public class Question implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属技能点 */
    private Long skillId;

    /** 知识点 */
    private String knowledgePoint;

    private String questionType;

    /** 题干 */
    private String content;

    /** 选项：[{"key":"A","text":"..."}] */
    private String options;

    /** 参考答案 */
    private String answer;

    /** 解析 */
    private String analysis;

    /** 建议分值 */
    private BigDecimal score;

    /** 难度 1易 2中 3难 */
    private Integer difficulty;

    private String genType;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Integer delFlag;

}