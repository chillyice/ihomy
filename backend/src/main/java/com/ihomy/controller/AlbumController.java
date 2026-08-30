package com.ihomy.controller;

import com.ihomy.annotation.OperationLog;
import com.ihomy.annotation.RequirePermission;
import com.ihomy.common.Result;
import com.ihomy.dto.AlbumDTO;
import com.ihomy.entity.Album;
import com.ihomy.entity.Family;
import com.ihomy.entity.SysUser;
import com.ihomy.mapper.FamilyMapper;
import com.ihomy.security.SecurityHelper;
import com.ihomy.service.AlbumMapService;
import com.ihomy.service.AlbumService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 相册接口:列表/详情/增删改;游客默认只看演示家庭的公开相册。
 */
@Tag(name = "相册")
@RestController
@RequestMapping("/album")
@RequiredArgsConstructor
public class AlbumController {

    private final AlbumService albumService;
    private final AlbumMapService albumMapService;
    private final SecurityHelper securityHelper;
    private final FamilyMapper familyMapper;
    private final com.ihomy.service.FileService fileService;

    @Operation(summary = "相册列表")
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list() {
        SysUser user = securityHelper.currentUser();
        boolean isGuest = user == null;
        Long familyId = user == null ? resolveDefaultFamily() : securityHelper.current().getFamilyId();
        return Result.success(albumService.list(familyId, isGuest));
    }

    private Long resolveDefaultFamily() {
        Family f = familyMapper.selectDefault();
        return f == null ? null : f.getId();
    }

    @Operation(summary = "相册详情(含照片)")
    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.success(albumService.detail(id, securityHelper.currentUser(),
                securityHelper.currentUser() == null ? null : securityHelper.current().getFamilyId()));
    }

    @Operation(summary = "分享令牌查看相册(游客可访问,相册 public 且家庭公开)")
    @GetMapping("/shared/{token}")
    public Result<Map<String, Object>> shared(@PathVariable String token) {
        return Result.success(albumService.shared(token));
    }

    @Operation(summary = "新建相册")
    @OperationLog(module = "ALBUM", operationType = "CREATE", description = "新建相册")
    @PostMapping
    public Result<Album> create(@RequestBody AlbumDTO dto) {
        return Result.success(albumService.create(securityHelper.currentUser(), securityHelper.current().getFamilyId(), dto));
    }

    @Operation(summary = "更新相册")
    @OperationLog(module = "ALBUM", operationType = "UPDATE", description = "修改相册")
    @PutMapping("/{id}")
    public Result<Album> update(@PathVariable Long id, @RequestBody AlbumDTO dto) {
        return Result.success(albumService.update(id, securityHelper.currentUser(), securityHelper.isOwner(), dto));
    }

    @Operation(summary = "删除相册")
    @OperationLog(module = "ALBUM", operationType = "DELETE", description = "删除相册")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        albumService.delete(id, securityHelper.currentUser(), securityHelper.isOwner());
        return Result.success();
    }

    @Operation(summary = "设置自定义封面(上传图片,优先于照片封面)")
    @OperationLog(module = "ALBUM", operationType = "UPDATE", description = "设置相册封面")
    @PostMapping("/{id}/cover")
    public Result<Void> setCover(@PathVariable Long id, MultipartFile file) {
        if (file == null || file.isEmpty()) throw new com.ihomy.common.BizException(com.ihomy.common.ResultCode.BAD_REQUEST, "请选择图片文件");
        String url = fileService.upload(file, file.getOriginalFilename(), file.getContentType());
        albumService.updateCover(id, securityHelper.currentUser(), securityHelper.isOwner(), url);
        return Result.success();
    }

    @Operation(summary = "清除自定义封面(回退为照片封面)")
    @OperationLog(module = "ALBUM", operationType = "UPDATE", description = "清除相册封面")
    @DeleteMapping("/{id}/cover")
    public Result<Void> clearCover(@PathVariable Long id) {
        albumService.updateCover(id, securityHelper.currentUser(), securityHelper.isOwner(), null);
        return Result.success();
    }

    @Operation(summary = "刷新映射相册(重新扫描设备目录,递归子树;仅设备映射相册)")
    @OperationLog(module = "ALBUM", operationType = "UPDATE", description = "刷新映射相册")
    @RequirePermission("storage:manage")
    @PostMapping("/{id}/refresh")
    public Result<Map<String, Long>> refresh(@PathVariable Long id) {
        com.ihomy.entity.Album a = albumService.getById(id);
        if (a == null) throw new com.ihomy.common.BizException(com.ihomy.common.ResultCode.NOT_FOUND);
        if (!a.getFamilyId().equals(securityHelper.current().getFamilyId())) {
            throw new com.ihomy.common.BizException(com.ihomy.common.ResultCode.FORBIDDEN);
        }
        return Result.success(Map.of("taskId", albumMapService.refreshAlbum(securityHelper.currentUser(), a)));
    }
}