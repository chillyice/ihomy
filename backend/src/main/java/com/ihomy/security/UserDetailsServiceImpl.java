package com.ihomy.security;

import com.ihomy.entity.SysUser;
import com.ihomy.mapper.SysRoleMapper;
import com.ihomy.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * 认证用户加载服务:登录时按邮箱查用户并组装其主家庭角色,
 * 供 Spring Security 认证流程使用。
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // 登录账号即注册邮箱(username 参数实际传邮箱)
        SysUser user = sysUserMapper.selectByEmail(username);
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在");
        }
        String roleCode = sysRoleMapper.selectRoleCodeByUserAndFamily(user.getId(), user.getFamilyId());
        if (roleCode == null) {
            roleCode = "GUEST";
        }
        return new User(
                user.getUsername(),
                user.getPassword(),
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + roleCode))
        );
    }
}
