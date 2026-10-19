package com.aitraining.module.ai.rag;

import lombok.Data;

/**
 * 检索命中的知识片段
 */
@Data
public class RagHit {
    private Long chunkId;
    private Long docId;
    private String docName;
    private String docType;
    private String source;
    private String content;
    /** 归一化后的相关度，0-1 */
    private double score;
}
