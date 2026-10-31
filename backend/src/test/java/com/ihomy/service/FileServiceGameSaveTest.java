package com.ihomy.service;

import com.ihomy.common.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 小游戏存档目录:由游戏文件 URL 反推 games/&lt;游戏目录&gt;,存档落在该目录的 saves/ 下,删游戏连带清存档。
 * 纯文件系统逻辑,不需要起 Spring。
 */
class FileServiceGameSaveTest {

    @TempDir
    Path tmp;

    private FileService service;

    @BeforeEach
    void setUp() {
        service = new FileService();
        ReflectionTestUtils.setField(service, "uploadDir", tmp.toString());
        ReflectionTestUtils.setField(service, "urlPrefix", "/files");
    }

    @Test
    void derivesGameDirectoryFromFileUrl() {
        assertThat(service.gameDirectory("/files/games/口袋妖怪/1729_x.gba"))
                .isEqualTo(tmp.resolve("games").resolve("口袋妖怪"));
        assertThat(service.gameDirectory("https://cdn.example.com/x.gba")).isNull();
        assertThat(service.gameDirectory("/files/games/only.gba")).isNull();
        assertThat(service.gameDirectory("/files/videos/x.gba")).isNull();
        assertThat(service.gameDirectory(null)).isNull();
    }

    @Test
    void writesListsAndDeletesSaveInSavesDir() throws Exception {
        Path gameDir = tmp.resolve("games").resolve("game");
        Files.createDirectories(gameDir);

        Path saved = service.writeGameSave(gameDir, "game.srm", saveFile("game.srm"));
        assertThat(saved).isEqualTo(gameDir.resolve("saves").resolve("game.srm"));
        assertThat(Files.readAllBytes(saved)).containsExactly(1, 2, 3);

        // 非白名单扩展名、空名都不落盘
        assertThatThrownBy(() -> service.writeGameSave(gameDir, "evil.exe", saveFile("evil.exe")))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.writeGameSave(gameDir, "  ", saveFile("  ")))
                .isInstanceOf(BizException.class);

        // 带路径的名称只取文件名,不会跳出 saves/
        Path escaped = service.writeGameSave(gameDir, "../../x.srm", saveFile("../../x.srm"));
        assertThat(escaped).isEqualTo(gameDir.resolve("saves").resolve("x.srm"));

        List<Path> saves = service.listGameSaves(gameDir);
        assertThat(saves).extracting(p -> p.getFileName().toString())
                .containsExactlyInAnyOrder("game.srm", "x.srm");

        assertThat(service.deleteGameSave(gameDir, "game.srm")).isTrue();
        assertThat(service.deleteGameSave(gameDir, "game.srm")).isFalse();
        assertThat(service.deleteGameSave(gameDir, "evil.exe")).isFalse();
    }

    @Test
    void deletingGameRemovesRomSavesAndFolder() throws Exception {
        Path gameDir = tmp.resolve("games").resolve("game");
        Files.createDirectories(gameDir);
        Path rom = gameDir.resolve("1729_game.gba");
        Files.write(rom, new byte[]{1});
        service.writeGameSave(gameDir, "game.srm", saveFile("game.srm"));

        service.deleteGameFiles("/files/games/game/1729_game.gba");

        assertThat(Files.exists(rom)).isFalse();
        assertThat(Files.exists(gameDir)).isFalse();
    }

    private static MockMultipartFile saveFile(String name) {
        return new MockMultipartFile("file", name, "application/octet-stream", new byte[]{1, 2, 3});
    }
}
