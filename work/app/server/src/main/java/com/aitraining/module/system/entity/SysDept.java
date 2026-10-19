package com.aitraining.module.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * sys_dept
 */
@Data
@TableName("sys_dept")
public class SysDept implements Serializable {

    /** 部门ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 上级部门ID */
    private Long parentId;

    /** 部门名称 */
    private String deptName;

    /** 部门负责人用户ID */
    private Long leaderId;

    /** 排序 */
    private Integer sortNo;

    /** 状态：1启用 0停用 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /** 0正常 1删除 */
    private Integer delFlag;

}