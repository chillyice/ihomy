package com.ihomy.service;

import com.ihomy.entity.Family;
import com.ihomy.entity.SysUser;
import com.ihomy.entity.SysUserRole;
import com.ihomy.mapper.FamilyMapper;
import com.ihomy.mapper.InvitationCodeMapper;
import com.ihomy.mapper.SysRoleMapper;
import com.ihomy.mapper.SysUserMapper;
import com.ihomy.mapper.SysUserRoleMapper;
import com.ihomy.security.JwtUtils;
import com.ihomy.security.SecurityHelper;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 多端家庭隔离:家庭切换只影响发起切换的那个设备。
 * 机制:刷新/壁纸令牌自带家庭快照,续期优先用令牌里的家庭,别的设备 switchFamily 写的
 * 全局「最近家庭」(Redis user:curfamily:{userId})只影响登录偏好,不再渗透进存量会话。
 */
class AuthServiceFamilyScopeTest {

    private static final String SECRET = "ihomy-secret-key-test-0123456789abcdef0123456789abcdef";
    private static final String BLACKLIST_PREFIX = "jwt:blacklist:";
    private static final String CUR_FAMILY_PREFIX = "user:curfamily:";

    private JwtUtils jwtUtils;
    private SysUserMapper sysUserMapper;
    private SysRoleMapper sysRoleMapper;
    private SysUserRoleMapper sysUserRoleMapper;
    private FamilyMapper familyMapper;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    private SecurityHelper securityHelper;
    private AuthService service;
    private BookPermissionService bookPermissionService;

