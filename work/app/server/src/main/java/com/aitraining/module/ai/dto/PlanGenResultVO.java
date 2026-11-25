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
    /** 生成方式：AI_HYBRID 混合 / AI_RULE 纯规则 / AI_LLM_ONLY 纯大模型 */
    private String genType;
    /** 生成模式枚举名：RULE / HYBRID / LLM_ONLY，供前端与实验分组使用 */
    private String mode;
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
    /** 输入 tokens（模型调用成本指标） */
    private Integer promptTokens;
    /** 输出 tokens */
    private Integer completionTokens;
    /** 总 tokens */
    private Integer totalTokens;
    /** 本次使用的学时预算，用于判断方案是否超预算（纯大模型组常见） */
    private BigDecimal budget;
    /** 是否命中缓存（未重复调用模型） */
    private Boolean cached;
    /** 幻觉课程数（仅 LLM_ONLY 有意义） */
    private Integer hallucinationCount;
    /** 被判为幻觉、已剔除的课程名（便于答辩时举例） */
    private List<String> hallucinatedCourses;

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
