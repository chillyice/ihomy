package com.ihomy.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GBA 存档文件名:消毒只留文件名、扩展名白名单、超长截断保留扩展名。
 * saves/ 目录既收模拟器上传的 .srm,也收手工放进去的文件,所有入口共用这一份口径。
 */
class GameSaveNameTest {

    @Test
    void keepsChineseAndStripsIllegalCharacters() {
        assertThat(GameSaveName.sanitize("口袋妖怪 绿宝石.srm")).isEqualTo("口袋妖怪_绿宝石.srm");
        assertThat(GameSaveName.sanitize("a:b*c?.srm")).isEqualTo("a_b_c_.srm");
    }

    @Test
    void onlyKeepsBaseName() {
        assertThat(GameSaveName.sanitize("../../etc/passwd")).isEqualTo("passwd");
        assertThat(GameSaveName.sanitize("..\\..\\save.srm")).isEqualTo("save.srm");
        assertThat(GameSaveName.sanitize("C:/games/x.srm")).isEqualTo("x.srm");
    }

    @Test
    void rejectsEmptyNames() {
        assertThat(GameSaveName.sanitize(null)).isNull();
        assertThat(GameSaveName.sanitize("   ")).isNull();
        assertThat(GameSaveName.sanitize("..")).isNull();
    }

    @Test
    void truncatesLongNamesButKeepsExtension() {
        String longName = "a".repeat(GameSaveName.MAX_LENGTH + 50) + ".srm";
        String sanitized = GameSaveName.sanitize(longName);
        assertThat(sanitized).endsWith(".srm");
        assertThat(sanitized.length()).isLessThanOrEqualTo(GameSaveName.MAX_LENGTH);
        assertThat(GameSaveName.allowed(sanitized)).isTrue();
    }

    @Test
    void extensionWhitelist() {
        assertThat(GameSaveName.allowed("game.srm")).isTrue();
        assertThat(GameSaveName.allowed("game.SAV")).isTrue();
        assertThat(GameSaveName.allowed("game.state")).isTrue();
        assertThat(GameSaveName.allowed("game.rtc")).isFalse();
        assertThat(GameSaveName.allowed("game.exe")).isFalse();
        assertThat(GameSaveName.allowed("srm")).isFalse();
        assertThat(GameSaveName.allowed(".srm")).isFalse();
        assertThat(GameSaveName.allowed("game.srm.exe")).isFalse();
        assertThat(GameSaveName.allowed(null)).isFalse();
    }
}
