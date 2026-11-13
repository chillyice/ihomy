package com.ihomy.controller;

import com.ihomy.annotation.OperationLog;
import com.ihomy.annotation.RequirePermission;
import com.ihomy.common.Result;
import com.ihomy.dto.CircleAliasDTO;
import com.ihomy.dto.CircleDTO;
import com.ihomy.dto.CircleJoinDTO;
import com.ihomy.entity.SysUser;
import com.ihomy.security.SecurityHelper;
import com.ihomy.service.CircleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 亲友圈接口:建圈/邀请/加入/改称呼/移除家庭/退出/解散。
 * 圈只管「谁和谁能互相分享」,加入亲友圈不会获得读取圈内任何内容的权限。
 */
@Tag(name = "亲友圈")
@RestController
@RequestMapping("/circle")
@RequiredArgsConstructor
public class CircleController {

    private final CircleService circleService;
    private final SecurityHelper securityHelper;

    @Operation(summary = "新建亲友圈")
    @OperationLog(module = "CIRCLE", operationType = "CREATE", description = "新建亲友圈")
    @RequirePermission("family:manage")
    @PostMapping
    public Result<Map<String, Object>> create(@Valid @RequestBody CircleDTO dto) {
        return Result.success(circleService.create(securityHelper.currentUser(),
                securityHelper.current().getFamilyId(), dto));
    }

    @Operation(summary = "当前家庭所在的亲友圈")
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list() {
        return Result.success(circleService.list(securityHelper.current().getFamilyId()));
    }

    @Operation(summary = "生成邀请码(7 天有效)")
    @OperationLog(module = "CIRCLE", operationType = "UPDATE", description = "生成亲友圈邀请码")
    @RequirePermission("family:manage")
    @PostMapping("/{id}/invite")
    public Result<Map<String, Object>> invite(@PathVariable Long id) {
        return Result.success(circleService.invite(id, securityHelper.current().getFamilyId()));
    }

    @Operation(summary = "凭邀请码加入亲友圈")
    @OperationLog(module = "CIRCLE", operationType = "CREATE", description = "加入亲友圈")
    @RequirePermission("family:manage")
    @PostMapping("/join")
    public Result<Map<String, Object>> join(@Valid @RequestBody CircleJoinDTO dto) {
        SysUser user = securityHelper.currentUser();
        return Result.success(circleService.join(dto.getCode(), securityHelper.current().getFamilyId(), user));
    }

    @Operation(summary = "修改圈名/封面")
    @OperationLog(module = "CIRCLE", operationType = "UPDATE", description = "修改亲友圈")
    @RequirePermission("family:manage")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody CircleDTO dto) {
        circleService.update(id, securityHelper.current().getFamilyId(), dto);
        return Result.success();
    }

    @Operation(summary = "设置本家庭在这个圈里的称呼")
    @OperationLog(module = "CIRCLE", operationType = "UPDATE", description = "修改圈内称呼")
    @PutMapping("/{id}/alias")
    public Result<Void> alias(@PathVariable Long id, @Valid @RequestBody CircleAliasDTO dto) {
        circleService.setAlias(id, securityHelper.current().getFamilyId(), dto.normalized());
        return Result.success();
    }

    @Operation(summary = "移除圈内某个家庭")
    @OperationLog(module = "CIRCLE", operationType = "DELETE", description = "移除亲友圈家庭")
    @RequirePermission("family:manage")
    @DeleteMapping("/{id}/family/{familyId}")
    public Result<Void> removeFamily(@PathVariable Long id, @PathVariable Long familyId) {
        circleService.removeFamily(id, familyId, securityHelper.current().getFamilyId());
        return Result.success();
    }

    @Operation(summary = "本家庭退出亲友圈")
    @OperationLog(module = "CIRCLE", operationType = "DELETE", description = "退出亲友圈")
    @RequirePermission("family:manage")
    @PostMapping("/{id}/leave")
    public Result<Void> leave(@PathVariable Long id) {
        circleService.leave(id, securityHelper.current().getFamilyId());
        return Result.success();
    }

    @Operation(summary = "解散亲友圈(仅发起家庭)")
    @OperationLog(module = "CIRCLE", operationType = "DELETE", description = "解散亲友圈")
    @RequirePermission("family:manage")
    @DeleteMapping("/{id}")
    public Result<Void> disband(@PathVariable Long id) {
        circleService.disband(id, securityHelper.current().getFamilyId());
        return Result.success();
    }
}
