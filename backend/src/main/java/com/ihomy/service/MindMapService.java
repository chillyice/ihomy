package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ihomy.common.BizException;
import com.ihomy.common.ResultCode;
import com.ihomy.common.UserNames;
import com.ihomy.dto.MindMapDTO;
import com.ihomy.entity.ContentMindmap;
import com.ihomy.entity.ContentMindmapSnapshot;
import com.ihomy.entity.SysUser;
import com.ihomy.mapper.ContentMindmapMapper;
import com.ihomy.mapper.ContentMindmapSnapshotMapper;
import com.ihomy.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 脑图服务:工具箱-脑图设计。
 * 家庭级共享,全家庭成员均可查看/编辑/删除;逻辑删除;历史版本快照(每图留最近 20 份)。
 */
@Service
@RequiredArgsConstructor
public class MindMapService {

    /** 每图保留快照数 */
    private static final int SNAPSHOT_KEEP = 20;

    private final ContentMindmapMapper mindmapMapper;
    private final ContentMindmapSnapshotMapper snapshotMapper;
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
            item.put("thumbUrl", m.getThumbUrl());
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
        m.setThumbUrl(StringUtils.hasText(dto.getThumbUrl()) ? dto.getThumbUrl() : null);
        mindmapMapper.insert(m);
        return m;
    }

    public ContentMindmap update(Long id, Long familyId, MindMapDTO dto) {
        ContentMindmap m = require(id, familyId);
        if (dto.getTitle() != null) m.setTitle(checkTitle(dto.getTitle()));
        if (dto.getData() != null) m.setData(dto.getData());
        if (dto.getThumbUrl() != null) m.setThumbUrl(StringUtils.hasText(dto.getThumbUrl()) ? dto.getThumbUrl() : null);
        // 不走 updateById:它会把 selectById 带出的旧 updated_at 显式回写,表上 ON UPDATE CURRENT_TIMESTAMP
        // 不触发,updated_at 永远停在创建时间 → 协同轮询(按 updatedAt 变化检测远端更新)与列表排序失效。
        // LambdaUpdateWrapper 只 SET 业务字段,updated_at 由表自动刷新;重查一次把新值带回给前端。
        // 乐观锁:客户端带 baseUpdatedAt(最后一次响应的 updated_at)时,库中 updated_at 若已被家人刷新
        // 则 update 影响 0 行 → 409,避免多标签页/多端互相覆盖(实测双开 20s 轮询窗口内必丢一端改动)。
        LambdaUpdateWrapper<ContentMindmap> uw = new LambdaUpdateWrapper<ContentMindmap>()
                .eq(ContentMindmap::getId, id)
                .set(ContentMindmap::getTitle, m.getTitle())
                .set(ContentMindmap::getData, m.getData())
                .set(ContentMindmap::getThumbUrl, m.getThumbUrl());
        if (StringUtils.hasText(dto.getBaseUpdatedAt())) {
            uw.le(ContentMindmap::getUpdatedAt, LocalDateTime.parse(dto.getBaseUpdatedAt()));
        }
        int rows = mindmapMapper.update(null, uw);
        if (rows == 0 && StringUtils.hasText(dto.getBaseUpdatedAt())) {
            throw new BizException(ResultCode.CONFLICT, "家人已更新了脑图，保存被阻止以免覆盖 TA 的修改");
        }
        return mindmapMapper.selectById(id);
    }

    public void delete(Long id, Long familyId) {
        require(id, familyId);
        mindmapMapper.deleteById(id);
    }

    /** 回收站列表(逻辑删数据 MP 常规查询不可见,走 XML) */
    public List<Map<String, Object>> trash(Long familyId) {
        List<ContentMindmap> rows = mindmapMapper.selectTrashByFamily(familyId);
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
            item.put("updatedAt", m.getUpdatedAt());
            result.add(item);
        }
        return result;
    }

    /** 从回收站恢复 */
    public void restore(Long id, Long familyId) {
        requireInTrash(id, familyId);
        mindmapMapper.restoreById(id);
    }

    /** 彻底删除(物理删) */
    public void purge(Long id, Long familyId) {
        requireInTrash(id, familyId);
        mindmapMapper.deletePhysicalById(id);
    }

    /** 校验家庭内已删除的脑图(MP selectById 自动过滤 deleted=1,走 XML) */
    private ContentMindmap requireInTrash(Long id, Long familyId) {
        ContentMindmap m = mindmapMapper.selectDeletedById(id);
        if (m == null || !m.getFamilyId().equals(familyId)) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        return m;
    }

    // ---------- 历史版本快照 ----------

    /** 创建快照(source=AUTO/MANUAL),超出保留数自动清理 */
    public void snapshot(Long id, Long userId, Long familyId, String source) {
        ContentMindmap m = require(id, familyId);
        ContentMindmapSnapshot s = new ContentMindmapSnapshot();
        s.setMindmapId(m.getId());
        s.setFamilyId(familyId);
        s.setUserId(userId);
        s.setTitle(m.getTitle());
        s.setData(m.getData());
        s.setSource("MANUAL".equals(source) ? "MANUAL" : "AUTO");
        snapshotMapper.insert(s);
        snapshotMapper.deleteKeepRecent(m.getId(), SNAPSHOT_KEEP);
    }

    /** 快照列表(不含 data 大字段,含操作人名) */
    public List<Map<String, Object>> listSnapshots(Long id, Long familyId) {
        require(id, familyId);
        List<ContentMindmapSnapshot> rows = snapshotMapper.selectListByMindmapId(id);
        if (rows.isEmpty()) return Collections.emptyList();
        Set<Long> userIds = rows.stream().map(ContentMindmapSnapshot::getUserId).collect(Collectors.toSet());
        Map<Long, String> nameMap = sysUserMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(SysUser::getId, UserNames::of));
        List<Map<String, Object>> result = new ArrayList<>(rows.size());
        for (ContentMindmapSnapshot s : rows) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", s.getId());
            item.put("title", s.getTitle());
            item.put("source", s.getSource());
            item.put("creatorName", nameMap.get(s.getUserId()));
            item.put("createdAt", s.getCreatedAt());
            result.add(item);
        }
        return result;
    }

    /** 获取快照完整数据(回滚预览/恢复用) */
    public ContentMindmapSnapshot getSnapshot(Long id, Long snapshotId, Long familyId) {
        require(id, familyId);
        ContentMindmapSnapshot s = snapshotMapper.selectById(snapshotId);
        if (s == null || !s.getMindmapId().equals(id) || !s.getFamilyId().equals(familyId)) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        return s;
    }

    /** 回滚:用快照数据覆盖当前脑图(当前内容先自动存一份快照防手滑) */
    public ContentMindmap restoreSnapshot(Long id, Long snapshotId, Long userId, Long familyId) {
        require(id, familyId);
        ContentMindmapSnapshot s = getSnapshot(id, snapshotId, familyId);
        ContentMindmap m = require(id, familyId);
        // 回滚前把当前状态存为快照(MANUAL 语义,便于再滚回来)
        ContentMindmapSnapshot backup = new ContentMindmapSnapshot();
        backup.setMindmapId(m.getId());
        backup.setFamilyId(familyId);
        backup.setUserId(userId);
        backup.setTitle(m.getTitle());
        backup.setData(m.getData());
        backup.setSource("MANUAL");
        snapshotMapper.insert(backup);
        snapshotMapper.deleteKeepRecent(m.getId(), SNAPSHOT_KEEP);
        // 恢复快照内容(updateById 会回写旧 updated_at 抑制 ON UPDATE 刷新,改走 LambdaUpdateWrapper)
        mindmapMapper.update(null, new LambdaUpdateWrapper<ContentMindmap>()
                .eq(ContentMindmap::getId, m.getId())
                .set(ContentMindmap::getTitle, s.getTitle())
                .set(ContentMindmap::getData, s.getData()));
        return mindmapMapper.selectById(m.getId());
    }

    /** 删除单份快照 */
    public void deleteSnapshot(Long id, Long snapshotId, Long familyId) {
        require(id, familyId);
        ContentMindmapSnapshot s = getSnapshot(id, snapshotId, familyId);
        snapshotMapper.deleteById(s.getId());
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
