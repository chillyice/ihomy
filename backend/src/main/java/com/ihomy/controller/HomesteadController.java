package com.ihomy.controller;

import com.ihomy.common.BizException;
import com.ihomy.common.Result;
import com.ihomy.common.ResultCode;
import com.ihomy.dto.HomesteadActionDTO;
import com.ihomy.dto.HomesteadBuildingDTO;
import com.ihomy.dto.HomesteadConsumeDTO;
import com.ihomy.dto.HomesteadGearDTO;
import com.ihomy.dto.HomesteadSellDTO;
import com.ihomy.dto.HomesteadToolDTO;
import com.ihomy.dto.HomesteadWearDTO;
import com.ihomy.security.LoginUser;
import com.ihomy.security.SecurityHelper;
import com.ihomy.service.HomesteadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 小院慢生活接口:读状态(顺带离线结算)、切动作、收工、卖物品、工具制造/升阶、
 * 装备制造/强化/穿戴、场地升级、吃料理、家庭榜。
 * 登录即可,无新权限码;写操作都是游戏内高频交互,与其它小游戏的奖励端点口径一致不记操作日志。
 * 写操作支持 Idempotency-Key 请求头(前端每次点击生成一个),重放不会二次入账。
 */
@Tag(name = "小院慢生活")
@RestController
@RequestMapping("/game/homestead")
@RequiredArgsConstructor
public class HomesteadController {

    private final HomesteadService homesteadService;
    private final SecurityHelper securityHelper;

    private LoginUser current() {
        LoginUser user = securityHelper.current();
        if (user == null || user.getUserId() == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        return user;
    }

    @Operation(summary = "读取存档(先结算离线收益)")
    @GetMapping("/state")
    public Result<Map<String, Object>> state() {
        LoginUser user = current();
        return Result.success(homesteadService.state(user.getUserId(), user.getFamilyId()));
    }

    @Operation(summary = "开始做某个动作")
    @PostMapping("/action")
    public Result<Map<String, Object>> action(@RequestBody @Valid HomesteadActionDTO dto,
                                              @RequestHeader(value = "Idempotency-Key", required = false) String opId) {
        LoginUser user = current();
        return Result.success(homesteadService.startAction(
                user.getUserId(), user.getFamilyId(), dto.getActionCode(), opId));
    }

    @Operation(summary = "收工回到空闲")
    @PostMapping("/stop")
    public Result<Map<String, Object>> stop(@RequestHeader(value = "Idempotency-Key", required = false) String opId) {
        LoginUser user = current();
        return Result.success(homesteadService.stop(user.getUserId(), user.getFamilyId(), opId));
    }

    @Operation(summary = "卖出背包物品")
    @PostMapping("/sell")
    public Result<Map<String, Object>> sell(@RequestBody @Valid HomesteadSellDTO dto,
                                            @RequestHeader(value = "Idempotency-Key", required = false) String opId) {
        LoginUser user = current();
        return Result.success(homesteadService.sell(
                user.getUserId(), user.getFamilyId(), dto.getItemCode(), dto.getQty(), opId));
    }

    @Operation(summary = "制造或升级工具")
    @PostMapping("/tool")
    public Result<Map<String, Object>> craftTool(@RequestBody @Valid HomesteadToolDTO dto,
                                                 @RequestHeader(value = "Idempotency-Key", required = false) String opId) {
        LoginUser user = current();
        return Result.success(homesteadService.craftTool(
                user.getUserId(), user.getFamilyId(), dto.getToolCode(), opId));
    }

    @Operation(summary = "制造装备")
    @PostMapping("/gear")
    public Result<Map<String, Object>> craftGear(@RequestBody @Valid HomesteadGearDTO dto,
                                                 @RequestHeader(value = "Idempotency-Key", required = false) String opId) {
        LoginUser user = current();
        return Result.success(homesteadService.craftGear(
                user.getUserId(), user.getFamilyId(), dto.getGearCode(), opId));
    }

    @Operation(summary = "强化装备")
    @PostMapping("/gear/enhance")
    public Result<Map<String, Object>> enhanceGear(@RequestBody @Valid HomesteadGearDTO dto,
                                                   @RequestHeader(value = "Idempotency-Key", required = false) String opId) {
        LoginUser user = current();
        return Result.success(homesteadService.enhanceGear(
                user.getUserId(), user.getFamilyId(), dto.getGearCode(), opId));
    }

    @Operation(summary = "穿上或脱下装备")
    @PostMapping("/wear")
    public Result<Map<String, Object>> wear(@RequestBody @Valid HomesteadWearDTO dto,
                                            @RequestHeader(value = "Idempotency-Key", required = false) String opId) {
        LoginUser user = current();
        return Result.success(homesteadService.wear(
                user.getUserId(), user.getFamilyId(), dto.getSlotCode(), dto.getGearCode(), opId));
    }

    @Operation(summary = "建造或升级场地")
    @PostMapping("/building")
    public Result<Map<String, Object>> upgradeBuilding(@RequestBody @Valid HomesteadBuildingDTO dto,
                                                       @RequestHeader(value = "Idempotency-Key", required = false) String opId) {
        LoginUser user = current();
        return Result.success(homesteadService.upgradeBuilding(
                user.getUserId(), user.getFamilyId(), dto.getBuildingCode(), opId));
    }

    @Operation(summary = "吃料理换限时加成")
    @PostMapping("/consume")
    public Result<Map<String, Object>> consume(@RequestBody @Valid HomesteadConsumeDTO dto,
                                               @RequestHeader(value = "Idempotency-Key", required = false) String opId) {
        LoginUser user = current();
        return Result.success(homesteadService.consume(
                user.getUserId(), user.getFamilyId(), dto.getItemCode(), dto.qtyOrOne(), opId));
    }

    @Operation(summary = "家庭小院指数榜")
    @GetMapping("/ranking")
    public Result<Map<String, Object>> ranking() {
        LoginUser user = current();
        return Result.success(homesteadService.ranking(user.getFamilyId(), user.getUserId()));
    }
}
