package com.ihomy.controller;

import com.ihomy.annotation.OperationLog;
import com.ihomy.common.BizException;
import com.ihomy.common.Result;
import com.ihomy.common.ResultCode;
import com.ihomy.dto.AnnouncementDTO;
import com.ihomy.entity.Announcement;
import com.ihomy.entity.SysUser;
import com.ihomy.security.SecurityHelper;
import com.ihomy.service.AnnouncementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 家庭公告/广告位接口:列表对成员与公开家庭访客可读,增删改仅家长。
 */
@Tag(name = "家庭公告")
@RestController
@RequestMapping("/announcement")
@RequiredArgsConstructor
public class AnnouncementController {

    private final AnnouncementService announcementService;
    private final SecurityHelper securityHelper;
    private final PublicController publicController;

    @Operation(summary = "公告列表(支持 ?hid= / ?home_id= 指定公开家庭)")
    @GetMapping("/list")
    public Result<List<Announcement>> list(@RequestParam(name = "hid", required = false) String hid,
                                           @RequestParam(name = "home_id", required = false) Long homeId) {
        Long familyId = publicController.resolveVisibleFamilyId(hid, homeId);
        return Result.success(announcementService.list(familyId, securityHelper.isOwner()));
    }

    @Operation(summary = "新增公告")
    @OperationLog(module = "ANNOUNCEMENT", operationType = "CREATE", description = "新增家庭公告")
    @PostMapping
    public Result<Announcement> create(@RequestBody AnnouncementDTO dto) {
        SysUser user = currentOwner();
        return Result.success(announcementService.create(user.getId(), user.getFamilyId(), dto));
    }

    @Operation(summary = "更新公告")
    @OperationLog(module = "ANNOUNCEMENT", operationType = "UPDATE", description = "修改家庭公告")
    @PutMapping("/{id}")
    public Result<Announcement> update(@PathVariable Long id, @RequestBody AnnouncementDTO dto) {
        SysUser user = currentOwner();
        return Result.success(announcementService.update(id, user.getFamilyId(), dto));
    }

    @Operation(summary = "删除公告")
    @OperationLog(module = "ANNOUNCEMENT", operationType = "DELETE", description = "删除家庭公告")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        SysUser user = currentOwner();
        announcementService.delete(id, user.getFamilyId());
        return Result.success();
    }

    private SysUser currentOwner() {
        if (!securityHelper.isOwner()) throw new BizException(ResultCode.FORBIDDEN);
        return securityHelper.currentUser();
    }
}
