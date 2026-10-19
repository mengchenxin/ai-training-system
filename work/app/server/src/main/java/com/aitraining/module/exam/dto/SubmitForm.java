package com.aitraining.module.exam.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class SubmitForm {
    /** PRE 培训前测 / POST 培训后测 */
    private String examStage = "POST";
    private Long planId;
    private List<AnswerItem> answers;

    @Data
    public static class AnswerItem {
        private Long questionId;
        private String userAnswer;
        private BigDecimal manualScore;
    }
}
