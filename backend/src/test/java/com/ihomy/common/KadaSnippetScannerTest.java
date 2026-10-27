package com.ihomy.common;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 卡带机审:危险等级口径必须与咔哒客户端一致;法务关键词只标记不拒绝;
 * 规范化必须剥掉设置 / 目录等本机数据,未知顶层字段一律拒。
 */
class KadaSnippetScannerTest {

    @Test
    void canonicalizeStripsLocalDataAndUnknownIsRejected() {
        String raw = """
                {"settings":{"paused":true},"folders":[{"id":"local"}],
                 "shortcuts":[{"triggers":["F1"],"folder":"local","actions":[{"type":"text","text":"hi"}]}]}
                """;
        String clean = KadaSnippetScanner.canonicalize(raw);
        assertThat(clean)
                .doesNotContain("settings")
                .doesNotContain("folders")
                .doesNotContain("\"folder\"")
                .contains("shortcuts");

        assertThatThrownBy(() -> KadaSnippetScanner.canonicalize(
                "{\"shortcuts\":[],\"evil\":true}"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("未知顶层字段");
        assertThatThrownBy(() -> KadaSnippetScanner.canonicalize("{\"shortcuts\":[]}"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("没有可发布条目");
        assertThatThrownBy(() -> KadaSnippetScanner.canonicalize("not json"))
                .isInstanceOf(BizException.class);
    }

    @Test
    void dangerLevelsMatchClientRules() {
        assertThat(level("{\"expansions\":[{\"trigger\":\";a\",\"replace\":\"x\"}]}")).isZero();
        assertThat(level("{\"shortcuts\":[{\"triggers\":[\"F1\"],\"actions\":[{\"type\":\"keys\",\"keys\":[\"Ctrl+C\"]}]}]}"))
                .isEqualTo(1);
        assertThat(level("{\"shortcuts\":[{\"triggers\":[\"F1\"],\"actions\":[{\"type\":\"open_url\",\"url\":\"https://x\"}]}]}"))
                .isEqualTo(2);
        assertThat(level("{\"shortcuts\":[{\"triggers\":[\"F1\"],\"actions\":[{\"type\":\"command\",\"shell\":\"cmd\",\"command\":\"whoami\"}]}]}"))
                .isEqualTo(3);
        assertThat(level("{\"shortcuts\":[{\"triggers\":[\"F1\"],\"actions\":[{\"type\":\"future_action\"}]}]}"))
                .isEqualTo(3);
    }

    @Test
    void legalTermsAreFlaggedButDoNotRaiseLevel() {
        KadaSnippetScanner.Result scan = KadaSnippetScanner.analyze(
                "{\"expansions\":[{\"trigger\":\";x\",\"replace\":\"台独 傻逼\"}]}");
        assertThat(scan.level()).isZero();
        assertThat(scan.legalFlags()).hasSize(2);
        assertThat(scan.legalFlags().toString()).contains("分裂国家").contains("侮辱");
    }

    @Test
    void suspiciousCommandIsReported() {
        KadaSnippetScanner.Result scan = KadaSnippetScanner.analyze("""
                {"shortcuts":[{"triggers":["F1"],"actions":[
                  {"type":"command","shell":"powershell","command":"powershell -EncodedCommand AAAA"}]}]}
                """);
        assertThat(scan.level()).isEqualTo(3);
        assertThat(scan.findings().toString()).contains("命令混淆");
    }

    @Test
    void metaIsWrittenServerSideAndHashed() {
        String clean = KadaSnippetScanner.canonicalize(
                "{\"shortcuts\":[{\"triggers\":[\"F1\"],\"actions\":[{\"type\":\"text\",\"text\":\"x\"}]}]}");
        String canonical = KadaSnippetScanner.withMeta(clean, "备份", "说明", "小明", List.of("工具"));
        assertThat(canonical).contains("\"name\":\"备份\"").contains("\"author\":\"小明\"").contains("\"tags\":[\"工具\"]");
        assertThat(KadaSnippetScanner.sha256Hex(canonical)).hasSize(64);
    }

    @Test
    void oversizedContentIsRejectedBeforeParsing() {
        String huge = "{\"shortcuts\":[],\"pad\":\"" + "x".repeat(KadaSnippetScanner.MAX_BYTES) + "\"}";
        assertThatThrownBy(() -> KadaSnippetScanner.canonicalize(huge))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("太大");
    }

    private int level(String json) {
        return KadaSnippetScanner.analyze(KadaSnippetScanner.canonicalize(json)).level();
    }
}
