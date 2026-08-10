package com.ihomy.controller;

import com.ihomy.annotation.OperationLog;
import com.ihomy.common.Result;
import com.ihomy.dto.VideoDTO;
import com.ihomy.dto.VideoWishDTO;
import com.ihomy.entity.SysUser;
import com.ihomy.entity.Video;
import com.ihomy.entity.VideoWish;
import com.ihomy.security.SecurityHelper;
import com.ihomy.service.FileService;
import com.ihomy.service.VideoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 放映厅接口:视频库列表/上传/增删改,以及"想看"清单的提交/列表/入库标记/删除。
 */
@Tag(name = "放映厅")
@RestController
@RequestMapping("/video")
@RequiredArgsConstructor
public class VideoController {

    private final VideoService videoService;
    private final FileService fileService;
    private final SecurityHelper securityHelper;

    @Operation(summary = "视频列表")
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list(@RequestParam(required = false) String keyword,
                                                  @RequestParam(required = false) String mediaType) {
        SysUser user = securityHelper.currentUser();
        Long familyId = user == null ? null : user.getFamilyId();
        return Result.success(videoService.list(familyId, keyword, mediaType));
    }

    @Operation(summary = "上传视频文件")
    @OperationLog(module = "VIDEO", operationType = "CREATE", description = "上传视频", saveArgs = false)
    @PostMapping("/upload")
    public Result<Map<String, String>> upload(@RequestParam("file") MultipartFile file) throws IOException {
        String url = fileService.uploadVideo(file.getBytes(), file.getOriginalFilename(), file.getContentType());
        Map<String, String> data = new HashMap<>();
        data.put("url", url);
        return Result.success(data);
    }

    @Operation(summary = "新增视频")
    @OperationLog(module = "VIDEO", operationType = "CREATE", description = "发布视频")
    @PostMapping
    public Result<Video> create(@RequestBody VideoDTO dto) {
        SysUser user = securityHelper.currentUser();
        return Result.success(videoService.create(user.getId(), user.getFamilyId(), dto));
    }

    @Operation(summary = "更新视频")
    @OperationLog(module = "VIDEO", operationType = "UPDATE", description = "编辑视频")
    @PutMapping("/{id}")
    public Result<Video> update(@PathVariable Long id, @RequestBody VideoDTO dto) {
        SysUser user = securityHelper.currentUser();
        return Result.success(videoService.update(id, user.getFamilyId(), user.getId(), securityHelper.isOwner(), dto));
    }

    @Operation(summary = "删除视频")
    @OperationLog(module = "VIDEO", operationType = "DELETE", description = "删除视频")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        SysUser user = securityHelper.currentUser();
        videoService.delete(id, user.getFamilyId(), user.getId(), securityHelper.isOwner());
        return Result.success();
    }

    @Operation(summary = "提交想看请求")
    @OperationLog(module = "VIDEO", operationType = "CREATE", description = "提交想看请求")
    @PostMapping("/wish")
    public Result<VideoWish> addWish(@RequestBody VideoWishDTO dto) {
        SysUser user = securityHelper.currentUser();
        VideoWish wish = new VideoWish();
        wish.setTitle(dto.getTitle());
        wish.setGenres(dto.getGenres());
        wish.setReason(dto.getReason());
        return Result.success(videoService.addWish(user.getId(), user.getFamilyId(), wish));
    }

    @Operation(summary = "想看请求列表")
    @GetMapping("/wish/list")
    public Result<List<Map<String, Object>>> listWishes() {
        SysUser user = securityHelper.currentUser();
        Long familyId = user == null ? null : user.getFamilyId();
        return Result.success(videoService.listWishes(familyId));
    }

    @Operation(summary = "标记想看已入库")
    @OperationLog(module = "VIDEO", operationType = "UPDATE", description = "标记想看已入库")
    @PutMapping("/wish/{id}/done")
    public Result<Void> markWishDone(@PathVariable Long id) {
        SysUser user = securityHelper.currentUser();
        videoService.markWishDone(id, user.getFamilyId());
        return Result.success();
    }

    @Operation(summary = "删除想看请求")
    @OperationLog(module = "VIDEO", operationType = "DELETE", description = "删除想看请求")
    @DeleteMapping("/wish/{id}")
    public Result<Void> deleteWish(@PathVariable Long id) {
        SysUser user = securityHelper.currentUser();
        videoService.deleteWish(id, user.getFamilyId());
        return Result.success();
    }
}
