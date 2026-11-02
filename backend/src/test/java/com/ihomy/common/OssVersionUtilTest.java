package com.ihomy.common;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 版本规则纯逻辑(不碰 Spring/DB):
 * 预发布识别决定「检测到的最新版能不能当升级项」,误判会让台账把 RC/-fix 当成正式版。
 */
class OssVersionUtilTest {

    @Test
    void prereleaseVersionsAreRecognized() {
        assertThat(OssVersionUtil.isPrerelease("4.2.0-RC1")).isTrue();
        assertThat(OssVersionUtil.isPrerelease("0.14.0-fix.3")).isTrue();
        assertThat(OssVersionUtil.isPrerelease("1.0.0-SNAPSHOT")).isTrue();
        assertThat(OssVersionUtil.isPrerelease("2.0.0-M1")).isTrue();
    }

    @Test
    void plainNumericVersionsAreStable() {
        assertThat(OssVersionUtil.isPrerelease("3.5.18.1")).isFalse();
        assertThat(OssVersionUtil.isPrerelease("v12.2")).isFalse();
        assertThat(OssVersionUtil.isPrerelease("5.8.48")).isFalse();
    }

    @Test
    void updateTypeClassifiesStableTargets() {
        assertThat(OssVersionUtil.updateType("4.1.1", "4.1.2")).isEqualTo(DictConst.OSS_UPDATE_PATCH);
        assertThat(OssVersionUtil.updateType("3.1.1", "3.2.0")).isEqualTo(DictConst.OSS_UPDATE_MINOR);
        assertThat(OssVersionUtil.updateType("3.5.17", "3.5.18")).isEqualTo(DictConst.OSS_UPDATE_PATCH);
    }

    @Test
    void prereleaseTargetIsNotAnUpgrade() {
        assertThat(OssVersionUtil.updateType("4.1.1", "4.2.0-RC1")).isEqualTo(DictConst.OSS_UPDATE_NONE);
        assertThat(OssVersionUtil.updateType("0.14.0", "0.14.0-fix.3")).isEqualTo(DictConst.OSS_UPDATE_NONE);
    }

    @Test
    void pickLatestStableSkipsPrereleases() {
        List<String> versions = List.of("4.1.1", "4.1.2", "4.2.0-M1", "4.2.0-RC1");

        assertThat(OssVersionUtil.pickLatestStable(versions)).isEqualTo("4.1.2");
        assertThat(OssVersionUtil.pickLatestStable(List.of("4.2.0-RC1"))).isNull();
        assertThat(OssVersionUtil.pickLatestStable(List.of())).isNull();
    }
}
