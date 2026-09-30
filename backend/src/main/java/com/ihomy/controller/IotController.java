package com.ihomy.controller;

import com.ihomy.annotation.OperationLog;
import com.ihomy.annotation.RequirePermission;
import com.ihomy.common.Result;
import com.ihomy.dto.IotConfigDTO;
import com.ihomy.dto.IotControlDTO;
import com.ihomy.dto.IotDeviceDTO;
import com.ihomy.entity.SysUser;
import com.ihomy.security.SecurityHelper;
import com.ihomy.service.HomeAssistantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 智能家居中控接口(家庭级):接入配置(家长)/ 设备列表与分组 / 历史曲线 / 控制设备。
 * 设备与历史由后端定时同步 Home Assistant 得来,这里只读库;控制直调 HA 服务由 HA 执行。
 */
@Tag(name = "智能家居中控")
@RestController
@RequestMapping("/iot")
@RequiredArgsConstructor
public class IotController {

    private final HomeAssistantService homeAssistantService;
    private final SecurityHelper securityHelper;

    private Long familyId() {
        SysUser user = securityHelper.currentUser();
        return user == null ? null : user.getFamilyId();
    }

    // ==================== 接入配置(家长) ====================

    @Operation(summary = "读取智能家居接入配置(令牌只回是否已设置)")
    @RequirePermission("storage:manage")
    @GetMapping("/config")
    public Result<Map<String, Object>> config() {
        return Result.success(homeAssistantService.configView(familyId()));
    }

    @Operation(summary = "保存智能家居接入配置")
    @OperationLog(module = "IOT", operationType = "UPDATE", description = "保存智能家居接入配置", saveArgs = false)
    @RequirePermission("storage:manage")
    @PutMapping("/config")
    public Result<Map<String, Object>> saveConfig(@RequestBody IotConfigDTO dto) {
        return Result.success(homeAssistantService.saveConfig(familyId(), securityHelper.currentUserId(), dto));
    }

    @Operation(summary = "移除智能家居接入配置(同步来的设备与历史一并清掉)")
    @OperationLog(module = "IOT", operationType = "DELETE", description = "移除智能家居接入配置", saveArgs = false)
    @RequirePermission("storage:manage")
    @DeleteMapping("/config")
    public Result<Void> removeConfig() {
        homeAssistantService.removeConfig(familyId());
        return Result.success();
    }

    @Operation(summary = "测试智能家居连通性(未填的项沿用已保存配置)")
    @OperationLog(module = "IOT", operationType = "QUERY", description = "测试智能家居连通性", saveArgs = false)
    @RequirePermission("storage:manage")
    @PostMapping("/test")
    public Result<Map<String, Object>> test(@RequestBody(required = false) IotConfigDTO dto) {
        return Result.success(homeAssistantService.test(familyId(), dto == null ? new IotConfigDTO() : dto));
    }

    // ==================== 设备与历史 ====================

    @Operation(summary = "家庭智能设备列表(含隐藏项与所属房间)")
    @GetMapping("/devices")
    public Result<List<Map<String, Object>>> devices() {
        return Result.success(homeAssistantService.devices(familyId()));
    }

    @Operation(summary = "修改智能设备的显示名/所属房间/是否展示")
    @OperationLog(module = "IOT", operationType = "UPDATE", description = "修改智能设备信息")
    @PutMapping("/devices/{id}")
    public Result<Map<String, Object>> updateDevice(@PathVariable Long id, @RequestBody IotDeviceDTO dto) {
        return Result.success(homeAssistantService.updateDevice(familyId(), id, dto));
    }

    @Operation(summary = "设备历史曲线数据点")
    @GetMapping("/history")
    public Result<List<Map<String, Object>>> history(@RequestParam Long deviceId,
                                                     @RequestParam(required = false) Integer hours) {
        return Result.success(homeAssistantService.history(familyId(), deviceId, hours));
    }

    // ==================== 控制 ====================

    @Operation(summary = "控制智能设备(开关灯/调温等,由 HA 执行并回读状态)")
    @OperationLog(module = "IOT", operationType = "UPDATE", description = "控制智能设备")
    @RequirePermission("iot:control")
    @PostMapping("/control")
    public Result<Map<String, Object>> control(@RequestBody IotControlDTO dto) {
        return Result.success(homeAssistantService.control(familyId(), dto));
    }
}
