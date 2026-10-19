package com.aitraining.module.auth.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class LoginResult {
    private String token;
    private Long userId;
    private String username;
    private String realName;
    private List<String> roles;
}
