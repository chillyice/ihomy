package com.ihomy.service;

import com.ihomy.common.BizException;
import com.ihomy.common.DictConst;
import com.ihomy.common.ResultCode;
import com.ihomy.dto.LoginDTO;
import com.ihomy.dto.RegisterDTO;
import com.ihomy.entity.Family;
import com.ihomy.entity.SysRole;
import com.ihomy.entity.SysUser;
import com.ihomy.entity.InvitationCode;
import com.ihomy.entity.SysUserRole;
import com.ihomy.mapper.InvitationCodeMapper;
import com.ihomy.mapper.FamilyMapper;
import com.ihomy.mapper.SysRoleMapper;
import com.ihomy.mapper.SysUserMapper;
import com.ihomy.mapper.SysUserRoleMapper;
import com.ihomy.security.JwtUtils;
import com.ihomy.security.LoginUser;
import com.ihomy.security.SecurityHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 认证业务:登录/注册/登出/刷新令牌,以及多家庭解析
 * (当前家庭 = Redis 会话切换 > 默认家庭 > 主家庭)。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final FamilyMapper familyMapper;
    private final JwtUtils jwtUtils;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redisTemplate;
    private final SecurityHelper securityHelper;
    private final InvitationCodeMapper invitationCodeMapper;
    private final CaptchaService captchaService;

    private static final String BLACKLIST_PREFIX = "jwt:blacklist:";
    private static final String CUR_FAMILY_PREFIX = "user:curfamily:";

    /** 生成 16 位混淆分享 token(UUID 去横线截取) */
    private String genShareToken() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /** 当前家庭解析优先级:Redis 会话切换 > 用户默认家庭 > 主家庭 */
    private Long resolveFamily(Long userId) {
        Long cur = curFamily(userId);
        if (cur != null) return cur;
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) return null;
        if (user.getDefaultFamilyId() != null) return user.getDefaultFamilyId();
        return user.getFamilyId();
    }

    /** 登录:验证码校验 + 邮箱密码校验,下发令牌并携带当前家庭 */
    public Map<String, Object> login(LoginDTO dto) {
        // 图形验证码校验(一次性,与注册同一套)
        if (!captchaService.verify(dto.getCaptchaId(), dto.getCaptchaCode())) {
            throw new BizException(ResultCode.CAPTCHA_ERROR);
        }
        // 登录账号 = 注册邮箱(大小写不敏感)
        SysUser user = sysUserMapper.selectByEmail(dto.getEmail().trim());
        if (user == null) {
            throw new BizException(ResultCode.USER_NOT_FOUND);
        }
        if (user.getIsFake() != null && user.getIsFake() == 1) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BizException(ResultCode.PASSWORD_ERROR);
        }
        if (DictConst.USER_DISABLED.equals(user.getStatus())) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        Long familyId = resolveFamily(user.getId());
        if (familyId == null) familyId = user.getFamilyId();
        String roleCode = sysRoleMapper.selectRoleCodeByUserAndFamily(user.getId(), familyId);
        if (roleCode == null) {
            roleCode = "GUEST";
        }
        return buildTokens(user, roleCode, familyId);
    }

    /** 注册:创建家庭(OWNER)或凭邀请码加入(MEMBER/预设角色),注册成功不自动登录 */
    @Transactional
    public Map<String, Object> register(RegisterDTO dto) {
        // 图形验证码校验(一次性,失败即删除)
        if (!captchaService.verify(dto.getCaptchaId(), dto.getCaptchaCode())) {
            throw new BizException(ResultCode.CAPTCHA_ERROR);
        }
        // 两次密码必须一致
        if (StringUtils.hasText(dto.getConfirmPassword())
                && !dto.getConfirmPassword().equals(dto.getPassword())) {
            throw new BizException(ResultCode.BAD_REQUEST);
        }
        // 邮箱唯一(邮箱即账号,重复注册拒绝)
        String email = dto.getEmail().trim().toLowerCase();
        if (sysUserMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getEmail, email)) > 0) {
            throw new BizException(ResultCode.EMAIL_EXISTS);
        }
        SysUser user = new SysUser();
        // 不再输入用户名/昵称:username 取邮箱(满足唯一约束),昵称默认邮箱前缀,可在个人设置修改
        user.setUsername(email);
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setNickname(email.contains("@") ? email.substring(0, email.indexOf('@')) : email);
        user.setEmail(email);
        user.setStatus(DictConst.USER_ACTIVE);

        if (StringUtils.hasText(dto.getInviteCode())) {
            InvitationCode ic = invitationCodeMapper.selectByCode(dto.getInviteCode().trim());
            if (ic == null || !DictConst.INVITE_UNUSED.equals(ic.getStatus())) throw new BizException(ResultCode.NOT_FOUND);
            if (ic.getExpiresAt() != null && ic.getExpiresAt().isBefore(java.time.LocalDateTime.now())) {
                throw new BizException(ResultCode.CONFLICT);
            }
            if (ic.getUsedCount() >= ic.getMaxUses()) throw new BizException(ResultCode.CONFLICT);
            user.setFamilyId(ic.getFamilyId());
            sysUserMapper.insert(user);

            SysUserRole ur = new SysUserRole();
            ur.setUserId(user.getId());
            ur.setRoleId(ic.getPresetRoleId());
            ur.setFamilyId(ic.getFamilyId());
            sysUserRoleMapper.insert(ur);

            ic.setUsedCount(ic.getUsedCount() + 1);
            invitationCodeMapper.updateById(ic);

            String roleCode = sysRoleMapper.selectRoleCodeByUserAndFamily(user.getId(), ic.getFamilyId());
            redisTemplate.opsForValue().set(CUR_FAMILY_PREFIX + user.getId(), String.valueOf(ic.getFamilyId()));
            return buildTokens(user, roleCode == null ? "MEMBER" : roleCode, ic.getFamilyId());
        }

        Family family = new Family();
        family.setName(StringUtils.hasText(dto.getFamilyName()) ? dto.getFamilyName() : "我的家庭");
        family.setCoverText("欢迎来到我们的家庭空间");
        family.setShareToken(genShareToken());
        familyMapper.insert(family);

        user.setFamilyId(family.getId());
        sysUserMapper.insert(user);

        family.setOwnerId(user.getId());
        familyMapper.updateById(family);

        SysRole ownerRole = sysRoleMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysRole>()
                        .eq(SysRole::getRoleCode, "OWNER"));
        if (ownerRole != null) {
            SysUserRole ur = new SysUserRole();
            ur.setUserId(user.getId());
            ur.setRoleId(ownerRole.getId());
            ur.setFamilyId(family.getId());
            sysUserRoleMapper.insert(ur);
        }

        redisTemplate.opsForValue().set(CUR_FAMILY_PREFIX + user.getId(), String.valueOf(family.getId()));
        return buildTokens(user, "OWNER", family.getId());
    }

    /** 登出:access token 加入 Redis 黑名单直至过期,refresh token 同步失效 */
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

    /** 刷新令牌:校验 refresh token 未过期/未拉黑后重签 access token */
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
        Long familyId = resolveFamily(user.getId());
        if (familyId == null) familyId = user.getFamilyId();
        String roleCode = sysRoleMapper.selectRoleCodeByUserAndFamily(user.getId(), familyId);
        if (roleCode == null) {
            roleCode = "GUEST";
        }
        return buildTokens(user, roleCode, familyId);
    }

    /** 我的家庭列表:来自角色绑定,标记主家庭/默认家庭/当前家庭 */
    public List<Map<String, Object>> listFamilies(Long userId) {
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) throw new BizException(ResultCode.USER_NOT_FOUND);

        Long current = resolveFamily(userId);
        java.util.Set<Long> seen = new java.util.HashSet<>();
        for (SysUserRole ur : sysUserRoleMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getUserId, userId))) {
            if (ur.getFamilyId() == null || !seen.add(ur.getFamilyId())) continue;
            Family f = familyMapper.selectById(ur.getFamilyId());
            if (f == null) continue;
            Map<String, Object> m = new HashMap<>();
            m.put("familyId", f.getId());
            m.put("name", f.getName());
            m.put("isDemo", f.getIsDemo());
            m.put("role", sysRoleMapper.selectRoleCodeByUserAndFamily(userId, f.getId()));
            m.put("isPrimary", user.getFamilyId() != null && user.getFamilyId().equals(f.getId()));
            m.put("isDefault", user.getDefaultFamilyId() != null && user.getDefaultFamilyId().equals(f.getId()));
            m.put("isCurrent", current != null && current.equals(f.getId()));
            result.add(m);
        }
        return result;
    }

    /** 切换当前家庭:校验目标家庭有角色绑定,setDefault=true 同时登记为默认家庭 */
    public Map<String, Object> switchFamily(Long userId, Long familyId, Boolean setDefault) {
        if (familyId == null) throw new BizException(ResultCode.BAD_REQUEST);
        String roleCode = sysRoleMapper.selectRoleCodeByUserAndFamily(userId, familyId);
        if (roleCode == null) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        redisTemplate.opsForValue().set(CUR_FAMILY_PREFIX + userId, String.valueOf(familyId));
        // setDefault=true 时登记为默认家庭,后续登录/刷新优先进入
        if (Boolean.TRUE.equals(setDefault)) {
            SysUser user = sysUserMapper.selectById(userId);
            if (user != null) {
                user.setDefaultFamilyId(familyId);
                sysUserMapper.updateById(user);
            }
        }
        SysUser user = sysUserMapper.selectById(userId);
        return buildTokens(user, roleCode, familyId);
    }

    /** 已登录用户凭邀请码加入家庭(不切换当前家庭) */
    @Transactional
    public void joinFamily(Long userId, String inviteCode) {
        if (!StringUtils.hasText(inviteCode)) throw new BizException(ResultCode.BAD_REQUEST);
        InvitationCode ic = invitationCodeMapper.selectByCode(inviteCode.trim());
        if (ic == null || !DictConst.INVITE_UNUSED.equals(ic.getStatus())) throw new BizException(ResultCode.NOT_FOUND);
        if (ic.getExpiresAt() != null && ic.getExpiresAt().isBefore(java.time.LocalDateTime.now())) {
            throw new BizException(ResultCode.CONFLICT);
        }
        if (ic.getUsedCount() >= ic.getMaxUses()) throw new BizException(ResultCode.CONFLICT);
        Long existing = sysUserRoleMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getUserId, userId).eq(SysUserRole::getFamilyId, ic.getFamilyId()));
        if (existing > 0) throw new BizException(ResultCode.CONFLICT);

        SysUserRole ur = new SysUserRole();
        ur.setUserId(userId);
        ur.setRoleId(ic.getPresetRoleId());
        ur.setFamilyId(ic.getFamilyId());
        sysUserRoleMapper.insert(ur);

        ic.setUsedCount(ic.getUsedCount() + 1);
        invitationCodeMapper.updateById(ic);
    }

    private Long curFamily(Long userId) {
        String v = redisTemplate.opsForValue().get(CUR_FAMILY_PREFIX + userId);
        if (v == null) return null;
        try {
            return Long.valueOf(v);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 当前登录用户 */
    public LoginUser currentUser() {
        return securityHelper.current();
    }

    /** 组装令牌响应:access/refresh token + 用户信息 + 当前家庭分享 token */
    private Map<String, Object> buildTokens(SysUser user, String roleCode, Long familyId) {
        String access = jwtUtils.generateAccessToken(user.getId(), user.getUsername(), roleCode, familyId);
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
        u.put("role", roleCode);
        u.put("familyId", familyId);
        data.put("user", u);
        // 附带当前家庭分享链接所需的混淆 token(注册/切换后前端可直接生成分享链接)
        if (familyId != null) {
            Family f = familyMapper.selectById(familyId);
            if (f != null && StringUtils.hasText(f.getShareToken())) {
                data.put("shareToken", f.getShareToken());
            }
        }
        return data;
    }
}
