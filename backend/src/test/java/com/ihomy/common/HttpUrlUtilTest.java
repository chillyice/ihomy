package com.ihomy.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 地址规范化与 SSRF 目标限制:家庭内网(127.0.0.1/192.168.x)是刚需必须放行,
 * 云元数据/链路本地及其绕过写法必须拒绝。
 */
class HttpUrlUtilTest {

    @Test
    void addsSchemeAndStripsTrailingSlash() {
        assertThat(HttpUrlUtil.normalize("192.168.1.5:8096", "err")).isEqualTo("http://192.168.1.5:8096");
        assertThat(HttpUrlUtil.normalize("http://nas.home:8096/", "err")).isEqualTo("http://nas.home:8096");
    }

    @Test
    void familyAddressesAllowed() {
        assertThat(HttpUrlUtil.normalize("http://127.0.0.1:8096", "err")).isEqualTo("http://127.0.0.1:8096");
        assertThat(HttpUrlUtil.normalize("http://192.168.1.10:8123", "err")).isEqualTo("http://192.168.1.10:8123");
    }

    @Test
    void rejectsNonHttpScheme() {
        assertThatThrownBy(() -> HttpUrlUtil.normalize("ftp://nas", "err")).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> HttpUrlUtil.normalize("file:///etc/passwd", "err")).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> HttpUrlUtil.normalize("   ", "err")).isInstanceOf(BizException.class);
    }

    @Test
    void blocksMetadataAndBypassForms() {
        String[] hosts = {
                "169.254.169.254",            // 云元数据
                "[::ffff:169.254.169.254]",   // IPv4 映射 IPv6 绕前缀判断
                "2852039166",                 // 十进制整数写法
                "0xa9fea9fe",                 // 十六进制写法
                "metadata.google.internal",
                "0.0.0.0",
                "fe80::1"
        };
        for (String h : hosts) {
            assertThatThrownBy(() -> HttpUrlUtil.normalize("http://" + h, "err"))
                    .as(h)
                    .isInstanceOf(BizException.class);
        }
    }

    @Test
    void blockedHostUsesCallerMessage() {
        assertThatThrownBy(() -> HttpUrlUtil.normalize("http://169.254.169.254", "err", "blocked"))
                .isInstanceOf(BizException.class)
                .hasMessage("blocked");
    }
}
