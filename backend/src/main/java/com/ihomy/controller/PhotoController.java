package com.ihomy.controller;

import com.ihomy.annotation.OperationLog;
import com.ihomy.common.Result;
import com.ihomy.dto.PhotoDTO;
import com.ihomy.dto.PhotoFromUrlDTO;
import com.ihomy.entity.Album;
import com.ihomy.entity.Photo;
import com.ihomy.entity.SysUser;
import com.ihomy.security.SecurityHelper;
import com.ihomy.service.AlbumService;
import com.ihomy.service.FileService;
import com.ihomy.service.PointsService;
import com.ihomy.controller.PublicController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
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
    private final PointsService pointsService;
    private final SecurityHelper securityHelper;
    private final PublicController publicController;

    @Operation(summary = "上传照片到相册（支持多张）")
    @OperationLog(module = "PHOTO", operationType = "CREATE", description = "上传照片", saveArgs = false)
    @PostMapping("/album/{albumId}/photos")
    public Result<List<Photo>> upload(@PathVariable Long albumId,
                                       @RequestParam(value = "files") MultipartFile[] files) throws IOException {
        SysUser user = securityHelper.currentUser();
        Album album = albumService.getById(albumId);
        if (album == null) throw new com.ihomy.common.BizException(com.ihomy.common.ResultCode.NOT_FOUND);
        Long fid = securityHelper.current().getFamilyId();
        if (!album.getFamilyId().equals(fid)) throw new com.ihomy.common.BizException(com.ihomy.common.ResultCode.FORBIDDEN);
        List<Photo> photos = new ArrayList<>();
        for (MultipartFile f : files) {
            String url = fileService.upload(f, f.getOriginalFilename(), f.getContentType(),
                    albumId, album == null ? null : album.getName());
            photos.add(albumService.addPhoto(albumId, user, fid, url, null));
        }
        if (!photos.isEmpty()) {
            pointsService.addRecord(user.getId(), fid, "REWARD",
                    PointsService.REWARD_PHOTO * photos.size(), "上传照片 ×" + photos.size());
            publicController.invalidateHomeCache(fid);
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
        Long fid = securityHelper.current().getFamilyId();
        albumService.deletePhoto(id, securityHelper.currentUser(), securityHelper.isOwner());
        publicController.invalidateHomeCache(fid);
        return Result.success();
    }

    @Operation(summary = "保存网络/Base64 图片到相册(AI 生图落库)")
    @OperationLog(module = "PHOTO", operationType = "CREATE", description = "保存 AI 生图到相册", saveArgs = false)
    @PostMapping("/album/{albumId}/photos/from-url")
    public Result<List<Photo>> saveFromUrl(@PathVariable Long albumId, @RequestBody PhotoFromUrlDTO dto) throws IOException {
        String url = dto.getUrl();
        if (url == null || url.isBlank()) throw new com.ihomy.common.BizException(com.ihomy.common.ResultCode.BAD_REQUEST, "url 不能为空");
        SysUser user = securityHelper.currentUser();
        if (user == null) throw new com.ihomy.common.BizException(com.ihomy.common.ResultCode.UNAUTHORIZED);
        Album album = albumService.getById(albumId);
        if (album == null) throw new com.ihomy.common.BizException(com.ihomy.common.ResultCode.NOT_FOUND);
        Long fid = securityHelper.current().getFamilyId();
        if (!album.getFamilyId().equals(fid)) throw new com.ihomy.common.BizException(com.ihomy.common.ResultCode.FORBIDDEN);
        byte[] data = fetchRemote(url);
        String ext = extOf(url);
        String savedUrl = fileService.upload(data, "ai." + ext, "image/" + (ext.equals("jpg") ? "jpeg" : ext), albumId, album.getName());
        Photo photo = albumService.addPhoto(albumId, user, fid, savedUrl, "AI 生图");
        publicController.invalidateHomeCache(fid);
        return Result.success(List.of(photo));
    }

    /** 下载 http(s) / 解码 data: 图片为字节;非 http(s) 协议拒绝,防 SSRF 越界 */
    private static byte[] fetchRemote(String url) throws IOException {
        if (url.startsWith("data:")) {
            int comma = url.indexOf(',');
            String b64 = comma >= 0 ? url.substring(comma + 1) : url;
            return Base64.getDecoder().decode(b64);
        }
        URI uri = URI.create(url);
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new IOException("不支持的图片协议: " + scheme);
        }
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
        HttpRequest req = HttpRequest.newBuilder(uri).GET().timeout(Duration.ofSeconds(30)).build();
        HttpResponse<byte[]> resp;
        try {
            resp = client.send(req, HttpResponse.BodyHandlers.ofByteArray());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IOException("下载图片失败: HTTP " + resp.statusCode());
        }
        return resp.body();
    }

    private static String extOf(String url) {
        if (url.startsWith("data:")) {
            String head = url.substring(5); // 去掉 "data:"
            int end = head.indexOf(';');
            if (end < 0) end = head.indexOf(',');
            String mime = (end < 0 ? head : head.substring(0, end)).toLowerCase();
            if (mime.contains("webp")) return "webp";
            if (mime.contains("jpg") || mime.contains("jpeg")) return "jpg";
            if (mime.contains("gif")) return "gif";
            return "png";
        }
        String path = url.toLowerCase().split("\\?")[0];
        int dot = path.lastIndexOf('.');
        if (dot >= 0) {
            String e = path.substring(dot + 1);
            if (e.equals("jpeg")) return "jpg";
            if (e.equals("png") || e.equals("webp") || e.equals("gif")) return e;
        }
        return "png";
    }
}