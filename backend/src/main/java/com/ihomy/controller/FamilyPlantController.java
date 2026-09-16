package com.ihomy.controller;

import com.ihomy.annotation.OperationLog;
import com.ihomy.common.Result;
import com.ihomy.dto.PlantDTO;
import com.ihomy.dto.PlantStateVO;
import com.ihomy.security.LoginUser;
import com.ihomy.security.SecurityHelper;
import com.ihomy.service.FamilyPlantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 植物养殖接口:全家共养一棵,成员均可照料(浇水/晒太阳/施肥)、收获;
 * 家庭隔离,成长/营养实时计算,天气影响加成,积分奖励/消耗,操作写入成长日志并通知成员。
 */
@Tag(name = "植物养殖")
@RestController
@RequestMapping("/plant")
@RequiredArgsConstructor
public class FamilyPlantController {

    private final FamilyPlantService plantService;
    private final SecurityHelper securityHelper;

    private LoginUser current() {
        return securityHelper.current();
    }

    @Operation(summary = "当前植物状态")
    @GetMapping
    public Result<PlantStateVO> state() {
        return Result.success(plantService.state(current().getFamilyId()));
    }

    @Operation(summary = "种植")
    @OperationLog(module = "PLANT", operationType = "CREATE", description = "种植植物")
    @PostMapping
    public Result<PlantStateVO> plant(@RequestBody(required = false) PlantDTO dto) {
        LoginUser user = current();
        return Result.success(plantService.plant(user.getUserId(), user.getFamilyId(), dto));
    }

    @Operation(summary = "浇水")
    @OperationLog(module = "PLANT", operationType = "UPDATE", description = "浇水")
    @PostMapping("/water")
    public Result<PlantStateVO> water(@RequestBody(required = false) PlantDTO dto) {
        LoginUser user = current();
        return Result.success(plantService.water(user.getUserId(), user.getFamilyId(), dto));
    }

    @Operation(summary = "晒太阳")
    @OperationLog(module = "PLANT", operationType = "UPDATE", description = "晒太阳")
    @PostMapping("/sun")
    public Result<PlantStateVO> sun(@RequestBody(required = false) PlantDTO dto) {
        LoginUser user = current();
        return Result.success(plantService.sun(user.getUserId(), user.getFamilyId(), dto));
    }

    @Operation(summary = "施肥")
    @OperationLog(module = "PLANT", operationType = "UPDATE", description = "施肥")
    @PostMapping("/fertilize")
    public Result<PlantStateVO> fertilize(@RequestBody(required = false) PlantDTO dto) {
        LoginUser user = current();
        return Result.success(plantService.fertilize(user.getUserId(), user.getFamilyId(), dto));
    }

    @Operation(summary = "收获")
    @OperationLog(module = "PLANT", operationType = "UPDATE", description = "收获植物")
    @PostMapping("/harvest")
    public Result<PlantStateVO> harvest(@RequestBody(required = false) PlantDTO dto) {
        LoginUser user = current();
        return Result.success(plantService.harvest(user.getUserId(), user.getFamilyId(), dto));
    }
}
