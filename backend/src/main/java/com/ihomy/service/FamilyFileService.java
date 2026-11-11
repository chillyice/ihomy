package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ihomy.common.BizException;
import com.ihomy.common.ResultCode;
import com.ihomy.entity.FamilyFile;
import com.ihomy.entity.SysUser;
import com.ihomy.mapper.FamilyFileMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 家庭文件业务:成员上传、全家可见、删除限上传者本人或家长。
 * 文件本体走 FileService 统一落盘,这里只维护列表记录;删除只删记录(与相册/图书馆的回收站口径不同,
 * 家庭文件不进回收站,点删即从列表消失,磁盘文件留给运维清理)。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FamilyFileService {

    /** 列表上限:家庭文件是「随手存」用途,不做分页,先给最近 500 条 */
    private static final int LIST_LIMIT = 500;
    private static final int NAME_MAX = 255;

    private final FamilyFileMapper familyFileMapper;
    private final FileService fileService;

    public List<FamilyFile> list(Long familyId, String keyword) {
        if (familyId == null) return List.of();
        LambdaQueryWrapper<FamilyFile> qw = new LambdaQueryWrapper<>();
        qw.eq(FamilyFile::getFamilyId, familyId);
        if (StringUtils.hasText(keyword)) qw.like(FamilyFile::getFileName, keyword.trim());
        qw.orderByDesc(FamilyFile::getCreatedAt).orderByDesc(FamilyFile::getId).last("LIMIT " + LIST_LIMIT);
        return familyFileMapper.selectList(qw);
    }

    public FamilyFile upload(SysUser user, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) throw new BizException(ResultCode.BAD_REQUEST);
        String original = cleanName(file.getOriginalFilename());
        String url = fileService.upload(file, original, file.getContentType());
        FamilyFile row = new FamilyFile();
        row.setFamilyId(user.getFamilyId());
        row.setUploaderId(user.getId());
        row.setFileName(original);
        row.setFileUrl(url);
        row.setMimeType(file.getContentType());
        row.setFileSize(file.getSize());
        row.setCreatedAt(LocalDateTime.now());
        familyFileMapper.insert(row);
        return row;
    }

    public void delete(Long id, SysUser user, boolean isOwner) {
        FamilyFile row = familyFileMapper.selectById(id);
        if (row == null || !row.getFamilyId().equals(user.getFamilyId())) throw new BizException(ResultCode.NOT_FOUND);
        if (!isOwner && !user.getId().equals(row.getUploaderId())) throw new BizException(ResultCode.FORBIDDEN);
        row.setDeletedAt(LocalDateTime.now());
        familyFileMapper.updateById(row);
        familyFileMapper.deleteById(id);
    }

    /** 只取文件名本身:去掉路径与控制字符,防止 ../ 或超长名进库 */
    private String cleanName(String raw) {
        String name = StringUtils.cleanPath(raw == null ? "" : raw);
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) name = name.substring(slash + 1);
        name = name.replaceAll("[\\p{Cntrl}]", "").trim();
        if (name.isEmpty()) name = "file";
        return name.length() > NAME_MAX ? name.substring(name.length() - NAME_MAX) : name;
    }
}
