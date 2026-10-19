package com.aitraining.module.train.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class PlanForm {

    private Long id;

    private String planNo;

    @NotBlank(message = "计划名称不能为空")
    private String planName;

    /** PERSONAL / DEPT / POST / BATCH */
    private String planType;

    private Long targetUserId;
    private Long targetDeptId;
    private Long targetPostId;

    private LocalDate startDate;
    private LocalDate endDate;

    /** MANUAL / AI_RULE / AI_LLM / AI_HYBRID */
    private String genType;

    private Long aiRecordId;

    private List<PlanCourseForm> courses;

    @Data
    public static class PlanCourseForm {
        private Long courseId;
        private Integer seqNo;
        private BigDecimal expectHours;
        private Long coverSkillId;
        private Integer gapBefore;
        private String planReason;
    }
}
