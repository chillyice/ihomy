package com.ihomy.controller;

import com.ihomy.annotation.OperationLog;
import com.ihomy.common.Result;
import com.ihomy.dto.GameInfoDTO;
import com.ihomy.dto.GameLinkDTO;
import com.ihomy.dto.GameSaveDTO;
import com.ihomy.entity.GameInfo;
import com.ihomy.security.LoginUser;
import com.ihomy.security.SecurityHelper;
import com.ihomy.service.GameInfoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 小游戏接口:导入 .swf(存 games/{游戏名}/)、添加外部网页游戏(LINK)、列表/详情/改名/改描述/删除。
 * 家庭隔离,登录即可(与植物/脑图一致)。网页游戏的站点登录信息统一存家庭保险箱(/vault)。
 */
@Tag(name = "小游戏")
@RestController
@RequestMapping("/game")
@RequiredArgsConstructor
public class GameInfoController {

    private final GameInfoService gameService;
    private final SecurityHelper securityHelper;
    private final com.ihomy.service.access.ViewerScopeResolver viewerScopeResolver;

    private LoginUser current() {
        return securityHelper.current();
    }

    @Operation(summary = "游戏列表")
    @GetMapping
    public Result<List<GameInfo>> list() {
        return Result.success(gameService.list(viewerScopeResolver.resolve()));
    }

    @Operation(summary = "游戏详情")
    @GetMapping("/{id}")
    public Result<GameInfo> get(@PathVariable Long id) {
        return Result.success(gameService.get(viewerScopeResolver.resolve(), id));
    }

    @Operation(summary = "导入游戏")
    @OperationLog(module = "GAME", operationType = "CREATE", description = "导入游戏", saveArgs = false)
    @PostMapping("/import")
    public Result<GameInfo> importGame(@RequestParam("file") MultipartFile file,
                                       @RequestParam("name") String name,
                                       @RequestParam(value = "description", required = false) String description,
                                       @RequestParam(value = "category", required = false) String category,
                                       @RequestParam(value = "series", required = false) String series,
                                       @RequestParam(value = "visibility", required = false) String visibility) {
        LoginUser user = current();
        return Result.success(gameService.importGame(user.getUserId(), user.getFamilyId(), file, name, description,
                category, series, visibility));
    }

    @Operation(summary = "添加网页游戏")
    @OperationLog(module = "GAME", operationType = "CREATE", description = "添加网页游戏")
    @PostMapping("/link")
    public Result<GameInfo> importLink(@Valid @RequestBody GameLinkDTO dto) {
        LoginUser user = current();
        return Result.success(gameService.importLink(user.getUserId(), user.getFamilyId(), dto));
    }

    @Operation(summary = "更新游戏")
    @OperationLog(module = "GAME", operationType = "UPDATE", description = "编辑游戏")
    @PutMapping("/{id}")
    public Result<GameInfo> update(@PathVariable Long id, @Valid @RequestBody GameInfoDTO dto) {
        return Result.success(gameService.update(current().getFamilyId(), id, dto));
    }

    @Operation(summary = "自动归类游戏系列")
    @OperationLog(module = "GAME", operationType = "UPDATE", description = "自动归类游戏系列")
    @PostMapping("/auto-series")
    public Result<Map<String, Object>> autoSeries() {
        int updated = gameService.autoSeries(current().getFamilyId());
        return Result.success(Map.of("updated", updated));
    }

