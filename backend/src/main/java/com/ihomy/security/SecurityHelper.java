package com.ihomy.security;

import com.ihomy.entity.SysUser;
import com.ihomy.mapper.SysRoleMapper;
import com.ihomy.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 安全上下文助手:供 service 层获取当前用户/家庭,并做权限码校验。
 * 权限模型:OWNER 恒有全部权限,其余角色按 用户-角色-权限 关联表查询。
 */
@Component
@RequiredArgsConstructor
public class SecurityHelper {

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;

    /** 当前登录用户上下文,未登录返回 null */
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

    /** 当前用户完整实体(查库),未登录返回 null */
    public SysUser currentUser() {
        LoginUser u = current();
        if (u == null) return null;
        return sysUserMapper.selectById(u.getUserId());
    }

    /** 当前用户是否为当前家庭的 OWNER */
    public boolean isOwner() {
        LoginUser u = current();
        return u != null && "OWNER".equals(u.getRole());
    }

    /** 校验当前用户是否拥有权限码(OWNER 恒真) */
    public boolean hasPermission(String code) {
        LoginUser u = current();
        if (u == null) return false;
        // OWNER 与 OPS（运维角色,由 OpsAccessFilter 另行限制访问范围）恒真
        if ("OWNER".equals(u.getRole()) || "OPS".equals(u.getRole())) return true;
        Set<String> codes = permissionCodes(u);
        return codes.contains(code);
    }

    /** 查询该用户在当前家庭拥有的全部权限码 */
    private Set<String> permissionCodes(LoginUser u) {
        List<String> list = sysRoleMapper.selectAuthCodesByUserAndFamily(u.getUserId(), u.getFamilyId());
        return new HashSet<>(list == null ? List.of() : list);
    }
}
