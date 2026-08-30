package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ihomy.common.BizException;
import com.ihomy.common.DictConst;
import com.ihomy.common.ResultCode;
import com.ihomy.dto.AlbumDTO;
import com.ihomy.entity.Album;
import com.ihomy.entity.Family;
import com.ihomy.entity.Photo;
import com.ihomy.entity.StorageDevice;
import com.ihomy.entity.SysUser;
import com.ihomy.mapper.AlbumMapper;
import com.ihomy.mapper.FamilyMapper;
import com.ihomy.mapper.PhotoMapper;
import com.ihomy.mapper.StorageDeviceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 相册业务:按家庭管理相册与照片(含设备目录映射的层级相册)。
 * 权限:成员可管理自己创建/上传的相册与照片,OWNER 可管理任何;游客仅可读默认家庭的公开相册。
 * 映射相册:sourceDeviceId 非空,照片为影子记录(url=storage:// 逻辑地址),出接口时换成签名中转 URL。
 */
@Service
@RequiredArgsConstructor
public class AlbumService {

    private final AlbumMapper albumMapper;
    private final PhotoMapper photoMapper;
    private final FamilyMapper familyMapper;
    private final StorageDeviceMapper storageDeviceMapper;
    private final FileService fileService;
    private final SignedUrlService signedUrlService;
    private final AlbumMapService albumMapService;
    private final ThumbnailService thumbnailService;

    /** 相册列表(全部层级):游客只返回 public;含来源设备名/映射状态/子相册数/子树照片合计 */
    public List<Map<String, Object>> list(Long familyId, boolean isGuest) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (familyId == null) return result;
        LambdaQueryWrapper<Album> qw = new LambdaQueryWrapper<>();
        qw.eq(Album::getFamilyId, familyId);
        if (isGuest) qw.eq(Album::getType, "public");
        qw.orderByDesc(Album::getId);
        List<Album> albums = albumMapper.selectList(qw);
        if (albums.isEmpty()) return result;

        Map<Long, Long> photoCounts = batchPhotoCounts(albums);
        Map<Long, List<Album>> byParent = albums.stream()
                .filter(a -> a.getParentId() != null)
                .collect(Collectors.groupingBy(Album::getParentId));
        Map<Long, String> deviceNames = batchDeviceNames(albums);
        Map<Long, Long> subtreeMemo = new HashMap<>();

        for (Album a : albums) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", a.getId());
            m.put("name", a.getName());
            m.put("type", a.getType());
            m.put("coverPhotoUrl", a.getCoverPhotoUrl());
            m.put("cover", signedUrlService.resolve(resolveCover(a)));
            m.put("photoCount", photoCounts.getOrDefault(a.getId(), 0L));
            m.put("totalPhotoCount", subtreePhotoCount(a, byParent, photoCounts, subtreeMemo));
            m.put("childCount", byParent.getOrDefault(a.getId(), List.of()).size());
            m.put("parentId", a.getParentId());
            m.put("sourceDeviceId", a.getSourceDeviceId());
            m.put("sourceDeviceName", deviceNames.get(a.getSourceDeviceId()));
            m.put("sourcePath", a.getSourcePath());
            m.put("syncStatus", a.getSyncStatus());
            m.put("lastSyncedAt", a.getLastSyncedAt());
            m.put("createdBy", a.getCreatedBy());
            m.put("createdAt", a.getCreatedAt());
            result.add(m);
        }
        return result;
    }

    /** 按主键取相册(PhotoController 上传时取相册名建目录) */
    public Album getById(Long id) {
        return albumMapper.selectById(id);
    }

    /** 相册详情+照片+子相册;游客仅可访问默认家庭的公开相册;映射相册静默触发当前层刷新 */
    public Map<String, Object> detail(Long albumId, SysUser user, Long currentFamilyId) {
        Album a = albumMapper.selectById(albumId);
        if (a == null) throw new BizException(ResultCode.NOT_FOUND);
        if (user == null) {
            if (!"public".equals(a.getType())) throw new BizException(ResultCode.FORBIDDEN);
            Family def = familyMapper.selectDefault();
            if (def == null || !def.getId().equals(a.getFamilyId())) throw new BizException(ResultCode.FORBIDDEN);
        } else if (!a.getFamilyId().equals(currentFamilyId)) {
            throw new BizException(ResultCode.FORBIDDEN);
        }

        LambdaQueryWrapper<Photo> qw = new LambdaQueryWrapper<>();
        qw.eq(Photo::getAlbumId, albumId).orderByDesc(Photo::getCreatedAt);
        List<Photo> photos = photoMapper.selectList(qw);
        for (Photo p : photos) {
            p.setUrl(signedUrlService.resolve(p.getUrl()));
        }

        // 子相册(层级映射):卡片式入口,含各自照片数与状态
        List<Album> children = albumMapper.selectList(new LambdaQueryWrapper<Album>()
                .eq(Album::getParentId, albumId).orderByAsc(Album::getName));
        Map<Long, Long> childCounts = batchPhotoCounts(children);
        Map<Long, List<Album>> childByParent = children.stream()
                .filter(c -> c.getParentId() != null)
                .collect(Collectors.groupingBy(Album::getParentId));
        Map<Long, String> deviceNames = batchDeviceNames(children);
        List<Map<String, Object>> childMaps = new ArrayList<>();
        for (Album c : children) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", c.getId());
            m.put("name", c.getName());
            m.put("cover", signedUrlService.resolve(resolveCover(c)));
            m.put("photoCount", childCounts.getOrDefault(c.getId(), 0L));
            m.put("childCount", childByParent.getOrDefault(c.getId(), List.of()).size());
            m.put("syncStatus", c.getSyncStatus());
            m.put("lastSyncedAt", c.getLastSyncedAt());
            m.put("sourceDeviceName", deviceNames.get(c.getSourceDeviceId()));
            childMaps.add(m);
        }

        if (a.getCoverPhotoUrl() != null) {
            a.setCoverPhotoUrl(signedUrlService.resolve(a.getCoverPhotoUrl()));
        }
        if (user != null && a.getSourceDeviceId() != null) {
            albumMapService.autoRefresh(a); // 静默刷新当前层(2 分钟节流,异步不阻塞响应)
        }

        // 父级相册链(面包屑导航用,从根到父)
        List<Map<String, Object>> parents = new ArrayList<>();
        Long pid = a.getParentId();
        int depth = 0;
        while (pid != null && depth++ < 10) {
            Album p = albumMapper.selectById(pid);
            if (p == null) break;
            Map<String, Object> pm = new HashMap<>();
            pm.put("id", p.getId());
            pm.put("name", p.getName());
            parents.add(0, pm);
            pid = p.getParentId();
        }

        Map<String, Object> data = new HashMap<>();
        data.put("album", a);
        data.put("photos", photos);
        data.put("children", childMaps);
        data.put("parents", parents);
        return data;
    }

    /** 新建相册,归属当前用户所在家庭,默认 public 类型;生成 16 位混淆分享令牌 */
    public Album create(SysUser user, Long currentFamilyId, AlbumDTO dto) {
        Album a = new Album();
        a.setName(dto.getName());
        a.setType(dto.getType() == null || dto.getType().isBlank() ? "public" : dto.getType());
        a.setShareToken(java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        a.setFamilyId(currentFamilyId);
        a.setCreatedBy(user.getId());
        albumMapper.insert(a);
        return a;
    }

    /** 游客凭分享令牌查看公开相册:相册 public 且所属家庭已公开(is_public=1),否则 404 不泄露存在性 */
    public Map<String, Object> shared(String token) {
        Album a = albumMapper.selectOne(new LambdaQueryWrapper<Album>().eq(Album::getShareToken, token));
        if (a == null || !"public".equals(a.getType())) throw new BizException(ResultCode.NOT_FOUND);
        Family family = familyMapper.selectById(a.getFamilyId());
        if (family == null || family.getIsPublic() == null || family.getIsPublic() != 1) {
            throw new BizException(ResultCode.NOT_FOUND);
        }

        LambdaQueryWrapper<Photo> qw = new LambdaQueryWrapper<>();
        qw.eq(Photo::getAlbumId, a.getId())
                .eq(Photo::getVisibility, DictConst.VIS_PUBLIC)
                .orderByDesc(Photo::getCreatedAt);
        List<Map<String, Object>> photos = new ArrayList<>();
        for (Photo p : photoMapper.selectList(qw)) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", p.getId());
            m.put("url", signedUrlService.resolve(p.getUrl()));
            m.put("description", p.getDescription());
            m.put("takenAt", p.getTakenAt());
            m.put("location", p.getLocation());
            photos.add(m);
        }

        Map<String, Object> album = new HashMap<>();
        album.put("id", a.getId());
        album.put("name", a.getName());
        album.put("type", a.getType());
        album.put("shareToken", a.getShareToken());
        album.put("createdAt", a.getCreatedAt());

        Map<String, Object> data = new HashMap<>();
        data.put("album", album);
        data.put("photos", photos);
        return data;
    }

    /** 更新相册:仅创建者或家长 */
    public Album update(Long id, SysUser user, boolean isOwner, AlbumDTO dto) {
        Album a = requireOwn(id, user, isOwner);
        a.setName(dto.getName());
        if (dto.getType() != null && !dto.getType().isBlank()) a.setType(dto.getType());
        albumMapper.updateById(a);
        return a;
    }

    /** 设置/清除自定义封面:仅创建者或家长;url 传 null 清除(回退照片封面)。注意 updateById 忽略 null,须显式 SET */
    public void updateCover(Long id, SysUser user, boolean isOwner, String url) {
        Album a = requireOwn(id, user, isOwner);
        String old = a.getCoverUrl();
        albumMapper.update(null, new LambdaUpdateWrapper<Album>()
                .eq(Album::getId, id)
                .set(Album::getCoverUrl, url));
        if (old != null && !old.isBlank()) fileService.deleteByUrl(old); // 换封面/清除时删旧文件
    }

    /** 删除相册(连同子相册、照片与文件):仅创建者或家长;映射相册删除=解除映射,源文件不受影响 */
    @Transactional
    public void delete(Long id, SysUser user, boolean isOwner) {
        Album root = requireOwn(id, user, isOwner);
        List<Album> all = albumMapper.selectList(new LambdaQueryWrapper<Album>()
                .eq(Album::getFamilyId, root.getFamilyId()));
        List<Album> subtree = new ArrayList<>();
        collectSubtree(root, all, subtree);
        for (Album a : subtree) {
            List<Photo> photos = photoMapper.selectList(new LambdaQueryWrapper<Photo>()
                    .eq(Photo::getAlbumId, a.getId()));
            photoMapper.deletePhysicalByAlbumId(a.getId());
            albumMapper.deletePhysicalById(a.getId());
            for (Photo p : photos) {
                fileService.deleteByUrl(p.getUrl()); // storage:// 逻辑地址自动跳过,设备文件永不删除
                thumbnailService.evictByUrl(p.getUrl()); // 影子照片的缩略图缓存一并清理
            }
        }
    }

    /** 添加照片:可见性随相册类型(public→PUBLIC,private→FAMILY);首张自动成为相册封面 */
    public Photo addPhoto(Long albumId, SysUser user, Long currentFamilyId, String url, String description) {
        Album a = albumMapper.selectById(albumId);
        if (a == null) throw new BizException(ResultCode.NOT_FOUND);
        if (!a.getFamilyId().equals(currentFamilyId)) throw new BizException(ResultCode.FORBIDDEN);

        Photo p = new Photo();
        p.setAlbumId(albumId);
        p.setUrl(url);
        p.setDescription(description);
        p.setAuthorId(user.getId());
        p.setFamilyId(a.getFamilyId());
        p.setVisibility("public".equals(a.getType()) ? DictConst.VIS_PUBLIC : DictConst.VIS_FAMILY);
        photoMapper.insert(p);

        if (a.getCoverPhotoUrl() == null || a.getCoverPhotoUrl().isBlank()) {
            a.setCoverPhotoUrl(url);
            albumMapper.updateById(a);
        }
        return p;
    }

    /** 修改照片备注:仅上传者或家长 */
    public void updatePhoto(Long photoId, SysUser user, boolean isOwner, String description) {
        Photo p = requirePhoto(photoId, user, isOwner);
        p.setDescription(description);
        photoMapper.updateById(p);
    }

    /** 删除照片:仅上传者或家长(连带删除文件与缩略图缓存;影子记录仅删数据库行) */
    @Transactional
    public void deletePhoto(Long photoId, SysUser user, boolean isOwner) {
        Photo p = requirePhoto(photoId, user, isOwner);
        photoMapper.deletePhysicalById(photoId);
        fileService.deleteByUrl(p.getUrl());
        thumbnailService.evictByUrl(p.getUrl());
    }

    /* ---------- 私有工具 ---------- */

    /** 子树照片总数(含自身),内存递归 + 备忘录(相册表数据量小) */
    private long subtreePhotoCount(Album a, Map<Long, List<Album>> byParent,
                                   Map<Long, Long> photoCounts, Map<Long, Long> memo) {
        Long cached = memo.get(a.getId());
        if (cached != null) return cached;
        long total = photoCounts.getOrDefault(a.getId(), 0L);
        for (Album c : byParent.getOrDefault(a.getId(), List.of())) {
            total += subtreePhotoCount(c, byParent, photoCounts, memo);
        }
        memo.put(a.getId(), total);
        return total;
    }

    /** 批量照片计数(GROUP BY,免 N+1) */
    private Map<Long, Long> batchPhotoCounts(List<Album> albums) {
        Map<Long, Long> counts = new HashMap<>();
        if (albums == null || albums.isEmpty()) return counts;
        List<Long> ids = albums.stream().map(Album::getId).toList();
        for (Map<String, Object> row : photoMapper.countByAlbumIds(ids)) {
            counts.put(Long.valueOf(row.get("albumId").toString()),
                    Long.valueOf(row.get("cnt").toString()));
        }
        return counts;
    }

    /** 批量来源设备名(免 N+1) */
    private Map<Long, String> batchDeviceNames(List<Album> albums) {
        Map<Long, String> names = new HashMap<>();
        if (albums == null || albums.isEmpty()) return names;
        Set<Long> ids = new HashSet<>();
        for (Album a : albums) {
            if (a.getSourceDeviceId() != null) ids.add(a.getSourceDeviceId());
        }
        if (ids.isEmpty()) return names;
        for (StorageDevice d : storageDeviceMapper.selectBatchIds(ids)) {
            names.put(d.getId(), d.getName());
        }
        return names;
    }

    /** 收集相册子树(含自身;先父后子顺序无所谓,逐个删除) */
    private void collectSubtree(Album root, List<Album> all, List<Album> out) {
        out.add(root);
        for (Album a : all) {
            if (root.getId().equals(a.getParentId())) collectSubtree(a, all, out);
        }
    }

    /** 相册封面:自定义封面 > 显式封面照片 > 最新一张照片(目录型相册无照片时为 null,前端显示默认图) */
    private String resolveCover(Album a) {
        if (a.getCoverUrl() != null && !a.getCoverUrl().isBlank()) {
            return a.getCoverUrl();
        }
        if (a.getCoverPhotoUrl() != null && !a.getCoverPhotoUrl().isBlank()) {
            return a.getCoverPhotoUrl();
        }
        LambdaQueryWrapper<Photo> qw = new LambdaQueryWrapper<>();
        qw.eq(Photo::getAlbumId, a.getId()).orderByDesc(Photo::getCreatedAt).last("LIMIT 1");
        Photo p = photoMapper.selectOne(qw);
        return p == null ? null : p.getUrl();
    }

    /** 校验相册归属:存在且是创建者(或家长),否则 404/403 */
    private Album requireOwn(Long id, SysUser user, boolean isOwner) {
        Album a = albumMapper.selectById(id);
        if (a == null) throw new BizException(ResultCode.NOT_FOUND);
        if (user == null) throw new BizException(ResultCode.FORBIDDEN);
        boolean creator = a.getCreatedBy() != null && a.getCreatedBy().equals(user.getId());
        if (!isOwner && !creator) throw new BizException(ResultCode.FORBIDDEN);
        return a;
    }

    /** 校验照片归属:存在且是上传者(或家长) */
    private Photo requirePhoto(Long id, SysUser user, boolean isOwner) {
        Photo p = photoMapper.selectById(id);
        if (p == null) throw new BizException(ResultCode.NOT_FOUND);
        if (user == null) throw new BizException(ResultCode.FORBIDDEN);
        boolean author = p.getAuthorId() != null && p.getAuthorId().equals(user.getId());
        if (!isOwner && !author) throw new BizException(ResultCode.FORBIDDEN);
        return p;
    }
}
