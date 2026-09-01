package com.ihomy.controller;

import com.ihomy.annotation.OperationLog;
import com.ihomy.annotation.RequirePermission;
import com.ihomy.common.BizException;
import com.ihomy.common.Result;
import com.ihomy.common.ResultCode;
import com.ihomy.entity.ContentMusic;
import com.ihomy.entity.ContentMusicPlaylist;
import com.ihomy.security.LoginUser;
import com.ihomy.security.SecurityHelper;
import com.ihomy.service.MusicMapService;
import com.ihomy.service.MusicService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

/**
 * 音乐曲库 + 歌单管理(背景音乐播放单元)
 */
@RestController
@RequestMapping("/music")
@RequiredArgsConstructor
public class MusicController {

    private final MusicService musicService;
    private final MusicMapService musicMapService;
    private final SecurityHelper securityHelper;

    private LoginUser requireLogin() {
        LoginUser user = securityHelper.current();
        if (user == null || user.getFamilyId() == null) throw new BizException(ResultCode.UNAUTHORIZED);
        return user;
    }

    // ========== 曲库 ==========

    @Operation(summary = "曲库列表(含映射来源设备名/状态)")
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list() {
        LoginUser user = requireLogin();
        return Result.success(musicService.listByFamily(user.getFamilyId()));
    }

    @Operation(summary = "播放地址(storage:// 逻辑地址现签,签名 URL 过期后重新获取)")
    @GetMapping("/{id}/play-url")
    public Result<Map<String, String>> playUrl(@PathVariable Long id) {
        LoginUser user = requireLogin();
        return Result.success(musicService.playUrl(id, user.getFamilyId()));
    }

    @Operation(summary = "从设备同步音乐:勾选目录映射为影子曲目记录(不拷贝文件)")
    @OperationLog(module = "MUSIC", operationType = "CREATE", description = "设备目录映射音乐", saveArgs = false)
    @RequirePermission("storage:manage")
    @PostMapping("/map")
    public Result<Map<String, Long>> map(@RequestBody Map<String, Object> body) {
        Long deviceId = body.get("deviceId") == null ? 0L : Long.valueOf(body.get("deviceId").toString());
        @SuppressWarnings("unchecked")
        List<String> paths = (List<String>) body.get("paths");
        Long taskId = musicMapService.createMapping(securityHelper.currentUser(), securityHelper.current().getFamilyId(), deviceId, paths);
        return Result.success(Map.of("taskId", taskId));
    }

    @Operation(summary = "刷新设备映射(重扫全部已映射目录,清理消失记录)")
    @OperationLog(module = "MUSIC", operationType = "UPDATE", description = "刷新设备音乐映射")
    @RequirePermission("storage:manage")
    @PostMapping("/refresh")
    public Result<Map<String, Long>> refresh() {
        Long taskId = musicMapService.refreshAll(securityHelper.currentUser(), securityHelper.current().getFamilyId());
        return Result.success(Map.of("taskId", taskId));
    }

    @Operation(summary = "专辑列表(按 album 分组)")
    @GetMapping("/albums")
    public Result<List<Map<String, Object>>> albums() {
        LoginUser user = requireLogin();
        return Result.success(musicService.albumsByFamily(user.getFamilyId()));
    }

    @Operation(summary = "上传单曲(自动解析元数据)")
    @OperationLog(module = "MUSIC", operationType = "CREATE", description = "上传单曲", saveArgs = false)
    @PostMapping("/upload")
    public Result<ContentMusic> upload(@RequestParam("file") MultipartFile file) {
        LoginUser user = requireLogin();
        return Result.success(musicService.uploadAndCreate(user.getFamilyId(), user.getUserId(), file));
    }

    @Operation(summary = "批量上传专辑(文件夹)")
    @OperationLog(module = "MUSIC", operationType = "CREATE", description = "批量上传专辑", saveArgs = false)
    @PostMapping("/upload-album")
    public Result<Void> uploadAlbum(@RequestParam("files") MultipartFile[] files,
                                     @RequestParam(value = "album", required = false) String album) {
        LoginUser user = requireLogin();
        musicService.batchUpload(user.getFamilyId(), user.getUserId(), Arrays.asList(files), album);
        return Result.success(null);
    }

    @Operation(summary = "添加外链曲目")
    @OperationLog(module = "MUSIC", operationType = "CREATE", description = "添加外链曲目")
    @PostMapping
    public Result<ContentMusic> add(@RequestBody ContentMusic dto) {
        LoginUser user = requireLogin();
        return Result.success(musicService.addExternal(user.getFamilyId(), user.getUserId(),
                dto.getUrl(), dto.getTitle(), dto.getArtist(), dto.getAlbum()));
    }

