package com.ihomy.service;

import com.ihomy.common.BizException;
import com.ihomy.common.ResultCode;
import com.ihomy.entity.SysUser;
import com.ihomy.entity.VaultItem;
import com.ihomy.mapper.SysUserMapper;
import com.ihomy.mapper.VaultItemMapper;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 保险箱查看密码的二次校验:必须先匹配当前账号的登录密码,失败不触碰密文与解密器。
 * 纯逻辑单测(Mockito 桩),不起 Spring/DB/Redis。
 */
class VaultServiceTest {

    private final VaultItemMapper itemMapper = mock(VaultItemMapper.class);
    private final SysUserMapper userMapper = mock(SysUserMapper.class);
    private final ParameterService parameterService = mock(ParameterService.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final AuthGuardService authGuard = mock(AuthGuardService.class);

    private final VaultService service =
            new VaultService(itemMapper, userMapper, parameterService, passwordEncoder, authGuard);

    private SysUser user(String encodedPassword) {
        SysUser user = new SysUser();
        user.setId(1L);
        user.setPassword(encodedPassword);
        return user;
    }

    @Test
    void wrongLoginPasswordIsRejectedBeforeTouchingVaultItem() {
        when(userMapper.selectById(1L)).thenReturn(user("hash"));
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        assertThatThrownBy(() -> service.revealPassword(9L, 2L, 1L, "wrong", "127.0.0.1"))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.PASSWORD_ERROR.getCode()));

        verify(authGuard).recordVaultUnlockFail(1L, "127.0.0.1");
        verify(itemMapper, never()).selectById(any());
        verifyNoInteractions(parameterService);
    }

    @Test
    void correctLoginPasswordRevealsDecryptedPassword() {
        when(userMapper.selectById(1L)).thenReturn(user("hash"));
        when(passwordEncoder.matches("right", "hash")).thenReturn(true);

        VaultItem item = new VaultItem();
        item.setId(9L);
        item.setFamilyId(2L);
        item.setOwnerId(1L);
        item.setPasswordEnc("ENC(abc)");
        when(itemMapper.selectById(9L)).thenReturn(item);
        when(parameterService.decrypt("ENC(abc)")).thenReturn("plain-password");

        assertThat(service.revealPassword(9L, 2L, 1L, "right", "127.0.0.1")).isEqualTo("plain-password");
        verify(authGuard).clearVaultUnlockFail(1L, "127.0.0.1");
    }
}
