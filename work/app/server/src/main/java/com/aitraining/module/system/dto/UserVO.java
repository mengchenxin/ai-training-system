package com.aitraining.module.system.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class UserVO {
    private Long id;
    private String username;
    private String realName;
    private Long deptId;
    private String deptName;
    private Long postId;
    private String postName;
    private String phone;
    private String email;
    private Integer status;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createTime;
    private List<String> roles;
    private List<Long> roleIds;
}