    @Operation(summary = "删除曲目")
    @OperationLog(module = "MUSIC", operationType = "DELETE", description = "删除曲目")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        LoginUser user = requireLogin();
        musicService.deleteMusic(user.getFamilyId(), id);
        return Result.success(null);
    }

    @Operation(summary = "批量删除曲目")
    @OperationLog(module = "MUSIC", operationType = "DELETE", description = "批量删除曲目")
    @DeleteMapping("/batch")
    public Result<Void> batchDelete(@RequestBody Map<String, List<Long>> body) {
        LoginUser user = requireLogin();
        musicService.batchDeleteMusic(user.getFamilyId(), body.getOrDefault("ids", Collections.emptyList()));
        return Result.success(null);
    }

    @Operation(summary = "按专辑批量删除")
    @OperationLog(module = "MUSIC", operationType = "DELETE", description = "按专辑删除曲目")
    @DeleteMapping("/album/{album}")
    public Result<Void> deleteByAlbum(@PathVariable String album) {
        LoginUser user = requireLogin();
        musicService.batchDeleteByAlbum(user.getFamilyId(), album);
        return Result.success(null);
    }

    // ========== 歌单 ==========

    @Operation(summary = "歌单列表")
    @GetMapping("/playlist/list")
    public Result<List<ContentMusicPlaylist>> playlistList() {
        LoginUser user = requireLogin();
        return Result.success(musicService.listPlaylists(user.getFamilyId()));
    }

    @Operation(summary = "新建歌单")
    @OperationLog(module = "MUSIC", operationType = "CREATE", description = "新建歌单")
    @PostMapping("/playlist")
    public Result<ContentMusicPlaylist> createPlaylist(@RequestBody Map<String, String> body) {
        LoginUser user = requireLogin();
        return Result.success(musicService.createPlaylist(user.getFamilyId(), user.getUserId(), body.get("name")));
    }

    @Operation(summary = "删除歌单")
    @OperationLog(module = "MUSIC", operationType = "DELETE", description = "删除歌单")
    @DeleteMapping("/playlist/{id}")
    public Result<Void> deletePlaylist(@PathVariable Long id) {
        LoginUser user = requireLogin();
        musicService.deletePlaylist(user.getFamilyId(), id);
        return Result.success(null);
    }

    @Operation(summary = "歌单曲目列表")
    @GetMapping("/playlist/{id}/tracks")
    public Result<List<ContentMusic>> playlistTracks(@PathVariable Long id) {
        LoginUser user = requireLogin();
        return Result.success(musicService.getPlaylistTracks(user.getFamilyId(), id));
    }

    @Operation(summary = "添加曲目到歌单")
    @OperationLog(module = "MUSIC", operationType = "UPDATE", description = "歌单添加曲目")
    @PostMapping("/playlist/{id}/tracks")
    public Result<Void> addTracks(@PathVariable Long id, @RequestBody Map<String, List<Long>> body) {
        LoginUser user = requireLogin();
        musicService.addTracksToPlaylist(user.getFamilyId(), id, body.getOrDefault("musicIds", Collections.emptyList()));
        return Result.success(null);
    }

    @Operation(summary = "从歌单移除曲目")
    @OperationLog(module = "MUSIC", operationType = "UPDATE", description = "歌单移除曲目")
    @DeleteMapping("/playlist/{id}/tracks/{musicId}")
    public Result<Void> removeTrack(@PathVariable Long id, @PathVariable Long musicId) {
        LoginUser user = requireLogin();
        musicService.removeTrackFromPlaylist(user.getFamilyId(), id, musicId);
        return Result.success(null);
    }

    @Operation(summary = "设为背景音乐歌单")
    @OperationLog(module = "MUSIC", operationType = "UPDATE", description = "设为背景歌单")
    @PutMapping("/playlist/{id}/set-background")
    public Result<Void> setBackground(@PathVariable Long id) {
        LoginUser user = requireLogin();
        musicService.setBackground(user.getFamilyId(), id);
        return Result.success(null);
    }

    @Operation(summary = "取消背景音乐")
    @OperationLog(module = "MUSIC", operationType = "UPDATE", description = "取消背景歌单")
    @DeleteMapping("/playlist/unset-background")
    public Result<Void> unsetBackground() {
        LoginUser user = requireLogin();
        musicService.unsetBackground(user.getFamilyId());
        return Result.success(null);
    }

    @Operation(summary = "获取当前背景音乐歌单+曲目")
    @GetMapping("/background")
    public Result<Map<String, Object>> getBackground() {
        LoginUser user = requireLogin();
        ContentMusicPlaylist p = musicService.getBackgroundPlaylist(user.getFamilyId());
        Map<String, Object> result = new HashMap<>();
        if (p != null) {
            result.put("playlist", p);
            result.put("tracks", musicService.getPlaylistTracks(user.getFamilyId(), p.getId()));
        }
        return Result.success(result);
    }
}
