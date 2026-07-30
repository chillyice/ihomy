package com.ihomy.service.impl;

import com.ihomy.common.BizException;
import com.ihomy.common.ResultCode;
import com.ihomy.dto.LoginDTO;
import com.ihomy.dto.RegisterDTO;
import com.ihomy.entity.Family;
import com.ihomy.entity.SysUser;
import com.ihomy.mapper.FamilyMapper;
import com.ihomy.mapper.SysUserMapper;
import com.ihomy.security.JwtUtils;
import com.ihomy.security.LoginUser;
import com.ihomy.security.SecurityHelper;
import com.ihomy.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final SysUserMapper sysUserMapper;
    private final FamilyMapper familyMapper;
    private final JwtUtils jwtUtils;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redisTemplate;
    private final SecurityHelper securityHelper;

    private static final String BLACKLIST_PREFIX = "jwt:blacklist:";

    @Override
    public Map<String, Object> login(LoginDTO dto) {
        SysUser user = sysUserMapper.selectByUsername(dto.getUsername());
        if (user == null) {
            throw new BizException(ResultCode.USER_NOT_FOUND);
        }
        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BizException(ResultCode.PASSWORD_ERROR);
        }
        return buildTokens(user);
    }

    @Override
    @Transactional
    public Map<String, Object> register(RegisterDTO dto) {
        if (sysUserMapper.selectByUsername(dto.getUsername()) != null) {
            throw new BizException(ResultCode.USER_EXISTS);
        }
        Family family = new Family();
        family.setName(StringUtils.hasText(dto.getFamilyName()) ? dto.getFamilyName() : "我的家庭");
        family.setCoverText("欢迎来到我们的家庭空间");
        familyMapper.insert(family);

        SysUser user = new SysUser();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setNickname(dto.getNickname());
        user.setRole("OWNER");
        user.setFamilyId(family.getId());
        sysUserMapper.insert(user);

        family.setOwnerId(user.getId());
        familyMapper.updateById(family);

        return buildTokens(user);
    }

    @Override
    public void logout(String token) {
        if (StringUtils.hasText(token) && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        if (StringUtils.hasText(token) && jwtUtils.isValid(token)) {
            long ttl = jwtUtils.parse(token).getExpiration().getTime() - System.currentTimeMillis();
            if (ttl > 0) {
                redisTemplate.opsForValue().set(BLACKLIST_PREFIX + token, "1", ttl, TimeUnit.MILLISECONDS);
            }
        }
    }

    @Override
    public Map<String, Object> refresh(String refreshToken) {
        if (!jwtUtils.isValid(refreshToken)) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        var claims = jwtUtils.parse(refreshToken);
        if (!"REFRESH".equals(claims.get("type", String.class))) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        if (Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + refreshToken))) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        Long userId = Long.valueOf(claims.getSubject());
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ResultCode.USER_NOT_FOUND);
        }
        return buildTokens(user);
    }

    @Override
    public LoginUser currentUser() {
        return securityHelper.current();
    }

    private Map<String, Object> buildTokens(SysUser user) {
        String access = jwtUtils.generateAccessToken(user.getId(), user.getUsername());
        String refresh = jwtUtils.generateRefreshToken(user.getId(), user.getUsername());
        Map<String, Object> data = new HashMap<>();
        data.put("accessToken", access);
        data.put("refreshToken", refresh);
        data.put("expiresIn", jwtUtils.getAccessExpire());
        Map<String, Object> u = new HashMap<>();
        u.put("id", user.getId());
        u.put("username", user.getUsername());
        u.put("nickname", user.getNickname());
        u.put("avatar", user.getAvatar());
        u.put("role", user.getRole());
        u.put("familyId", user.getFamilyId());
        data.put("user", u);
        return data;
    }
}
