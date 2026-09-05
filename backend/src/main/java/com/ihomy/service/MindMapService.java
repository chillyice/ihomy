package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ihomy.common.BizException;
import com.ihomy.common.ResultCode;
import com.ihomy.common.UserNames;
import com.ihomy.dto.MindMapDTO;
import com.ihomy.entity.ContentMindmap;
import com.ihomy.entity.SysUser;
import com.ihomy.mapper.ContentMindmapMapper;
import com.ihomy.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 脑图服务:工具箱-脑图设计。
 * 家庭级共享,全家庭成员均可查看/编辑/删除;逻辑删除。
 */
@Service
@RequiredArgsConstructor
public class MindMapService {

    private final ContentMindmapMapper mindmapMapper;
    private final SysUserMapper sysUserMapper;

    /** 脑图列表(不含 data 大字段),按更新时间倒序 */
    public List<Map<String, Object>> list(Long familyId) {
        List<ContentMindmap> rows = mindmapMapper.selectList(new LambdaQueryWrapper<ContentMindmap>()
                .eq(familyId != null, ContentMindmap::getFamilyId, familyId)
                .orderByDesc(ContentMindmap::getUpdatedAt));
        if (rows.isEmpty()) return Collections.emptyList();
        Set<Long> userIds = rows.stream().map(ContentMindmap::getUserId).collect(Collectors.toSet());
        Map<Long, String> nameMap = sysUserMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(SysUser::getId, UserNames::of));
        List<Map<String, Object>> result = new ArrayList<>(rows.size());
        for (ContentMindmap m : rows) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", m.getId());
            item.put("title", m.getTitle());
            item.put("creatorName", nameMap.get(m.getUserId()));
            item.put("createdAt", m.getCreatedAt());
            item.put("updatedAt", m.getUpdatedAt());
            result.add(item);
        }
        return result;
    }

    public ContentMindmap get(Long id, Long familyId) {
        return require(id, familyId);
    }

    public ContentMindmap create(Long userId, Long familyId, MindMapDTO dto) {
        ContentMindmap m = new ContentMindmap();
        m.setFamilyId(familyId);
        m.setUserId(userId);
        m.setTitle(checkTitle(dto.getTitle()));
        m.setData(StringUtils.hasText(dto.getData()) ? dto.getData() : null);
        mindmapMapper.insert(m);
        return m;
    }

    public ContentMindmap update(Long id, Long familyId, MindMapDTO dto) {
        ContentMindmap m = require(id, familyId);
        if (dto.getTitle() != null) m.setTitle(checkTitle(dto.getTitle()));
        if (dto.getData() != null) m.setData(dto.getData());
        mindmapMapper.updateById(m);
        return m;
    }

    public void delete(Long id, Long familyId) {
        require(id, familyId);
        mindmapMapper.deleteById(id);
    }

    private ContentMindmap require(Long id, Long familyId) {
        ContentMindmap m = mindmapMapper.selectById(id);
        if (m == null || !m.getFamilyId().equals(familyId)) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        return m;
    }

    private String checkTitle(String title) {
        String t = title == null ? "" : title.trim();
        if (t.isEmpty()) throw new BizException(ResultCode.BAD_REQUEST, "请填写脑图标题");
        if (t.length() > 100) throw new BizException(ResultCode.BAD_REQUEST, "脑图标题不能超过100字");
        return t;
    }
}
