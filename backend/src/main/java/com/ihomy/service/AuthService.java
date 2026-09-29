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
    private final AuthGuardService authGuard;
    private final BlogService blogService;

    private static final String BLACKLIST_PREFIX = "jwt:blacklist:";
    private static final String CUR_FAMILY_PREFIX = "user:curfamily:";
    /** 轮换宽限:同一 refresh token 在此窗口内被重复提交(多标签/多端并发)时返回同一份新令牌 */
    private static final long ROTATE_GRACE_MS = 60_000L;

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
    public Map<String, Object> login(LoginDTO dto, String ip) {
        // 防爆破:账号或来源 IP 失败过多直接拒绝(成功登录后清零)
        authGuard.checkLoginBlocked(ip, dto.getEmail());
        // 图形验证码校验(一次性,与注册同一套)
        if (!captchaService.verify(dto.getCaptchaId(), dto.getCaptchaCode())) {
            throw new BizException(ResultCode.CAPTCHA_ERROR);
        }
        // 登录账号 = 注册邮箱(大小写不敏感)
        SysUser user = sysUserMapper.selectByEmail(dto.getEmail().trim());
        if (user == null) {
            authGuard.recordLoginFail(ip, dto.getEmail());
            throw new BizException(ResultCode.USER_NOT_FOUND);
        }
        if (user.getIsFake() != null && user.getIsFake() == 1) {
            authGuard.recordLoginFail(ip, dto.getEmail());
            throw new BizException(ResultCode.FORBIDDEN);
        }
        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            authGuard.recordLoginFail(ip, dto.getEmail());
            throw new BizException(ResultCode.PASSWORD_ERROR);
        }
        if (DictConst.USER_DISABLED.equals(user.getStatus())) {
            authGuard.recordLoginFail(ip, dto.getEmail());
            throw new BizException(ResultCode.FORBIDDEN);
        }
        authGuard.clearLoginFail(ip, dto.getEmail());
        Long familyId = resolveFamily(user.getId());
        if (familyId == null) familyId = user.getFamilyId();
        String roleCode = sysRoleMapper.selectRoleCodeByUserAndFamily(user.getId(), familyId);
        if (roleCode == null) {
            roleCode = "GUEST";
        }
        if (Boolean.TRUE.equals(user.getMustChangePassword())) {
            // 首登强制改密:不签发长期刷新令牌——否则改密后旧刷新令牌仍能换出不受限的访问令牌,
            // 等于默认密码登录者可在家长改密后继续使用。改密成功时再补发完整令牌。
            return buildTokens(user, roleCode, familyId, null);
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
            if (ic == null || !DictConst.INVITE_UNUSED.equals(ic.getStatus())) throw new BizException(ResultCode.NOT_FOUND, "邀请码不存在或已失效");
            if (ic.getExpiresAt() != null && ic.getExpiresAt().isBefore(java.time.LocalDateTime.now())) {
                throw new BizException(ResultCode.CONFLICT, "邀请码已过期");
            }
            if (ic.getUsedCount() >= ic.getMaxUses()) throw new BizException(ResultCode.CONFLICT, "邀请码已使用完");
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
        // 注入初始博客分类(未分类/生活随笔等,幂等)
        blogService.seedDefaultCategories(family.getId());

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

    /**
     * 修改密码:校验原密码后更新哈希,并清除首登强制改密标记。
     * 返回重签的令牌——原令牌带 pwdChange 标记受限,改密后必须换成新令牌才能正常访问。
     */
    public Map<String, Object> changePassword(Long userId, String oldPassword, String newPassword) {
        if (!StringUtils.hasText(oldPassword)) {
            throw new BizException(ResultCode.BAD_REQUEST, "请输入原密码");
        }
        if (newPassword == null || newPassword.length() < 6 || newPassword.length() > 30) {
            throw new BizException(ResultCode.BAD_REQUEST, "新密码长度 6-30 位");
        }
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ResultCode.USER_NOT_FOUND);
        }
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new BizException(ResultCode.PASSWORD_ERROR);
        }
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new BizException(ResultCode.BAD_REQUEST, "新密码不能与原密码相同");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(0);
        sysUserMapper.updateById(user);
        securityHelper.invalidateUser(userId);
        return buildTokens(user, roleOf(user), familyOf(user));
    }

    /** 登出:access token 与本次会话的 refresh token 一并拉黑,彻底终止会话 */
    public void logout(String token, String refreshToken) {
        blacklist(stripBearer(token), "out");
        // 壁纸令牌(WALLPAPER)是桌面壁纸的独立长存凭据,刻意不随浏览器登出吊销
        if (StringUtils.hasText(refreshToken) && jwtUtils.isValid(refreshToken)
                && "REFRESH".equals(jwtUtils.parse(refreshToken).get("type", String.class))) {
            blacklist(refreshToken, "out");
        }
    }

    /** 签发壁纸令牌(供桌面壁纸首次接入,之后由 /auth/refresh 滑动续期) */
    public Map<String, String> wallpaperToken(SysUser user) {
        return Map.of("refreshToken", jwtUtils.generateWallpaperToken(user.getId(), user.getUsername()));
    }

    /**
     * 刷新令牌:校验 refresh token 未过期/未拉黑后重签 access token。
     * 普通刷新令牌每次使用后即拉黑(轮换,单次有效,被盗令牌无法在登出后继续续期);
     * 壁纸令牌(WALLPAPER)不轮换拉黑,允许浏览器会话与多个壁纸实例各持一份、各自滑动续期。
     */
    public Map<String, Object> refresh(String refreshToken) {
        if (!jwtUtils.isValid(refreshToken)) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        var claims = jwtUtils.parse(refreshToken);
        String type = claims.get("type", String.class);
        boolean wallpaper = "WALLPAPER".equals(type);
        if (!"REFRESH".equals(type) && !wallpaper) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        String black = redisTemplate.opsForValue().get(BLACKLIST_PREFIX + refreshToken);
        if (black != null) {
            // 轮换宽限:60s 内重复提交同一令牌(多标签/多端并发续期)返回首次轮换出的同一份新令牌,
            // 避免并发把某一端踢下线;登出标记 out 与超出宽限的轮换一律拒绝
            String reused = replayWithinGrace(black);
            if (reused == null) throw new BizException(ResultCode.UNAUTHORIZED);
            SysUser u = sysUserMapper.selectById(Long.valueOf(claims.getSubject()));
            if (u == null) throw new BizException(ResultCode.USER_NOT_FOUND);
            return buildTokens(u, roleOf(u), familyOf(u), reused);
        }
        Long userId = Long.valueOf(claims.getSubject());
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ResultCode.USER_NOT_FOUND);
        }
        Long familyId = familyOf(user);
        String roleCode = roleOf(user);
        if (wallpaper) {
            return buildTokens(user, roleCode, familyId,
                    jwtUtils.generateWallpaperToken(user.getId(), user.getUsername()));
        }
        Map<String, Object> out = buildTokens(user, roleCode, familyId);
        blacklist(refreshToken, "rot:" + System.currentTimeMillis() + ":" + out.get("refreshToken"));
        return out;
    }

    private String stripBearer(String token) {
        return token != null && token.startsWith("Bearer ") ? token.substring(7) : token;
    }

    /** 拉黑 token:value=out(登出,一律拒绝)/ rot:时刻:替换令牌(轮换宽限内可复得同一份新令牌) */
    private void blacklist(String token, String value) {
        if (!StringUtils.hasText(token) || !jwtUtils.isValid(token)) return;
        long ttl = jwtUtils.parse(token).getExpiration().getTime() - System.currentTimeMillis();
        if (ttl > 0) {
            redisTemplate.opsForValue().set(BLACKLIST_PREFIX + token, value, ttl, TimeUnit.MILLISECONDS);
        }
    }

    /** 轮换宽限判定:黑名单值形如 rot:时刻:替换令牌,宽限期内返回替换令牌,否则 null */
    private String replayWithinGrace(String black) {
        if (black == null || !black.startsWith("rot:")) return null;
        int i = black.indexOf(':', 4);
        if (i < 0) return null;
        try {
            if (System.currentTimeMillis() - Long.parseLong(black.substring(4, i)) > ROTATE_GRACE_MS) return null;
            return black.substring(i + 1);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 当前家庭解析(优先 Redis 会话切换/默认家庭,回落主家庭) */
    private Long familyOf(SysUser user) {
        Long familyId = resolveFamily(user.getId());
        return familyId != null ? familyId : user.getFamilyId();
    }

    private String roleOf(SysUser user) {
        String roleCode = sysRoleMapper.selectRoleCodeByUserAndFamily(user.getId(), familyOf(user));
        return roleCode == null ? "GUEST" : roleCode;
    }

    /** 我的家庭列表:来自角色绑定,标记主家庭/默认家庭/当前家庭 */
    public List<Map<String, Object>> listFamilies(Long userId) {
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) throw new BizException(ResultCode.USER_NOT_FOUND);

        Long current = resolveFamily(userId);
        java.util.Set<Long> seen = new java.util.HashSet<>();
        List<SysUserRole> roles = sysUserRoleMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getUserId, userId));
        // 批量取家庭与角色码,免逐家庭 2 次查询(N+1)
        java.util.Set<Long> familyIds = roles.stream().map(SysUserRole::getFamilyId)
                .filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        Map<Long, Family> familyMap = familyIds.isEmpty() ? Map.of()
                : familyMapper.selectBatchIds(familyIds).stream()
                        .collect(java.util.stream.Collectors.toMap(Family::getId, f -> f));
        Map<Long, String> roleMap = sysRoleMapper.selectRoleCodesByUser(userId).stream()
                .collect(java.util.stream.Collectors.toMap(
                        row -> ((Number) row.get("familyId")).longValue(),
                        row -> (String) row.get("roleCode"), (a, b) -> a));
        for (SysUserRole ur : roles) {
            if (ur.getFamilyId() == null || !seen.add(ur.getFamilyId())) continue;
            Family f = familyMap.get(ur.getFamilyId());
            if (f == null) continue;
            Map<String, Object> m = new HashMap<>();
            m.put("familyId", f.getId());
            m.put("name", f.getName());
            m.put("isDemo", f.getIsDemo());
            m.put("role", roleMap.get(f.getId()));
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
        // 切换家庭后新家庭权限码不同,主动失效(虽然 5min TTL 也会自然过期,但切换是显式语义)
        securityHelper.invalidatePerms(userId, familyId);
        securityHelper.invalidateUser(userId);
        return buildTokens(user, roleCode, familyId);
    }

    /** 已登录用户凭邀请码加入家庭(不切换当前家庭) */
    @Transactional
    public void joinFamily(Long userId, String inviteCode) {
        if (!StringUtils.hasText(inviteCode)) throw new BizException(ResultCode.BAD_REQUEST);
        InvitationCode ic = invitationCodeMapper.selectByCode(inviteCode.trim());
        if (ic == null || !DictConst.INVITE_UNUSED.equals(ic.getStatus())) throw new BizException(ResultCode.NOT_FOUND, "邀请码不存在或已失效");
        if (ic.getExpiresAt() != null && ic.getExpiresAt().isBefore(java.time.LocalDateTime.now())) {
            throw new BizException(ResultCode.CONFLICT, "邀请码已过期");
        }
        if (ic.getUsedCount() >= ic.getMaxUses()) throw new BizException(ResultCode.CONFLICT, "邀请码已使用完");
        Long existing = sysUserRoleMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getUserId, userId).eq(SysUserRole::getFamilyId, ic.getFamilyId()));
        if (existing > 0) throw new BizException(ResultCode.CONFLICT, "您已在该家庭中");

        SysUserRole ur = new SysUserRole();
        ur.setUserId(userId);
        ur.setRoleId(ic.getPresetRoleId());
        ur.setFamilyId(ic.getFamilyId());
        sysUserRoleMapper.insert(ur);

        ic.setUsedCount(ic.getUsedCount() + 1);
        invitationCodeMapper.updateById(ic);
        // 新加入家庭后权限码列表变化,主动失效
        securityHelper.invalidatePerms(userId, ic.getFamilyId());
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

    /** 组装令牌响应(普通会话):签发新的 refresh token */
    private Map<String, Object> buildTokens(SysUser user, String roleCode, Long familyId) {
        return buildTokens(user, roleCode, familyId, jwtUtils.generateRefreshToken(user.getId(), user.getUsername()));
    }

    /** 组装令牌响应:access token + 指定 refresh token(壁纸令牌/轮换复用)+ 用户信息 + 分享 token + 权限码 */
    private Map<String, Object> buildTokens(SysUser user, String roleCode, Long familyId, String refresh) {
        boolean mustChange = Boolean.TRUE.equals(user.getMustChangePassword());
        String access = jwtUtils.generateAccessToken(user.getId(), user.getUsername(), roleCode, familyId, mustChange);
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
        // 首登强制改密标记:前端登录后据此弹强制改密框(改密成功后消失)
        u.put("mustChangePassword", mustChange);
        // 权限码列表:当前家庭角色权限 + 系统级 OPS 权限(若有 OPS 绑定)
        java.util.List<String> perms = new java.util.ArrayList<>();
        if (familyId != null) {
            perms.addAll(sysRoleMapper.selectAuthCodesByUserAndFamily(user.getId(), familyId));
        }
        boolean hasOps = sysRoleMapper.countOpsRole(user.getId()) > 0;
        u.put("isOps", hasOps);
        if (hasOps) {
            // 家庭角色授权里可能已带 ops:view(占位家庭绑定),去重后再补
            if (!perms.contains("ops:view")) {
                perms.add("ops:view");
            }
        } else {
            // ops:view 是系统级权限,只随 OPS 角色绑定(family_id=NULL)生效(见 OpsAccessFilter);
            // 家庭角色授权里不该带它(历史种子给 OWNER 发过全量权限),否则前端按权限码判 OPS 会误判
            perms.remove("ops:view");
        }
        u.put("permissions", perms);
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
