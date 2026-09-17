package com.ihomy.controller;

import com.ihomy.annotation.OperationLog;
import com.ihomy.common.Result;
import com.ihomy.dto.GameInfoDTO;
import com.ihomy.entity.GameInfo;
import com.ihomy.security.LoginUser;
import com.ihomy.security.SecurityHelper;
import com.ihomy.service.GameInfoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 小游戏接口:导入 .swf(存 games/{游戏名}/)、列表/详情/改名/改描述/删除。
 * 家庭隔离,登录即可(与植物/脑图一致)。
 */
@Tag(name = "小游戏")
@RestController
@RequestMapping("/game")
@RequiredArgsConstructor
public class GameInfoController {

    private final GameInfoService gameService;
    private final SecurityHelper securityHelper;

    private LoginUser current() {
        return securityHelper.current();
    }

    @Operation(summary = "游戏列表")
    @GetMapping
    public Result<List<GameInfo>> list() {
        return Result.success(gameService.list(current().getFamilyId()));
    }

    @Operation(summary = "游戏详情")
    @GetMapping("/{id}")
    public Result<GameInfo> get(@PathVariable Long id) {
        return Result.success(gameService.get(current().getFamilyId(), id));
    }

    @Operation(summary = "导入游戏")
    @OperationLog(module = "GAME", operationType = "CREATE", description = "导入游戏", saveArgs = false)
    @PostMapping("/import")
    public Result<GameInfo> importGame(@RequestParam("file") MultipartFile file,
                                       @RequestParam("name") String name,
                                       @RequestParam(value = "description", required = false) String description) {
        LoginUser user = current();
        return Result.success(gameService.importGame(user.getUserId(), user.getFamilyId(), file, name, description));
    }

    @Operation(summary = "更新游戏")
    @OperationLog(module = "GAME", operationType = "UPDATE", description = "编辑游戏")
    @PutMapping("/{id}")
    public Result<GameInfo> update(@PathVariable Long id, @RequestBody GameInfoDTO dto) {
        return Result.success(gameService.update(current().getFamilyId(), id, dto));
    }

    @Operation(summary = "删除游戏")
    @OperationLog(module = "GAME", operationType = "DELETE", description = "删除游戏")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        gameService.delete(current().getFamilyId(), id);
        return Result.success();
    }

    @Operation(summary = "H5 小游戏通关奖励")
    @OperationLog(module = "GAME", operationType = "UPDATE", description = "宠物连连看通关奖励")
    @PostMapping("/petlink/reward")
    public Result<Map<String, Object>> petLinkReward() {
        LoginUser user = current();
        return Result.success(gameService.petLinkReward(user.getUserId(), user.getFamilyId()));
    }
}
