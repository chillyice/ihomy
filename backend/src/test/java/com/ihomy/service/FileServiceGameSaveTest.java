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
 * 小游戏存档目录:由游戏文件 URL 反推 games/&lt;游戏目录&gt;,存档按成员落在 saves/u{userId}/ 下,
 * 成员之间互不可见,删游戏连带清空所有人的存档。纯文件系统逻辑,不需要起 Spring。
 */
class FileServiceGameSaveTest {

    private static final long ALICE = 6L;
    private static final long BOB = 7L;

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
    void writesListsAndDeletesSaveInMemberDir() throws Exception {
        Path gameDir = tmp.resolve("games").resolve("game");
        Files.createDirectories(gameDir);

        Path saved = service.writeGameSave(gameDir, ALICE, "game.srm", saveFile("game.srm"));
        assertThat(saved).isEqualTo(gameDir.resolve("saves").resolve("u6").resolve("game.srm"));
        assertThat(Files.readAllBytes(saved)).containsExactly(1, 2, 3);

        // 非白名单扩展名、空名都不落盘
        assertThatThrownBy(() -> service.writeGameSave(gameDir, ALICE, "evil.exe", saveFile("evil.exe")))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.writeGameSave(gameDir, ALICE, "  ", saveFile("  ")))
                .isInstanceOf(BizException.class);

        // 带路径的名称只取文件名,不会跳出成员目录
        Path escaped = service.writeGameSave(gameDir, ALICE, "../../x.srm", saveFile("../../x.srm"));
        assertThat(escaped).isEqualTo(gameDir.resolve("saves").resolve("u6").resolve("x.srm"));

        List<Path> saves = service.listGameSaves(gameDir, ALICE);
        assertThat(saves).extracting(p -> p.getFileName().toString())
                .containsExactlyInAnyOrder("game.srm", "x.srm");

        assertThat(service.deleteGameSave(gameDir, ALICE, "game.srm")).isTrue();
        assertThat(service.deleteGameSave(gameDir, ALICE, "game.srm")).isFalse();
        assertThat(service.deleteGameSave(gameDir, ALICE, "evil.exe")).isFalse();
    }

    @Test
    void memberSavesAreIsolated() throws Exception {
        Path gameDir = tmp.resolve("games").resolve("game");
        Files.createDirectories(gameDir);

        service.writeGameSave(gameDir, ALICE, "game.srm", saveFile("game.srm"));
        assertThat(service.listGameSaves(gameDir, BOB)).isEmpty();
        assertThat(service.findGameSave(gameDir, BOB, "game.srm")).isNull();
        assertThat(service.findGameSave(gameDir, ALICE, "game.srm")).isNotNull();
        assertThat(service.deleteGameSave(gameDir, BOB, "game.srm")).isFalse();
        assertThat(service.findGameSave(gameDir, ALICE, "game.srm")).isNotNull();
    }

    @Test
    void migratesLegacySharedSaveToOwnerDirOnce() throws Exception {
        Path gameDir = tmp.resolve("games").resolve("game");
        Path legacyDir = gameDir.resolve("saves");
        Files.createDirectories(legacyDir);
        Files.write(legacyDir.resolve("game.srm"), new byte[]{4, 5, 6});

        service.migrateLegacyGameSaves(gameDir, ALICE);

        assertThat(Files.exists(legacyDir.resolve("game.srm"))).isFalse();
        Path owned = legacyDir.resolve("u6").resolve("game.srm");
        assertThat(Files.readAllBytes(owned)).containsExactly(4, 5, 6);

        // 再跑一次不重复搬、也不影响成员目录
        service.migrateLegacyGameSaves(gameDir, ALICE);
        assertThat(Files.readAllBytes(owned)).containsExactly(4, 5, 6);
    }

    @Test
    void deletingGameRemovesRomSavesAndFolder() throws Exception {
        Path gameDir = tmp.resolve("games").resolve("game");
        Files.createDirectories(gameDir);
        Path rom = gameDir.resolve("1729_game.gba");
        Files.write(rom, new byte[]{1});
        service.writeGameSave(gameDir, ALICE, "game.srm", saveFile("game.srm"));
        service.writeGameSave(gameDir, BOB, "game.srm", saveFile("game.srm"));

        service.deleteGameFiles("/files/games/game/1729_game.gba");

        assertThat(Files.exists(rom)).isFalse();
        assertThat(Files.exists(gameDir)).isFalse();
    }

    private static MockMultipartFile saveFile(String name) {
        return new MockMultipartFile("file", name, "application/octet-stream", new byte[]{1, 2, 3});
    }
}
