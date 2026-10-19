package com.aitraining.module.auth.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 当前登录用户信息（前端据此渲染菜单与按钮）
 */
@Data
@Builder
public class UserInfoVO {
    private Long userId;
    private String username;
    private String realName;
    private String avatar;
    private Long deptId;
    private String deptName;
    private Long postId;
    private String postName;
    private List<String> roles;
    private List<String> roleNames;
    private List<String> permissions;
}
