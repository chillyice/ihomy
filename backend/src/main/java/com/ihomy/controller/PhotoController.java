package com.ihomy.controller;

import com.ihomy.annotation.OperationLog;
import com.ihomy.common.HttpUrlUtil;
import com.ihomy.common.LivePhotoUtil;
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
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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

    /** 远程图片体积上限:AI 生图直链足够;生产堆 384m,响应无上限读入会 OOM */
    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;
    /** 手动跟随重定向上限——每一跳都要重新校验地址,不交给 HttpClient 自动跳(自动跳会绕过校验) */
    private static final int MAX_REDIRECTS = 3;

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
        // 实况照片:同一次上传内,同名的静态图 + 短片(苹果 Live Photo)配对后关联
        Map<String, MultipartFile> liveVideos = new HashMap<>();
        List<MultipartFile> images = new ArrayList<>();
        for (MultipartFile f : files) {
            if (LivePhotoUtil.isVideo(f.getOriginalFilename(), f.getContentType())) {
                liveVideos.put(LivePhotoUtil.baseKey(f.getOriginalFilename()), f);
            } else {
                images.add(f);
            }
        }
        for (MultipartFile f : images) {
            String url = fileService.upload(f, f.getOriginalFilename(), f.getContentType(),
                    albumId, album == null ? null : album.getName());
            MultipartFile mov = liveVideos.get(LivePhotoUtil.baseKey(f.getOriginalFilename()));
            String liveVideoUrl = mov == null ? null
                    : fileService.uploadVideo(mov, mov.getOriginalFilename(), mov.getContentType());
            photos.add(albumService.addPhoto(albumId, user, fid, url, null, liveVideoUrl));
        }
        if (!photos.isEmpty()) {
            pointsService.rewardPhotoUpload(user.getId(), fid, photos.size());
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
        String ext = extOf(url);
        String baseName = (dto.getName() == null || dto.getName().isBlank()) ? "ai" : dto.getName();
        String contentType = "image/" + (ext.equals("jpg") ? "jpeg" : ext);
        String savedUrl;
        if (url.startsWith("data:")) {
            savedUrl = fileService.upload(decodeDataUrl(url), baseName + "." + ext, contentType,
                    albumId, album.getName());
        } else {
            // http(s) 走临时文件流式落盘:抓取全程不整包入堆
            Path tmp = fetchToTemp(url);
            try {
                savedUrl = fileService.upload(tmp, baseName + "." + ext, contentType, albumId, album.getName());
            } finally {
                Files.deleteIfExists(tmp);
            }
        }
        String desc = (dto.getDescription() == null || dto.getDescription().isBlank()) ? "AI 生图" : dto.getDescription();
        Photo photo = albumService.addPhoto(albumId, user, fid, savedUrl, desc, null);
        publicController.invalidateHomeCache(fid);
        return Result.success(List.of(photo));
    }

    /** data: 图片解码为字节:先按 base64 长度设上限,防超长串把堆吃满 */
    private static byte[] decodeDataUrl(String url) {
        int comma = url.indexOf(',');
        if (comma < 0) {
            throw new com.ihomy.common.BizException(com.ihomy.common.ResultCode.BAD_REQUEST, "图片数据不完整");
        }
        String b64 = url.substring(comma + 1);
        if (b64.length() > MAX_IMAGE_BYTES / 3 * 4 + 16) {
            throw new com.ihomy.common.BizException(com.ihomy.common.ResultCode.BAD_REQUEST, "图片超过大小限制");
        }
        try {
            return Base64.getDecoder().decode(b64);
        } catch (IllegalArgumentException e) {
            throw new com.ihomy.common.BizException(com.ihomy.common.ResultCode.BAD_REQUEST, "图片数据无法解析");
        }
    }

    /**
     * 下载 http(s) 图片到临时文件:限体积 + 逐跳地址校验 + 流式写盘。
     * 拦协议外地址、云元数据主机名与解析到内网段的地址(含重定向每一跳);
     * 调用方负责删临时文件。
     */
    private static Path fetchToTemp(String url) throws IOException {
        URI uri = checkFetchable(URI.create(url.trim()));
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
        for (int hop = 0; ; hop++) {
            HttpRequest req = HttpRequest.newBuilder(uri).GET().timeout(Duration.ofSeconds(30)).build();
            HttpResponse<InputStream> resp;
            try {
                resp = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException(e);
            }
            int code = resp.statusCode();
            if (code >= 300 && code < 400) {
                resp.body().close();
                if (hop >= MAX_REDIRECTS) throw new IOException("下载图片失败: 重定向次数过多");
                String loc = resp.headers().firstValue("Location").orElse(null);
                if (loc == null) throw new IOException("下载图片失败: 重定向缺少目标地址");
                uri = checkFetchable(uri.resolve(loc));
                continue;
            }
            if (code < 200 || code >= 300) {
                resp.body().close();
                throw new IOException("下载图片失败: HTTP " + code);
            }
            Path tmp = Files.createTempFile("ihomy-fetch-", ".img");
            try (InputStream in = resp.body(); OutputStream out = Files.newOutputStream(tmp)) {
                byte[] buf = new byte[8192];
                long total = 0;
                int n;
                while ((n = in.read(buf)) > 0) {
                    total += n;
                    if (total > MAX_IMAGE_BYTES) {
                        throw new com.ihomy.common.BizException(com.ihomy.common.ResultCode.BAD_REQUEST, "图片超过大小限制");
                    }
                    out.write(buf, 0, n);
                }
            } catch (Throwable t) {
                Files.deleteIfExists(tmp);
                throw t;
            }
            return tmp;
        }
    }

    /** 地址校验:仅 http(s);拦云元数据等主机名;域名解析结果落在内网段同样拒绝 */
    private static URI checkFetchable(URI uri) throws IOException {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new IOException("不支持的图片协议: " + scheme);
        }
        String host = uri.getHost();
        if (host == null || host.isEmpty()) {
            throw new IOException("图片地址缺少主机名");
        }
        String blocked = "这个地址不能作为图片来源";
        if (HttpUrlUtil.isBlockedHost(host)) {
            throw new com.ihomy.common.BizException(com.ihomy.common.ResultCode.BAD_REQUEST, blocked);
        }
        InetAddress[] addrs;
        try {
            addrs = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new IOException("域名无法解析: " + host);
        }
        for (InetAddress a : addrs) {
            if (HttpUrlUtil.isPrivateAddress(a)) {
                throw new com.ihomy.common.BizException(com.ihomy.common.ResultCode.BAD_REQUEST, blocked);
            }
        }
        return uri;
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