package com.aitraining.module.exam.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.io.Serializable;
import java.math.BigDecimal;
import lombok.Data;

/**
 * exam_paper_question
 */
@Data
@TableName("exam_paper_question")
public class ExamPaperQuestion implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long paperId;

    private Long questionId;

    private Integer seqNo;

    private BigDecimal score;

}