    @Operation(summary = "删除游戏")
    @OperationLog(module = "GAME", operationType = "DELETE", description = "删除游戏")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        gameService.delete(current().getFamilyId(), id);
        return Result.success();
    }

    @Operation(summary = "游戏存档列表")
    @GetMapping("/{id}/saves")
    public Result<List<GameSaveDTO>> saves(@PathVariable Long id) {
        LoginUser user = current();
        return Result.success(gameService.listSaves(user.getFamilyId(), user.getUserId(), id));
    }

    @Operation(summary = "下载游戏存档")
    @GetMapping("/{id}/saves/{name}")
    public ResponseEntity<Resource> downloadSave(@PathVariable Long id, @PathVariable String name) {
        LoginUser user = current();
        Path file = gameService.saveFile(user.getFamilyId(), user.getUserId(), id, name);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        String encoded = URLEncoder.encode(file.getFileName().toString(), StandardCharsets.UTF_8).replace("+", "%20");
        headers.set("Content-Disposition", "attachment; filename*=UTF-8''" + encoded);
        try {
            headers.setContentLength(Files.size(file));
        } catch (IOException ignored) {
            // 长度取不到时不带 Content-Length,交给分块传输
        }
        return new ResponseEntity<>(new PathResource(file), headers, HttpStatus.OK);
    }

    /** 自动存档每 30 秒一次,属高频端点,不记操作日志(避免日志被存档同步刷满) */
    @Operation(summary = "上传/覆盖游戏存档")
    @PostMapping("/{id}/saves")
    public Result<GameSaveDTO> uploadSave(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        LoginUser user = current();
        return Result.success(gameService.uploadSave(user.getFamilyId(), user.getUserId(), id, file));
    }

    @Operation(summary = "删除游戏存档")
    @OperationLog(module = "GAME", operationType = "DELETE", description = "删除游戏存档")
    @DeleteMapping("/{id}/saves/{name}")
    public Result<Void> deleteSave(@PathVariable Long id, @PathVariable String name) {
        LoginUser user = current();
        gameService.deleteSave(user.getFamilyId(), user.getUserId(), id, name);
        return Result.success();
    }

    @Operation(summary = "H5 小游戏通关奖励")
    @OperationLog(module = "GAME", operationType = "UPDATE", description = "宠物连连看通关奖励")
    @PostMapping("/petlink/reward")
    public Result<Map<String, Object>> petLinkReward() {
        LoginUser user = current();
        return Result.success(gameService.petLinkReward(user.getUserId(), user.getFamilyId()));
    }

    @Operation(summary = "H5 小游戏完成奖励")
    @OperationLog(module = "GAME", operationType = "UPDATE", description = "数独完成奖励")
    @PostMapping("/sudoku/reward")
    public Result<Map<String, Object>> sudokuReward() {
        LoginUser user = current();
        return Result.success(gameService.sudokuReward(user.getUserId(), user.getFamilyId()));
    }

    @Operation(summary = "H5 小游戏达成奖励")
    @OperationLog(module = "GAME", operationType = "UPDATE", description = "2048 达成奖励")
    @PostMapping("/2048/reward")
    public Result<Map<String, Object>> game2048Reward() {
        LoginUser user = current();
        return Result.success(gameService.game2048Reward(user.getUserId(), user.getFamilyId()));
    }

    @Operation(summary = "H5 小游戏通关奖励")
    @OperationLog(module = "GAME", operationType = "UPDATE", description = "扫雷通关奖励")
    @PostMapping("/minesweeper/reward")
    public Result<Map<String, Object>> minesweeperReward() {
        LoginUser user = current();
        return Result.success(gameService.minesweeperReward(user.getUserId(), user.getFamilyId()));
    }

    @Operation(summary = "H5 小游戏完成奖励")
    @OperationLog(module = "GAME", operationType = "UPDATE", description = "纸牌接龙完成奖励")
    @PostMapping("/solitaire/reward")
    public Result<Map<String, Object>> solitaireReward() {
        LoginUser user = current();
        return Result.success(gameService.solitaireReward(user.getUserId(), user.getFamilyId()));
    }

    @Operation(summary = "H5 小游戏完成奖励")
    @OperationLog(module = "GAME", operationType = "UPDATE", description = "蜘蛛纸牌完成奖励")
    @PostMapping("/spider/reward")
    public Result<Map<String, Object>> spiderReward() {
        LoginUser user = current();
        return Result.success(gameService.spiderReward(user.getUserId(), user.getFamilyId()));
    }

    @Operation(summary = "H5 小游戏完成奖励")
    @OperationLog(module = "GAME", operationType = "UPDATE", description = "自由纸牌完成奖励")
    @PostMapping("/freecell/reward")
    public Result<Map<String, Object>> freecellReward() {
        LoginUser user = current();
        return Result.success(gameService.freecellReward(user.getUserId(), user.getFamilyId()));
    }

    @Operation(summary = "H5 小游戏达标奖励")
    @OperationLog(module = "GAME", operationType = "UPDATE", description = "俄罗斯方块消行奖励")
    @PostMapping("/tetris/reward")
    public Result<Map<String, Object>> tetrisReward() {
        LoginUser user = current();
        return Result.success(gameService.tetrisReward(user.getUserId(), user.getFamilyId()));
    }

    @Operation(summary = "H5 小游戏达标奖励")
    @OperationLog(module = "GAME", operationType = "UPDATE", description = "贪吃蛇得分奖励")
    @PostMapping("/snake/reward")
    public Result<Map<String, Object>> snakeReward() {
        LoginUser user = current();
        return Result.success(gameService.snakeReward(user.getUserId(), user.getFamilyId()));
    }

    @Operation(summary = "H5 小游戏胡牌奖励")
    @OperationLog(module = "GAME", operationType = "UPDATE", description = "麻将胡牌奖励")
    @PostMapping("/mahjong/reward")
    public Result<Map<String, Object>> mahjongReward() {
        LoginUser user = current();
        return Result.success(gameService.mahjongReward(user.getUserId(), user.getFamilyId()));
    }
}
