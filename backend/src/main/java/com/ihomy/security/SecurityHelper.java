package com.ihomy.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ihomy.entity.SysUser;
import com.ihomy.mapper.SysRoleMapper;
import com.ihomy.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 安全上下文助手:供 service 层获取当前用户/家庭,并做权限码校验。
 * 权限模型:OWNER 恒有全部权限,其余角色按 用户-角色-权限 关联表查询。
 *
 * 性能:currentUser() 与 permissionCodes() 走 Redis 短 TTL 缓存(5min),
 * 避免每个 @RequirePermission 接口都打 DB。失效见 invalidateUser / invalidatePerms。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityHelper {

    private static final Duration USER_TTL = Duration.ofMinutes(5);
    private static final Duration PERM_TTL = Duration.ofMinutes(5);
    private static final String KEY_USER = "ihomy:user:";
    private static final String KEY_PERM = "ihomy:perms:";

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

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

    /** 当前用户完整实体(优先 Redis 缓存,未登录返回 null) */
    public SysUser currentUser() {
        LoginUser u = current();
        if (u == null) return null;
        Long userId = u.getUserId();
        String key = KEY_USER + userId;
        try {
            String json = redis.opsForValue().get(key);
            if (json != null) {
                return mapper.readValue(json, SysUser.class);
            }
        } catch (Exception e) {
            log.warn("read user cache failed uid={}, fallback to DB", userId, e);
        }
        SysUser user = sysUserMapper.selectById(userId);
        if (user != null) {
            try {
                redis.opsForValue().set(key, mapper.writeValueAsString(user), USER_TTL);
            } catch (Exception e) {
                log.warn("write user cache failed uid={}", userId, e);
            }
        }
        return user;
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

    /** 查询该用户在当前家庭拥有的全部权限码(带 Redis 缓存) */
    private Set<String> permissionCodes(LoginUser u) {
        String key = KEY_PERM + u.getUserId() + ":" + u.getFamilyId();
        try {
            String json = redis.opsForValue().get(key);
            if (json != null) {
                return mapper.readValue(json, Set.class);
            }
        } catch (Exception e) {
            log.warn("read perm cache failed uid={}, fallback to DB", u.getUserId(), e);
        }
        List<String> list = sysRoleMapper.selectAuthCodesByUserAndFamily(u.getUserId(), u.getFamilyId());
        Set<String> codes = new HashSet<>(list == null ? List.of() : list);
        try {
            redis.opsForValue().set(key, mapper.writeValueAsString(codes), PERM_TTL);
        } catch (Exception e) {
            log.warn("write perm cache failed uid={}", u.getUserId(), e);
        }
        return codes;
    }

    /** 失效用户资料缓存(供 PUT /profile、admin 改资料等调用) */
    public void invalidateUser(Long userId) {
        if (userId != null) {
            redis.delete(KEY_USER + userId);
        }
    }

    /** 失效用户在某家庭的权限码缓存(供角色变更、加入/退出家庭等调用) */
    public void invalidatePerms(Long userId, Long familyId) {
        if (userId != null && familyId != null) {
            redis.delete(KEY_PERM + userId + ":" + familyId);
        }
    }
}
