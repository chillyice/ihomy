package com.ihomy.controller;

import com.ihomy.annotation.OperationLog;
import com.ihomy.common.Result;
import com.ihomy.dto.AnniversaryDTO;
import com.ihomy.entity.Anniversary;
import com.ihomy.entity.SysUser;
import com.ihomy.security.SecurityHelper;
import com.ihomy.service.AnniversaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 家庭纪念日接口:列表(访客可读)与增删改(需登录)。
 * 一条纪念日可关联多位家庭成员(成员账号列表),不关联即为家庭级纪念日。
 */
@Tag(name = "家庭纪念日")
@RestController
@RequestMapping("/anniversary")
@RequiredArgsConstructor
public class AnniversaryController {

    private final AnniversaryService anniversaryService;
    private final SecurityHelper securityHelper;
    private final PublicController publicController;

    @Operation(summary = "纪念日列表(支持 ?hid= / ?home_id= 指定公开家庭)")
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list(@RequestParam(name = "hid", required = false) String hid,
                                                 @RequestParam(name = "home_id", required = false) Long homeId) {
        // 访客(未登录)按公开家庭取数 —— 与公告同一入口(publicController.resolveVisibleFamilyId)。
        // 此前游客分支 familyId=null 直接返回空列表,于是首页纪念日卡片有内容、纪念日页却「暂无纪念日」。
        Long familyId = publicController.resolveVisibleFamilyId(hid, homeId);
        return Result.success(anniversaryService.list(familyId));
    }

    @Operation(summary = "新增纪念日")
    @OperationLog(module = "ANNIVERSARY", operationType = "CREATE", description = "新增纪念日")
    @PostMapping
    public Result<Anniversary> create(@RequestBody AnniversaryDTO dto) {
        SysUser user = securityHelper.currentUser();
        Anniversary created = anniversaryService.create(user.getId(), user.getFamilyId(), dto);
        publicController.invalidateHomeCache(user.getFamilyId());
        return Result.success(created);
    }

    @Operation(summary = "更新纪念日")
    @OperationLog(module = "ANNIVERSARY", operationType = "UPDATE", description = "修改纪念日")
    @PutMapping("/{id}")
    public Result<Anniversary> update(@PathVariable Long id, @RequestBody AnniversaryDTO dto) {
        SysUser user = securityHelper.currentUser();
        Anniversary updated = anniversaryService.update(id, user.getFamilyId(), dto);
        publicController.invalidateHomeCache(user.getFamilyId());
        return Result.success(updated);
    }

    @Operation(summary = "删除纪念日")
    @OperationLog(module = "ANNIVERSARY", operationType = "DELETE", description = "删除纪念日")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        SysUser user = securityHelper.currentUser();
        anniversaryService.delete(id, user.getFamilyId());
        publicController.invalidateHomeCache(user.getFamilyId());
        return Result.success();
    }
}
