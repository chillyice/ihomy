package com.ihomy.common;

import com.ihomy.common.LogAlertFingerprint.Event;
import com.ihomy.common.LogAlertFingerprint.Fingerprint;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 日志同类问题归一化的纯逻辑单测(不依赖 Spring/DB):
 * 核心诉求——「同一个原因反复刷屏」要归成一条,「不同原因」不能被糊在一起。
 * 场景取自生产实际:OSS 组件探测对 Maven Central 连续读超时(49 条不同 URL 的同一异常)。
 */
class LogAlertFingerprintTest {

    private static Event timeout(String url) {
        return new Event("ERROR", "thirdparty.maven",
                "!!! GET " + url + " failed costMs=5731",
                "java.net.SocketTimeoutException", "Read timed out",
                "com.ihomy.common.ThirdPartyHttp#request", "abc123");
    }

    @Test
    void sameExceptionDifferentUrlGroupsTogether() {
        Fingerprint a = LogAlertFingerprint.of(timeout("https://search.maven.org/solrsearch/select?q=cn.hutool"));
        Fingerprint b = LogAlertFingerprint.of(timeout("https://search.maven.org/solrsearch/select?q=org.lombok"));

        // 不同组件的探测 URL 不同,但异常类型+出错位置一致 → 应算同一类问题
        assertThat(a.key()).isEqualTo(b.key());
        assertThat(a.title()).isEqualTo("SocketTimeoutException: Read timed out @ ThirdPartyHttp#request");
    }

    @Test
    void differentAppFrameIsDifferentProblem() {
        Fingerprint a = LogAlertFingerprint.of(timeout("https://x/y"));
        Fingerprint b = LogAlertFingerprint.of(new Event("ERROR", "thirdparty.maven",
                "!!! GET https://x/y failed costMs=1",
                "java.net.SocketTimeoutException", "Read timed out",
                "com.ihomy.service.WeatherService#resolveLocation", "t"));

        assertThat(a.key()).isNotEqualTo(b.key());
    }

    @Test
    void differentLoggerIsDifferentProblem() {
        Fingerprint maven = LogAlertFingerprint.of(timeout("https://x/y"));
        Fingerprint weather = LogAlertFingerprint.of(new Event("ERROR", "thirdparty.weather",
                "!!! GET https://x/y failed costMs=1",
                "java.net.SocketTimeoutException", "Read timed out",
                "com.ihomy.common.ThirdPartyHttp#request", "t"));

        assertThat(maven.key()).isNotEqualTo(weather.key());
    }

    @Test
    void exceptionFingerprintIgnoresLineNumberChanges() {
        // 栈帧不带行号:代码行号变动不应导致重新预警
        String key = LogAlertFingerprint.of(timeout("https://x/y")).key();
        assertThat(key).hasSize(64);
        assertThat(LogAlertFingerprint.of(timeout("https://x/y?other=1")).key()).isEqualTo(key);
    }

    @Test
    void messageWithoutExceptionNormalizesVolatileValues() {
        Fingerprint a = LogAlertFingerprint.of(new Event("ERROR", "com.ihomy.service.XService",
                "入库失败 id=1001 cost=23ms tid=590f7b47a6e84cb8", null, null, null, "t1"));
        Fingerprint b = LogAlertFingerprint.of(new Event("ERROR", "com.ihomy.service.XService",
                "入库失败 id=2002 cost=998ms tid=ff00aa11bb22cc33", null, null, null, "t2"));

        assertThat(a.key()).isEqualTo(b.key());
        assertThat(a.title()).contains("入库失败 id=# cost=#ms tid=#");
    }

    @Test
    void uuidIsNormalized() {
        assertThat(LogAlertFingerprint.normalize("token=3f2504e0-4f89-11d3-9a0c-0305e82c3301 end"))
                .isEqualTo("token=# end");
    }

    @Test
    void differentMessageIsDifferentProblem() {
        Fingerprint a = LogAlertFingerprint.of(new Event("ERROR", "L", "连接超时", null, null, null, null));
        Fingerprint b = LogAlertFingerprint.of(new Event("ERROR", "L", "磁盘写入失败", null, null, null, null));
        assertThat(a.key()).isNotEqualTo(b.key());
    }

    @Test
    void blankAndNullMessageFallBackToPlaceholder() {
        assertThat(LogAlertFingerprint.of(new Event("ERROR", "L", null, null, null, null, null)).title())
                .isEqualTo("(未识别日志)");
        assertThat(LogAlertFingerprint.of(new Event("ERROR", "L", "   \n  ", null, null, null, null)).title())
                .isEqualTo("(未识别日志)");
    }

    @Test
    void levelIsPartOfFingerprint() {
        Fingerprint e = LogAlertFingerprint.of(new Event("ERROR", "L", "同一句话", null, null, null, null));
        Fingerprint w = LogAlertFingerprint.of(new Event("WARN", "L", "同一句话", null, null, null, null));
        assertThat(e.key()).isNotEqualTo(w.key());
    }

    @Test
    void multiLineMessageUsesFirstLineOnly() {
        Fingerprint a = LogAlertFingerprint.of(new Event("ERROR", "L",
                "第一行\n第二行带 123\n第三行", null, null, null, null));
        Fingerprint b = LogAlertFingerprint.of(new Event("ERROR", "L",
                "第一行\n完全不同的后续行 456", null, null, null, null));
        assertThat(a.key()).isEqualTo(b.key());
        assertThat(a.title()).isEqualTo("第一行");
    }

    @Test
    void frameShortStripsPackageOnly() {
        assertThat(LogAlertFingerprint.frameShort("com.ihomy.common.ThirdPartyHttp#request"))
                .isEqualTo("ThirdPartyHttp#request");
        assertThat(LogAlertFingerprint.frameShort(null)).isEmpty();
    }
}
