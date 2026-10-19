package com.aitraining.module.ai.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * kb_doc
 */
@Data
@TableName("kb_doc")
public class KbDoc implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 文档名称 */
    private String docName;

    private String docType;

    /** 来源说明（如《国家职业技能标准》），用于方案引用溯源 */
    private String source;

    /** 落库文件路径或原文位置 */
    private String filePath;

    /** 切分片段数 */
    private Integer chunkCount;

    /** 索引状态：PENDING/PROCESSING/READY/FAILED */
    private String status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Integer delFlag;

}