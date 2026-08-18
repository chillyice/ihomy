package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ihomy.common.BizException;
import com.ihomy.common.DictConst;
import com.ihomy.common.ResultCode;
import com.ihomy.common.UserNames;
import com.ihomy.dto.AnniversaryDTO;
import com.ihomy.entity.Anniversary;
import com.ihomy.entity.SysUser;
import com.ihomy.mapper.AnniversaryMapper;
import com.ihomy.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 家庭纪念日业务:支持阳历/农历(+闰月),可关联成员,每年重复。
 * 全员可增删改本家庭纪念日;列表对同家庭访客也可读。
 */
@Service
@RequiredArgsConstructor
public class AnniversaryService {

    private final AnniversaryMapper anniversaryMapper;
    private final SysUserMapper sysUserMapper;

    /** 家庭纪念日列表,附带关联成员昵称 */
    public List<Map<String, Object>> list(Long familyId) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (familyId == null) return result;
        LambdaQueryWrapper<Anniversary> qw = new LambdaQueryWrapper<>();
        qw.eq(Anniversary::getFamilyId, familyId).orderByDesc(Anniversary::getId);
        List<Anniversary> list = anniversaryMapper.selectList(qw);
        // 批量取关联成员昵称,避免 N+1
        java.util.Set<Long> userIds = new java.util.HashSet<>();
        for (Anniversary a : list) {
            if (a.getUserId() != null) userIds.add(a.getUserId());
        }
        Map<Long, SysUser> userMap = batchUsers(userIds);
        for (Anniversary a : list) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", a.getId());
            m.put("name", a.getName());
            m.put("calendar", a.getCalendar());
            m.put("month", a.getMonth());
            m.put("day", a.getDay());
            m.put("isLeap", a.getIsLeap());
            m.put("recurring", a.getRecurring());
            m.put("userId", a.getUserId());
            SysUser u = a.getUserId() == null ? null : userMap.get(a.getUserId());
            m.put("userName", UserNames.of(u));
            result.add(m);
        }
        return result;
    }

    /** 批量取用户,返回 id→SysUser 映射(空集返空 Map) */
    private Map<Long, SysUser> batchUsers(java.util.Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) return Map.of();
        List<SysUser> users = sysUserMapper.selectBatchIds(ids);
        Map<Long, SysUser> map = new HashMap<>(users.size() * 2);
        for (SysUser u : users) {
            map.put(u.getId(), u);
        }
        return map;
    }

    /** 新增纪念日,默认每年重复 */
    public Anniversary create(Long userId, Long familyId, AnniversaryDTO dto) {
        Anniversary a = new Anniversary();
        apply(a, dto);
        a.setFamilyId(familyId);
        a.setCreatedBy(userId);
        anniversaryMapper.insert(a);
        return a;
    }

    /** 更新纪念日,仅限本家庭 */
    public Anniversary update(Long id, Long familyId, AnniversaryDTO dto) {
        Anniversary a = requireOwn(id, familyId);
        apply(a, dto);
        anniversaryMapper.updateById(a);
        return a;
    }

    /** 删除纪念日,仅限本家庭 */
    public void delete(Long id, Long familyId) {
        requireOwn(id, familyId);
        anniversaryMapper.deleteById(id);
    }

    /** DTO 字段落库,isLeap/recurring 缺省补默认值 */
    private void apply(Anniversary a, AnniversaryDTO dto) {
        a.setName(dto.getName());
        a.setCalendar(dto.getCalendar());
        a.setMonth(dto.getMonth());
        a.setDay(dto.getDay());
        a.setIsLeap(dto.getIsLeap() == null ? 0 : dto.getIsLeap());
        a.setUserId(dto.getUserId());
        a.setRecurring(DictConst.annRecurring(dto.getRecurring()));
    }

    /** 校验纪念日存在且属于该家庭 */
    private Anniversary requireOwn(Long id, Long familyId) {
        Anniversary a = anniversaryMapper.selectById(id);
        if (a == null) throw new BizException(ResultCode.NOT_FOUND);
        if (familyId != null && !familyId.equals(a.getFamilyId())) throw new BizException(ResultCode.FORBIDDEN);
        return a;
    }

    /** 成员昵称(无昵称回退账号名) */
    private String resolveUserName(Long userId) {
        if (userId == null) return null;
        return UserNames.of(sysUserMapper.selectById(userId));
    }
}
