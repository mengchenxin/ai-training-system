package com.aitraining.module.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * sys_user
 */
@Data
@TableName("sys_user")
public class SysUser implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 登录账号 */
    private String username;

    /** BCrypt 密文 */
    private String password;

    /** 姓名 */
    private String realName;

    /** 所属部门 */
    private Long deptId;

    /** 所属岗位 */
    private Long postId;

    private String phone;

    private String email;

    private String avatar;

    /** 1启用 0停用 */
    private Integer status;

    private LocalDateTime lastLoginAt;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Integer delFlag;

}