    @BeforeEach
    void init() throws Exception {
        jwtUtils = new JwtUtils();
        set("secret", SECRET);
        set("accessExpire", 7200L);
        set("refreshExpire", 604800L);
        jwtUtils.init();
        sysUserMapper = mock(SysUserMapper.class);
        sysRoleMapper = mock(SysRoleMapper.class);
        sysUserRoleMapper = mock(SysUserRoleMapper.class);
        familyMapper = mock(FamilyMapper.class);
        redisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        securityHelper = mock(SecurityHelper.class);
        bookPermissionService = mock(BookPermissionService.class);
        lenient().when(bookPermissionService.levelOf(anyLong(), anyLong(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("WRITE");
        lenient().when(sysRoleMapper.countOpsRole(anyLong())).thenReturn(0);
        lenient().when(sysRoleMapper.selectAuthCodesByUserAndFamily(anyLong(), anyLong())).thenReturn(List.of());
        service = new AuthService(sysUserMapper, sysRoleMapper, sysUserRoleMapper, familyMapper, jwtUtils,
                mock(PasswordEncoder.class), redisTemplate, securityHelper,
                mock(InvitationCodeMapper.class), mock(CaptchaService.class), mock(AuthGuardService.class),
                mock(BlogService.class), bookPermissionService);
    }

    private void set(String name, Object value) throws Exception {
        Field f = JwtUtils.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(jwtUtils, value);
    }

    /** 用户 6:主家庭 1,仍保有家庭 2 的 MEMBER 角色;另一设备把全局「最近家庭」切到了 1 */
    private SysUser user() {
        SysUser u = new SysUser();
        u.setId(6L);
        u.setUsername("multi");
        u.setNickname("多端用户");
        u.setFamilyId(1L);
        return u;
    }

    private void stubMember(Long familyId, String role) {
        when(sysRoleMapper.selectRoleCodeByUserAndFamily(6L, familyId)).thenReturn(role);
    }

    @Test
    void refresh沿用令牌自带家庭_不跟随其他设备的切换() {
        // 设备 B 自己的刷新令牌是在家庭 2 签发的;设备 A 之后把全局最近家庭切成了 1
        String rt = jwtUtils.generateRefreshToken(6L, "multi", 2L);
        when(valueOps.get(BLACKLIST_PREFIX + rt)).thenReturn(null);
        when(sysUserMapper.selectById(6L)).thenReturn(user());
        stubMember(2L, "MEMBER");

        Map<String, Object> data = service.refresh(rt);

        assertThat(((Number) ((Map<?, ?>) data.get("user")).get("familyId")).longValue()).isEqualTo(2L);
        Claims access = jwtUtils.parse((String) data.get("accessToken"));
        assertThat(((Number) access.get("familyId")).longValue()).isEqualTo(2L);
        // 新刷新令牌的家庭快照也还是 2:设备 B 下轮续期继续留在家庭 2
        Claims next = jwtUtils.parse((String) data.get("refreshToken"));
        assertThat(((Number) next.get("familyId")).longValue()).isEqualTo(2L);
        // 全程没有把设备 B 带去全局最近家庭 1
        verify(sysRoleMapper).selectAuthCodesByUserAndFamily(6L, 2L);
    }

    @Test
    void 令牌家庭已不是成员时回落全局解析() {
        // 家庭 2 的角色被移除(退圈/被移出):快照失效,回落 Redis 最近家庭
        String rt = jwtUtils.generateRefreshToken(6L, "multi", 2L);
        when(valueOps.get(BLACKLIST_PREFIX + rt)).thenReturn(null);
        when(valueOps.get(CUR_FAMILY_PREFIX + 6L)).thenReturn("1");
        when(sysUserMapper.selectById(6L)).thenReturn(user());
        stubMember(2L, null);
        stubMember(1L, "MEMBER");

        Map<String, Object> data = service.refresh(rt);

        assertThat(((Number) ((Map<?, ?>) data.get("user")).get("familyId")).longValue()).isEqualTo(1L);
    }

    @Test
    void 轮换宽限重放仍沿用令牌家庭() {
        String rt = jwtUtils.generateRefreshToken(6L, "multi", 2L);
        String replacement = "rot:" + System.currentTimeMillis() + ":fake-replacement";
        // 黑名单里已有 rot 记录 = 该令牌刚刚被轮换过,宽限期内重复提交
        when(valueOps.get(BLACKLIST_PREFIX + rt)).thenReturn(replacement);
        when(sysUserMapper.selectById(6L)).thenReturn(user());
        stubMember(2L, "MEMBER");

        Map<String, Object> data = service.refresh(rt);

        // 宽限路径返回首次轮换出的同一份令牌,家庭仍是令牌快照的 2,不被全局最近家庭带跑
        assertThat(data.get("refreshToken")).isEqualTo("fake-replacement");
        assertThat(((Number) ((Map<?, ?>) data.get("user")).get("familyId")).longValue()).isEqualTo(2L);
    }

    @Test
    void switchFamily签发的刷新令牌携带目标家庭() {
        stubMember(2L, "OWNER");
        when(valueOps.get(org.mockito.ArgumentMatchers.anyString())).thenReturn(null);
        when(sysUserMapper.selectById(6L)).thenReturn(user());

        Map<String, Object> data = service.switchFamily(6L, 2L, false);

        assertThat(((Number) ((Map<?, ?>) data.get("user")).get("familyId")).longValue()).isEqualTo(2L);
        assertThat(((Number) jwtUtils.parse((String) data.get("refreshToken")).get("familyId")).longValue())
                .isEqualTo(2L);
        // 全局最近家庭照写(仅作登录偏好)
        verify(valueOps).set(CUR_FAMILY_PREFIX + 6L, "2");
        verify(securityHelper).invalidatePerms(6L, 2L);
        verify(securityHelper).invalidateUser(6L);
    }

    @Test
    void 家庭列表的isCurrent按会话标记() {
        SysUserRole r1 = new SysUserRole();
        r1.setUserId(6L);
        r1.setFamilyId(1L);
        SysUserRole r2 = new SysUserRole();
        r2.setUserId(6L);
        r2.setFamilyId(2L);
        when(sysUserRoleMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(r1, r2));
        Family f1 = new Family();
        f1.setId(1L);
        f1.setName("小窝");
        Family f2 = new Family();
        f2.setId(2L);
        f2.setName("养生协会");
        when(familyMapper.selectBatchIds(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(f1, f2));
        when(sysRoleMapper.selectRoleCodesByUser(6L)).thenReturn(List.of(
                Map.of("familyId", 1L, "roleCode", "OWNER"),
                Map.of("familyId", 2L, "roleCode", "MEMBER")));
        when(sysUserMapper.selectById(6L)).thenReturn(user());

        // 本会话在家庭 2:即使全局最近家庭写着 1,isCurrent 也标在 2 上(另一台设备不受影响)
        List<Map<String, Object>> list = service.listFamilies(6L, 2L);
        assertThat(list).extracting(m -> m.get("isCurrent")).containsExactly(false, true);
    }
}
