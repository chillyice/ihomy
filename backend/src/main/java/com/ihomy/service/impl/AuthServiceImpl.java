package com.ihomy.service.impl;

import com.ihomy.common.BizException;
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
import com.ihomy.service.AuthService;
import com.ihomy.service.CaptchaService;
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
 * 认证/注册/家庭管理实现:登录注册、JWT 签发与刷新、
 * 多家庭解析切换、邀请码加入家庭,并维护 Redis 当前家庭缓存。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

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

    @Override
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
        // 演示用假账号禁止登录
        if (user.getIsFake() != null && user.getIsFake() == 1) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BizException(ResultCode.PASSWORD_ERROR);
        }
        // 状态为 1 的账号已禁用
        if (user.getStatus() != null && user.getStatus() == 1) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        // 解析当前家庭,缺省回退主家庭,再查该家庭角色
        Long familyId = resolveFamily(user.getId());
        if (familyId == null) familyId = user.getFamilyId();
        String roleCode = sysRoleMapper.selectRoleCodeByUserAndFamily(user.getId(), familyId);
        if (roleCode == null) {
            roleCode = "GUEST";
        }
        return buildTokens(user, roleCode, familyId);
    }

    @Override
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
        user.setStatus(0);

        if (StringUtils.hasText(dto.getInviteCode())) {
            // 携带邀请码:校验有效性后直接加入受邀家庭,不新建家庭
            InvitationCode ic = invitationCodeMapper.selectByCode(dto.getInviteCode().trim());
            if (ic == null || ic.getStatus() != 1) throw new BizException(ResultCode.NOT_FOUND);
            if (ic.getExpiresAt() != null && ic.getExpiresAt().isBefore(java.time.LocalDateTime.now())) {
                throw new BizException(ResultCode.CONFLICT);
            }
            if (ic.getUsedCount() >= ic.getMaxUses()) throw new BizException(ResultCode.CONFLICT);
            user.setFamilyId(ic.getFamilyId());
            sysUserMapper.insert(user);

            // 按邀请码预设角色绑定到受邀家庭
            SysUserRole ur = new SysUserRole();
            ur.setUserId(user.getId());
            ur.setRoleId(ic.getPresetRoleId());
            ur.setFamilyId(ic.getFamilyId());
            sysUserRoleMapper.insert(ur);

            ic.setUsedCount(ic.getUsedCount() + 1);
            invitationCodeMapper.updateById(ic);

            // 记录当前家庭,便于后续直接定位
            String roleCode = sysRoleMapper.selectRoleCodeByUserAndFamily(user.getId(), ic.getFamilyId());
            redisTemplate.opsForValue().set(CUR_FAMILY_PREFIX + user.getId(), String.valueOf(ic.getFamilyId()));
            return buildTokens(user, roleCode == null ? "MEMBER" : roleCode, ic.getFamilyId());
        }

        // 无邀请码:自建家庭并绑定 OWNER
        Family family = new Family();
        family.setName(StringUtils.hasText(dto.getFamilyName()) ? dto.getFamilyName() : "我的家庭");
        family.setCoverText("欢迎来到我们的家庭空间");
        family.setShareToken(genShareToken());
        familyMapper.insert(family);

        user.setFamilyId(family.getId());
        sysUserMapper.insert(user);

        // 回填家庭 OWNER(家庭表创建后才有 id)
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

    @Override
    public void logout(String token) {
        if (StringUtils.hasText(token) && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        // 把 token 剩余有效期写入 Redis 黑名单,实现登出即失效
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
        // 仅接受 REFRESH 类型令牌,且不在黑名单中
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
        // 刷新时按当前解析的家庭重签 token(用户可能已切换家庭)
        Long familyId = resolveFamily(user.getId());
        if (familyId == null) familyId = user.getFamilyId();
        String roleCode = sysRoleMapper.selectRoleCodeByUserAndFamily(user.getId(), familyId);
        if (roleCode == null) {
            roleCode = "GUEST";
        }
        return buildTokens(user, roleCode, familyId);
    }

    @Override
    public List<Map<String, Object>> listFamilies(Long userId) {
        // 从用户-家庭角色绑定表聚合出全部家庭(含主家庭),并标注各标记位
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) throw new BizException(ResultCode.USER_NOT_FOUND);

        Long current = resolveFamily(userId);
        java.util.Set<Long> seen = new java.util.HashSet<>();
        for (SysUserRole ur : sysUserRoleMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getUserId, userId))) {
            // 去重:同一家庭多条绑定只取一次
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

    @Override
    public Map<String, Object> switchFamily(Long userId, Long familyId, Boolean setDefault) {
        if (familyId == null) throw new BizException(ResultCode.BAD_REQUEST);
        // 必须在该家庭有角色绑定才能切换
        String roleCode = sysRoleMapper.selectRoleCodeByUserAndFamily(userId, familyId);
        if (roleCode == null) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        // 写入 Redis 会话级当前家庭,后续接口按此取数
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
        // 重签 token,让新家庭快照随 JWT 下发
        return buildTokens(user, roleCode, familyId);
    }

    /** 已登录用户凭邀请码加入新家庭(与注册带码逻辑一致,但不自动切换) */
    @Override
    @Transactional
    public void joinFamily(Long userId, String inviteCode) {
        if (!StringUtils.hasText(inviteCode)) throw new BizException(ResultCode.BAD_REQUEST);
        InvitationCode ic = invitationCodeMapper.selectByCode(inviteCode.trim());
        if (ic == null || ic.getStatus() != 1) throw new BizException(ResultCode.NOT_FOUND);
        if (ic.getExpiresAt() != null && ic.getExpiresAt().isBefore(java.time.LocalDateTime.now())) {
            throw new BizException(ResultCode.CONFLICT);
        }
        if (ic.getUsedCount() >= ic.getMaxUses()) throw new BizException(ResultCode.CONFLICT);
        // 防重复加入同一家庭
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
        // 读取 Redis 中会话级当前家庭,无则返回 null
        String v = redisTemplate.opsForValue().get(CUR_FAMILY_PREFIX + userId);
        if (v == null) return null;
        try {
            return Long.valueOf(v);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public LoginUser currentUser() {
        return securityHelper.current();
    }

    private Map<String, Object> buildTokens(SysUser user, String roleCode, Long familyId) {
        // 组装 access/refresh token 与用户信息;附加当前家庭分享 token 供前端生成分享链接
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
