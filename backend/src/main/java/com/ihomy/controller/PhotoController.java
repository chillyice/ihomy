package com.ihomy.controller;

import com.ihomy.annotation.OperationLog;
import com.ihomy.common.Result;
import com.ihomy.dto.PhotoDTO;
import com.ihomy.entity.Photo;
import com.ihomy.entity.SysUser;
import com.ihomy.security.SecurityHelper;
import com.ihomy.service.AlbumService;
import com.ihomy.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 照片接口:批量上传(走 FileService 落盘)/改备注/删除。
 */
@Tag(name = "照片")
@RestController
@RequestMapping
@RequiredArgsConstructor
public class PhotoController {

    private final AlbumService albumService;
    private final FileService fileService;
    private final SecurityHelper securityHelper;

    @Operation(summary = "上传照片到相册（支持多张）")
    @OperationLog(module = "PHOTO", operationType = "CREATE", description = "上传照片", saveArgs = false)
    @PostMapping("/album/{albumId}/photos")
    public Result<List<Photo>> upload(@PathVariable Long albumId,
                                       @RequestParam(value = "files") MultipartFile[] files) throws IOException {
        SysUser user = securityHelper.currentUser();
        List<Photo> photos = new ArrayList<>();
        for (MultipartFile f : files) {
            String url = fileService.upload(f.getBytes(), f.getOriginalFilename(), f.getContentType());
            photos.add(albumService.addPhoto(albumId, user, url, null));
        }
        return Result.success(photos);
    }

    @Operation(summary = "更新照片备注")
    @OperationLog(module = "PHOTO", operationType = "UPDATE", description = "编辑照片备注")
    @PutMapping("/photo/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody PhotoDTO dto) {
        albumService.updatePhoto(id, securityHelper.currentUser(), securityHelper.isOwner(), dto.getDescription());
        return Result.success();
    }

    @Operation(summary = "删除照片")
    @OperationLog(module = "PHOTO", operationType = "DELETE", description = "删除照片")
    @DeleteMapping("/photo/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        albumService.deletePhoto(id, securityHelper.currentUser(), securityHelper.isOwner());
        return Result.success();
    }
}