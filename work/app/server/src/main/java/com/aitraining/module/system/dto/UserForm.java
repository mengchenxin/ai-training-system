package com.aitraining.module.system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class UserForm {
    private Long id;

    @NotBlank(message = "账号不能为空")
    private String username;

    /** 新增时必填；修改时为空表示不修改密码 */
    private String password;

    @NotBlank(message = "姓名不能为空")
    private String realName;

    private Long deptId;
    private Long postId;
    private String phone;
    private String email;
    private Integer status;
    private List<Long> roleIds;
}
