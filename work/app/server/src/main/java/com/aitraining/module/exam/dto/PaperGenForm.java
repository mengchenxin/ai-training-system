package com.aitraining.module.exam.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class PaperGenForm {
    private String paperName;
    private Long planId;
    private List<Long> skillIds;
    private Integer easyCount = 0;
    private Integer mediumCount = 0;
    private Integer hardCount = 0;
    private BigDecimal passScore = BigDecimal.valueOf(60);
    private BigDecimal totalScore = BigDecimal.valueOf(100);
    /** 是否包含简答题 */
    private Boolean includeShort = true;
}
