package com.family.security;

import com.family.entity.SysUser;
import com.family.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SecurityHelper {

    private final SysUserMapper sysUserMapper;

    public LoginUser current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof LoginUser)) {
            return null;
        }
        return (LoginUser) auth.getPrincipal();
    }

    public Long currentUserId() {
        LoginUser u = current();
        return u == null ? null : u.getUserId();
    }

    public SysUser currentUser() {
        LoginUser u = current();
        if (u == null) return null;
        return sysUserMapper.selectById(u.getUserId());
    }

    public boolean isOwner() {
        LoginUser u = current();
        return u != null && "OWNER".equals(u.getRole());
    }
}
