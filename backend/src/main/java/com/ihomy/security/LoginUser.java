package com.ihomy.security;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 当前登录用户上下文:由 JWT 解析而来,
 * 存于 SecurityContext,服务层通过 SecurityHelper 取用。
 */
@Data
@AllArgsConstructor
public class LoginUser {
    private Long userId;
    private String username;
    private String role;
    private Long familyId;
}
