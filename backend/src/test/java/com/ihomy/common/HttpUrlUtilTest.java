package com.ihomy.common;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;

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

    @Test
    void privateAddressesRejectedWhenFetchingRemoteImage() throws Exception {
        String[] privates = {
                "10.0.0.1", "172.16.0.1", "192.168.1.1",       // RFC1918
                "127.0.0.1", "::1", "0.0.0.0",                 // 回环/任意地址
                "169.254.169.254", "169.254.1.1",              // 链路本地 + 云元数据
                "100.64.0.1", "100.100.100.200",               // 100.64.0.0/10 共享地址段
                "fc00::1", "fd00::1",                          // IPv6 ULA
                "ff02::1"                                      // IPv6 组播
        };
        for (String h : privates) {
            assertThat(HttpUrlUtil.isPrivateAddress(InetAddress.getByName(h))).as(h).isTrue();
        }
        // IPv4 映射 IPv6 的字节形态:Java 解析字面量会直接给 Inet4Address,这里手工构造走映射分支
        byte[] mapped = new byte[16];
        mapped[10] = (byte) 0xFF;
        mapped[11] = (byte) 0xFF;
        mapped[12] = (byte) 192;
        mapped[13] = (byte) 168;
        mapped[14] = 1;
        mapped[15] = 2;
        assertThat(HttpUrlUtil.isPrivateAddress(InetAddress.getByAddress(mapped))).isTrue();

        String[] publics = {"8.8.8.8", "1.1.1.1", "172.32.0.1", "104.16.0.1"};
        for (String h : publics) {
            assertThat(HttpUrlUtil.isPrivateAddress(InetAddress.getByName(h))).as(h).isFalse();
        }
    }
}
