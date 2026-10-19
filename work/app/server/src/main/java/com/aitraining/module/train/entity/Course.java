package com.aitraining.module.train.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * course
 */
@Data
@TableName("course")
public class Course implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 课程编码 */
    private String courseCode;

    /** 课程名称 */
    private String courseName;

    /** 主要覆盖技能点 */
    private Long skillId;

    /** 课程类别 */
    private String category;

    /** 适用等级/难度 */
    private Integer level;

    /** 建议学时 */
    private BigDecimal durationHours;

    /** 课程简介（同时作为知识库语料） */
    private String intro;

    /** 课程资源地址 */
    private String resourceUrl;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Integer delFlag;

}