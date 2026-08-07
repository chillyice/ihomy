package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ihomy.common.BizException;
import com.ihomy.common.ResultCode;
import com.ihomy.dto.AlbumDTO;
import com.ihomy.entity.Album;
import com.ihomy.entity.Family;
import com.ihomy.entity.Photo;
import com.ihomy.entity.SysUser;
import com.ihomy.mapper.AlbumMapper;
import com.ihomy.mapper.FamilyMapper;
import com.ihomy.mapper.PhotoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 相册业务:按家庭管理相册与照片。
 * 权限:成员可管理自己创建/上传的相册与照片,OWNER 可管理任何;游客仅可读默认家庭的公开相册。
 */
@Service
@RequiredArgsConstructor
public class AlbumService {

    private final AlbumMapper albumMapper;
    private final PhotoMapper photoMapper;
    private final FamilyMapper familyMapper;

    /** 相册列表,游客只返回 public 类型;附带封面与照片数 */
    public List<Map<String, Object>> list(Long familyId, boolean isGuest) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (familyId == null) return result;
        LambdaQueryWrapper<Album> qw = new LambdaQueryWrapper<>();
        qw.eq(Album::getFamilyId, familyId);
        if (isGuest) qw.eq(Album::getType, "public");
        qw.orderByDesc(Album::getId);
        for (Album a : albumMapper.selectList(qw)) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", a.getId());
            m.put("name", a.getName());
            m.put("type", a.getType());
            m.put("coverPhotoUrl", a.getCoverPhotoUrl());
            m.put("cover", resolveCover(a));
            m.put("photoCount", countPhotos(a.getId()));
            m.put("createdBy", a.getCreatedBy());
            m.put("createdAt", a.getCreatedAt());
            result.add(m);
        }
        return result;
    }

    /** 相册详情+照片列表;游客仅可访问默认家庭的公开相册 */
    public Map<String, Object> detail(Long albumId, SysUser user) {
        Album a = albumMapper.selectById(albumId);
        if (a == null) throw new BizException(ResultCode.NOT_FOUND);
        if (user == null) {
            if (!"public".equals(a.getType())) throw new BizException(ResultCode.FORBIDDEN);
            Family def = familyMapper.selectDefault();
            if (def == null || !def.getId().equals(a.getFamilyId())) throw new BizException(ResultCode.FORBIDDEN);
        } else if (!a.getFamilyId().equals(user.getFamilyId())) {
            throw new BizException(ResultCode.FORBIDDEN);
        }

        LambdaQueryWrapper<Photo> qw = new LambdaQueryWrapper<>();
        qw.eq(Photo::getAlbumId, albumId).orderByDesc(Photo::getCreatedAt);
        List<Photo> photos = photoMapper.selectList(qw);

        Map<String, Object> data = new HashMap<>();
        data.put("album", a);
        data.put("photos", photos);
        return data;
    }

    /** 新建相册,归属当前用户所在家庭,默认 public 类型 */
    public Album create(SysUser user, AlbumDTO dto) {
        Album a = new Album();
        a.setName(dto.getName());
        a.setType(dto.getType() == null || dto.getType().isBlank() ? "public" : dto.getType());
        a.setFamilyId(user.getFamilyId());
        a.setCreatedBy(user.getId());
        albumMapper.insert(a);
        return a;
    }

    /** 更新相册:仅创建者或家长 */
    public Album update(Long id, SysUser user, boolean isOwner, AlbumDTO dto) {
        Album a = requireOwn(id, user, isOwner);
        a.setName(dto.getName());
        if (dto.getType() != null && !dto.getType().isBlank()) a.setType(dto.getType());
        albumMapper.updateById(a);
        return a;
    }

    /** 删除相册(连带照片):仅创建者或家长 */
    public void delete(Long id, SysUser user, boolean isOwner) {
        requireOwn(id, user, isOwner);
        albumMapper.deleteById(id);
        LambdaQueryWrapper<Photo> qw = new LambdaQueryWrapper<>();
        qw.eq(Photo::getAlbumId, id);
        photoMapper.delete(qw);
    }

    /** 添加照片:可见性随相册类型(public→4,private→3);首张自动成为相册封面 */
    public Photo addPhoto(Long albumId, SysUser user, String url, String description) {
        Album a = albumMapper.selectById(albumId);
        if (a == null) throw new BizException(ResultCode.NOT_FOUND);
        if (!a.getFamilyId().equals(user.getFamilyId())) throw new BizException(ResultCode.FORBIDDEN);

        Photo p = new Photo();
        p.setAlbumId(albumId);
        p.setUrl(url);
        p.setDescription(description);
        p.setAuthorId(user.getId());
        p.setFamilyId(a.getFamilyId());
        p.setVisibility("public".equals(a.getType()) ? 4 : 3);
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

    /** 删除照片:仅上传者或家长 */
    public void deletePhoto(Long photoId, SysUser user, boolean isOwner) {
        requirePhoto(photoId, user, isOwner);
        photoMapper.deleteById(photoId);
    }

    private long countPhotos(Long albumId) {
        LambdaQueryWrapper<Photo> qw = new LambdaQueryWrapper<>();
        qw.eq(Photo::getAlbumId, albumId);
        return photoMapper.selectCount(qw);
    }

    /** 相册封面:优先取显式封面,否则取最新一张照片 */
    private String resolveCover(Album a) {
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
