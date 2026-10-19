package com.aitraining.module.ai.dto;

import com.aitraining.module.ai.rag.RagHit;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 个性化培训方案生成结果
 */
@Data
public class PlanGenResultVO {
    private Long userId;
    private String realName;
    private String postName;
    /** 方案总体说明 */
    private String summary;
    private BigDecimal totalHours;
    private List<PlannedCourse> courses;
    /** 生成方式：AI_HYBRID 规则+大模型 / AI_RULE 纯规则降级 */
    private String genType;
    /** AI 生成记录 ID，可用于采纳/驳回与实验统计 */
    private Long aiRecordId;
    /** 是否走了大模型 */
    private Boolean llmUsed;
    /** 降级或异常原因 */
    private String fallbackReason;
    /** RAG 引用来源 */
    private List<RefSource> refs;
    /** 生成耗时 */
    private Integer costMs;
    /** 结构化校验是否通过 */
    private Boolean schemaValid;

    @Data
    public static class RefSource {
        private String docName;
        private Double score;
        private String snippet;

        public static RefSource of(RagHit hit) {
            RefSource r = new RefSource();
            r.setDocName(hit.getDocName());
            r.setScore(hit.getScore());
            String c = hit.getContent();
            r.setSnippet(c.length() > 90 ? c.substring(0, 90) + "…" : c);
            return r;
        }
    }
}
