package com.aitraining.module.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * post
 */
@Data
@TableName("post")
public class Post implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 岗位编码 */
    private String postCode;

    /** 岗位名称 */
    private String postName;

    /** 归属部门 */
    private Long deptId;

    /** 岗位职责说明 */
    private String postDesc;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Integer delFlag;

}