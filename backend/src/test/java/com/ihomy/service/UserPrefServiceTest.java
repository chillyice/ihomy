package com.ihomy.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.ihomy.common.BizException;
import com.ihomy.common.ResultCode;
import com.ihomy.entity.SysUserPref;
import com.ihomy.mapper.SysUserPrefMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 界面偏好:键格式、内容长度、键数上限三条护栏,以及「内容没变不写库」的幂等口径。
 */
class UserPrefServiceTest {

    private static final Long USER = 7L;

    private final SysUserPrefMapper mapper = mock(SysUserPrefMapper.class);
    private final UserPrefService service = new UserPrefService(mapper);

    /** 单测没有 MyBatis-Plus 启动扫描,手动注册实体信息,否则 LambdaWrapper 的列名解析会报错 */
    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), SysUserPref.class);
    }

    private static SysUserPref row(String key, String value) {
        SysUserPref pref = new SysUserPref();
        pref.setUserId(USER);
        pref.setPrefKey(key);
        pref.setPrefValue(value);
        return pref;
    }

    @Test
    void listMineMapsKeyToValue() {
        when(mapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(row("home.layout.v3", "[{\"id\":\"feed\"}]"), row("home.warm.v3", "[]")));

        assertThat(service.listMine(USER))
                .containsEntry("home.layout.v3", "[{\"id\":\"feed\"}]")
                .containsEntry("home.warm.v3", "[]")
                .hasSize(2);
    }

    @Test
    void listMineReturnsEmptyMapWhenNothingSaved() {
        when(mapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        assertThat(service.listMine(USER)).isEmpty();
    }

    @Test
    void saveUpsertsNewKey() {
        when(mapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        service.save(USER, "home.layout.v3", "[{\"id\":\"feed\"}]");

        verify(mapper).upsert(USER, "home.layout.v3", "[{\"id\":\"feed\"}]");
    }

    @Test
    void saveSkipsWriteWhenValueUnchanged() {
        when(mapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(row("home.layout.v3", "[]")));

        service.save(USER, "home.layout.v3", "[]");

        verify(mapper, never()).upsert(any(), any(), any());
    }

    @Test
    void saveTrimsKeyBeforeWriting() {
        when(mapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        service.save(USER, " home.layout.v3 ", "[]");

        verify(mapper).upsert(USER, "home.layout.v3", "[]");
    }

    @Test
    void badKeyRejected() {
        assertRejected("Home.Layout", "[]");
        assertRejected("home layout", "[]");
        assertRejected("home..layout", "[]");
        assertRejected("9home", "[]");
        assertRejected("a".repeat(65), "[]");
    }

    @Test
    void emptyOrBlankValueRejected() {
        assertRejected("home.layout.v3", "");
        assertRejected("home.layout.v3", "   ");
        assertRejected("home.layout.v3", null);
    }

    @Test
    void oversizedValueRejected() {
        assertThatThrownBy(() -> service.save(USER, "home.layout.v3", "x".repeat(16385)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("偏好内容过大")
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo(ResultCode.BAD_REQUEST.getCode()));
        verify(mapper, never()).upsert(any(), any(), any());
    }

    @Test
    void maxLengthValueAccepted() {
        when(mapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        assertThatCode(() -> service.save(USER, "home.layout.v3", "x".repeat(16384))).doesNotThrowAnyException();
    }

    @Test
    void keyCountLimitStopsNewKeysButNotOverwrites() {
        List<SysUserPref> full = new ArrayList<>();
        for (int i = 0; i < 20; i++) full.add(row("pref.p" + i, "[]"));
        when(mapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(full);

        // 新键被拦下
        assertThatThrownBy(() -> service.save(USER, "home.layout.v3", "[]"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("偏好数量已达上限");
        verify(mapper, never()).upsert(any(), any(), any());

        // 已有键的覆盖写不受条数限制
        assertThatCode(() -> service.save(USER, "pref.p3", "[{\"id\":\"feed\"}]")).doesNotThrowAnyException();
        verify(mapper).upsert(eq(USER), eq("pref.p3"), eq("[{\"id\":\"feed\"}]"));
    }

    private void assertRejected(String key, String value) {
        assertThatThrownBy(() -> service.save(USER, key, value))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo(ResultCode.BAD_REQUEST.getCode()));
    }
}
