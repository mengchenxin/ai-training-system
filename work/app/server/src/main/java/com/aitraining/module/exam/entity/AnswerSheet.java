package com.aitraining.module.exam.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * answer_sheet
 */
@Data
@TableName("answer_sheet")
public class AnswerSheet implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 试卷ID */
    private Long paperId;

    /** 考生 */
    private Long userId;

    /** ONGOING作答中/SUBMITTED已交卷 */
    private String status;

    private LocalDateTime startTime;

    private LocalDateTime submitTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}