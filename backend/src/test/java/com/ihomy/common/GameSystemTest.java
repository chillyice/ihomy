package com.ihomy.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 模拟器机种映射:导入扩展名 → type、是否有电池存档、SWF/LINK 不算模拟器。
 * 前端 utils/games/emulatorSystems.js 与这里同口径,改一边必须同步另一边。
 */
class GameSystemTest {

    @Test
    void mapsRomExtensionsToSystemTypes() {
        assertThat(GameSystem.typeForName("口袋妖怪 绿宝石.gba")).isEqualTo(GameSystem.TYPE_GBA);
        assertThat(GameSystem.typeForName("Tetris.GB")).isEqualTo(GameSystem.TYPE_GB);
        assertThat(GameSystem.typeForName("tetris.gbc")).isEqualTo(GameSystem.TYPE_GB);
        assertThat(GameSystem.typeForName("mario.nes")).isEqualTo(GameSystem.TYPE_NES);
        assertThat(GameSystem.typeForName("zelda.sfc")).isEqualTo(GameSystem.TYPE_SNES);
        assertThat(GameSystem.typeForName("zelda.SMC")).isEqualTo(GameSystem.TYPE_SNES);
        assertThat(GameSystem.typeForName("sonic.md")).isEqualTo(GameSystem.TYPE_SEGA);
        assertThat(GameSystem.typeForName("sonic.gg")).isEqualTo(GameSystem.TYPE_SEGA);
        assertThat(GameSystem.typeForName("outrun.zip")).isEqualTo(GameSystem.TYPE_ARCADE);
        assertThat(GameSystem.typeForName("outrun.7z")).isEqualTo(GameSystem.TYPE_ARCADE);
        assertThat(GameSystem.typeForName("mario64.nds")).isEqualTo(GameSystem.TYPE_NDS);
        assertThat(GameSystem.typeForName("flash.swf")).isEqualTo(GameConst.TYPE_SWF);
    }

    @Test
    void rejectsUnknownOrAmbiguousFiles() {
        assertThat(GameSystem.typeForName("movie.mp4")).isNull();
        assertThat(GameSystem.typeForName("disc.iso")).isNull();
        assertThat(GameSystem.typeForName("disc.cue")).isNull();
        assertThat(GameSystem.typeForName("rom.bin")).isNull();
        assertThat(GameSystem.typeForName("gba")).isNull();
        assertThat(GameSystem.typeForName(null)).isNull();
    }

    @Test
    void onlyBatterySaveSystemsExposeSaves() {
        assertThat(GameSystem.hasSave(GameSystem.TYPE_GBA)).isTrue();
        assertThat(GameSystem.hasSave(GameSystem.TYPE_NDS)).isTrue();
        assertThat(GameSystem.hasSave(GameSystem.TYPE_SEGA)).isTrue();
        assertThat(GameSystem.hasSave(GameSystem.TYPE_ARCADE)).isFalse();
        assertThat(GameSystem.hasSave(GameConst.TYPE_SWF)).isFalse();
        assertThat(GameSystem.hasSave(GameConst.TYPE_LINK)).isFalse();
    }

    @Test
    void emulatorTypesExcludeSwfAndLink() {
        assertThat(GameSystem.isEmulator(GameSystem.TYPE_NES)).isTrue();
        assertThat(GameSystem.isEmulator(GameSystem.TYPE_ARCADE)).isTrue();
        assertThat(GameSystem.isEmulator(GameConst.TYPE_SWF)).isFalse();
        assertThat(GameSystem.isEmulator(GameConst.TYPE_LINK)).isFalse();
        assertThat(GameSystem.isEmulator(null)).isFalse();
    }

    @Test
    void importHintListsEveryAcceptedExtension() {
        assertThat(GameSystem.importHint())
                .contains(".gba", ".gb", ".gbc", ".nes", ".sfc", ".smc", ".md", ".gen", ".smd", ".sms", ".gg", ".zip", ".7z", ".nds");
    }

    @Test
    void desktopPackagesNeedExplicitType() {
        // 桌面游戏安装包必须由导入方显式指定类型:zip/7z 与街机 romset 同后缀,不能靠扩展名自动判定
        assertThat(GameSystem.isDesktopPackage("DemonStar Setup.exe")).isTrue();
        assertThat(GameSystem.isDesktopPackage("game.ZIP")).isTrue();
        assertThat(GameSystem.isDesktopPackage("game.msi")).isTrue();
        assertThat(GameSystem.isDesktopPackage("mario.nes")).isFalse();
        assertThat(GameSystem.isDesktopPackage(null)).isFalse();
        assertThat(GameSystem.typeForName("DemonStar Setup.exe")).isNull();
        assertThat(GameSystem.desktopHint()).contains(".exe", ".msi", ".zip", ".7z", ".rar");
        assertThat(GameSystem.hasSave(GameConst.TYPE_DESKTOP)).isFalse();
        assertThat(GameSystem.isEmulator(GameConst.TYPE_DESKTOP)).isFalse();
    }
}
