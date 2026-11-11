package com.ihomy.controller;

import com.ihomy.annotation.OperationLog;
import com.ihomy.common.Result;
import com.ihomy.entity.FamilyFile;
import com.ihomy.entity.SysUser;
import com.ihomy.security.SecurityHelper;
import com.ihomy.service.FamilyFileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 家庭文件接口:成员把任意文件存进家庭列表,全家可见;删除限上传者本人或家长。
 * 与相册/图书馆/放映厅并列,不参与游客公开。
 */
@Tag(name = "家庭文件")
@RestController
@RequestMapping("/file/family")
@RequiredArgsConstructor
public class FamilyFileController {

    private final FamilyFileService familyFileService;
    private final SecurityHelper securityHelper;

    @Operation(summary = "家庭文件列表(按上传时间倒序,可选关键词)")
    @GetMapping("/list")
    public Result<List<FamilyFile>> list(@RequestParam(name = "keyword", required = false) String keyword) {
        SysUser user = securityHelper.currentUser();
        return Result.success(familyFileService.list(user.getFamilyId(), keyword));
    }

    @Operation(summary = "上传家庭文件")
    @OperationLog(module = "FAMILY_FILE", operationType = "CREATE", description = "上传家庭文件", saveArgs = false)
    @PostMapping("/upload")
    public Result<FamilyFile> upload(@RequestParam("file") MultipartFile file) throws IOException {
        return Result.success(familyFileService.upload(securityHelper.currentUser(), file));
    }

    @Operation(summary = "删除家庭文件(上传者或家长)")
    @OperationLog(module = "FAMILY_FILE", operationType = "DELETE", description = "删除家庭文件")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        familyFileService.delete(id, securityHelper.currentUser(), securityHelper.isOwner());
        return Result.success();
    }
}
