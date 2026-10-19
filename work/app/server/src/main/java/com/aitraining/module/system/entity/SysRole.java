package com.aitraining.module.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * sys_role
 */
@Data
@TableName("sys_role")
public class SysRole implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 角色标识：ADMIN/LECTURER/EMPLOYEE */
    private String roleCode;

    /** 角色名称：管理员/讲师/员工 */
    private String roleName;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Integer delFlag;

}