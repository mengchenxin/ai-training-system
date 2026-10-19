package com.aitraining.module.ai.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * kb_chunk
 */
@Data
@TableName("kb_chunk")
public class KbChunk implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long docId;

    /** 片段序号 */
    private Integer chunkIndex;

    /** 片段正文 */
    private String content;

    /** 近似字数 */
    private Integer tokenCount;

    private String embedding;

    /** 使用的嵌入模型名称 */
    private String vecModel;

    private LocalDateTime createTime;

}