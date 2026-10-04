package com.ihomy.controller;

import com.ihomy.annotation.OperationLog;
import com.ihomy.common.Result;
import com.ihomy.entity.SysUser;
import com.ihomy.security.SecurityHelper;
import com.ihomy.service.RecycleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 回收站接口:照片/相册/视频/图书的逻辑删内容查看、恢复、彻底删除与清空(家庭内成员可用)。
 */
@Tag(name = "回收站")
@RestController
@RequestMapping("/recycle")
@RequiredArgsConstructor
public class RecycleController {

    private final RecycleService recycleService;
    private final SecurityHelper securityHelper;

    @Operation(summary = "回收站列表(type=photo/album/video/book)")
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list(@RequestParam String type) {
        return Result.success(recycleService.list(familyId(), type));
    }

    @Operation(summary = "从回收站恢复")
    @OperationLog(module = "RECYCLE", operationType = "UPDATE", description = "回收站恢复")
    @PostMapping("/{id}/restore")
    public Result<Void> restore(@PathVariable Long id, @RequestParam String type) {
        recycleService.restore(familyId(), type, id);
        return Result.success();
    }

    @Operation(summary = "彻底删除(物理删记录与磁盘文件)")
    @OperationLog(module = "RECYCLE", operationType = "DELETE", description = "回收站彻底删除")
    @DeleteMapping("/{id}")
    public Result<Void> purge(@PathVariable Long id, @RequestParam String type) {
        recycleService.purge(familyId(), type, id);
        return Result.success();
    }

    @Operation(summary = "清空某类回收站")
    @OperationLog(module = "RECYCLE", operationType = "DELETE", description = "清空回收站")
    @DeleteMapping("/empty")
    public Result<Map<String, Integer>> empty(@RequestParam String type) {
        return Result.success(Map.of("count", recycleService.empty(familyId(), type)));
    }

    private Long familyId() {
        SysUser user = securityHelper.currentUser();
        return user == null ? null : user.getFamilyId();
    }
}
