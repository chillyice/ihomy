package com.ihomy.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 外部网页游戏地址(LINK):scheme 自动补全、http(s) 白名单、必须带主机名、长度上限。
 * 网址最终以新标签页打开,所有入口共用这一份校验,堵住 javascript:/data: 等危险 scheme。
 */
class GameLinkUrlTest {

    @Test
    void acceptsHttpAndHttpsUrls() {
        assertThat(GameLinkUrl.normalize("https://nonograms-katana.com/play/"))
                .isEqualTo("https://nonograms-katana.com/play/");
        assertThat(GameLinkUrl.normalize("http://192.168.1.8:5173/games")).isEqualTo("http://192.168.1.8:5173/games");
    }

    @Test
    void prependsHttpsWhenSchemeMissing() {
        assertThat(GameLinkUrl.normalize("nonograms-katana.com/play")).isEqualTo("https://nonograms-katana.com/play");
        assertThat(GameLinkUrl.normalize("  nonograms-katana.com/play  ")).isEqualTo("https://nonograms-katana.com/play");
    }

    @Test
    void rejectsDangerousSchemes() {
        assertThatThrownBy(() -> GameLinkUrl.normalize("javascript:alert(1)"))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> GameLinkUrl.normalize("data:text/html,<script>x</script>"))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> GameLinkUrl.normalize("file:///C:/Windows/win.ini"))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> GameLinkUrl.normalize("ftp://example.com/game"))
                .isInstanceOf(BizException.class);
    }

    @Test
    void rejectsMissingHostOrEmpty() {
        assertThatThrownBy(() -> GameLinkUrl.normalize(null)).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> GameLinkUrl.normalize("   ")).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> GameLinkUrl.normalize("https://")).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> GameLinkUrl.normalize("http://路径没有主机")).isInstanceOf(BizException.class);
    }

    @Test
    void rejectsOverlongUrls() {
        String longUrl = "https://example.com/" + "a".repeat(GameLinkUrl.MAX_LENGTH);
        assertThatThrownBy(() -> GameLinkUrl.normalize(longUrl)).isInstanceOf(BizException.class);
    }

    @Test
    void keepsPathAndQueryIntact() {
        assertThat(GameLinkUrl.normalize("https://example.com/play/?lang=zh#top"))
                .isEqualTo("https://example.com/play/?lang=zh#top");
    }
}
