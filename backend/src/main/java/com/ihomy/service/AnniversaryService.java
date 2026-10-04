package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ihomy.common.BizException;
import com.ihomy.common.DictConst;
import com.ihomy.common.ResultCode;
import com.ihomy.common.UserNames;
import com.ihomy.dto.AnniversaryDTO;
import com.ihomy.entity.Anniversary;
import com.ihomy.entity.FamilyAnniversaryMember;
import com.ihomy.entity.SysUser;
import com.ihomy.mapper.AnniversaryMapper;
import com.ihomy.mapper.FamilyAnniversaryMemberMapper;
import com.ihomy.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 家庭纪念日业务:支持阳历/农历(+闰月),每年重复,可关联多位家庭成员(V10.11)。
 * 全员可增删改本家庭纪念日;列表对同家庭访客也可读。
 */
@Service
@RequiredArgsConstructor
public class AnniversaryService {

    private final AnniversaryMapper anniversaryMapper;
    private final FamilyAnniversaryMemberMapper memberMapper;
    private final SysUserMapper sysUserMapper;

    /** 家庭纪念日列表,附带关联成员账号与展示名 */
    public List<Map<String, Object>> list(Long familyId) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (familyId == null) return result;
        List<Anniversary> list = anniversaryMapper.selectList(new LambdaQueryWrapper<Anniversary>()
                .eq(Anniversary::getFamilyId, familyId)
                .orderByDesc(Anniversary::getId));
        if (list.isEmpty()) return result;

        // 一次取全部关联行,按纪念日分组;再批量取账号,避免 N+1
        List<Long> annivIds = new ArrayList<>(list.size());
        for (Anniversary a : list) annivIds.add(a.getId());
        Map<Long, List<Long>> memberIdsByAnniv = new HashMap<>();
        Set<Long> userIds = new HashSet<>();
        for (FamilyAnniversaryMember rel : memberMapper.selectList(new LambdaQueryWrapper<FamilyAnniversaryMember>()
                .in(FamilyAnniversaryMember::getAnniversaryId, annivIds)
                .orderByAsc(FamilyAnniversaryMember::getId))) {
            memberIdsByAnniv.computeIfAbsent(rel.getAnniversaryId(), k -> new ArrayList<>()).add(rel.getUserId());
            userIds.add(rel.getUserId());
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
            List<Long> ids = memberIdsByAnniv.getOrDefault(a.getId(), List.of());
            m.put("memberIds", ids);
            List<String> names = new ArrayList<>(ids.size());
            for (Long uid : ids) {
                String n = UserNames.of(userMap.get(uid));
                if (n != null) names.add(n);
            }
            m.put("memberNames", names);
            result.add(m);
        }
        return result;
    }

    /** 批量取用户,返回 id→SysUser 映射(空集返空 Map) */
    private Map<Long, SysUser> batchUsers(Set<Long> ids) {
        if (ids == null || ids.isEmpty()) return Map.of();
        List<SysUser> users = sysUserMapper.selectBatchIds(ids);
        Map<Long, SysUser> map = new HashMap<>(users.size() * 2);
        for (SysUser u : users) {
            map.put(u.getId(), u);
        }
        return map;
    }

    /** 新增纪念日,默认每年重复;关联成员一并落库 */
    @Transactional
    public Anniversary create(Long userId, Long familyId, AnniversaryDTO dto) {
        List<Long> memberIds = normalizeMemberIds(dto);
        validateMembers(familyId, memberIds);
        Anniversary a = new Anniversary();
        apply(a, dto);
        a.setFamilyId(familyId);
        a.setCreatedBy(userId);
        anniversaryMapper.insert(a);
        saveMembers(a.getId(), familyId, memberIds);
        return a;
    }

    /** 更新纪念日,仅限本家庭;关联成员整体重写 */
    @Transactional
    public Anniversary update(Long id, Long familyId, AnniversaryDTO dto) {
        Anniversary a = requireOwn(id, familyId);
        List<Long> memberIds = normalizeMemberIds(dto);
        validateMembers(familyId, memberIds);
        apply(a, dto);
        anniversaryMapper.updateById(a);
        memberMapper.delete(new LambdaQueryWrapper<FamilyAnniversaryMember>()
                .eq(FamilyAnniversaryMember::getAnniversaryId, id));
        saveMembers(id, familyId, memberIds);
        return a;
    }

    /** 删除纪念日,仅限本家庭;关联行一并清除 */
    @Transactional
    public void delete(Long id, Long familyId) {
        requireOwn(id, familyId);
        memberMapper.delete(new LambdaQueryWrapper<FamilyAnniversaryMember>()
                .eq(FamilyAnniversaryMember::getAnniversaryId, id));
        anniversaryMapper.deleteById(id);
    }

    /** DTO 字段落库,isLeap/recurring 缺省补默认值 */
    private void apply(Anniversary a, AnniversaryDTO dto) {
        a.setName(dto.getName());
        a.setCalendar(dto.getCalendar());
        a.setMonth(dto.getMonth());
        a.setDay(dto.getDay());
        a.setIsLeap(dto.getIsLeap() == null ? 0 : dto.getIsLeap());
        a.setRecurring(DictConst.annRecurring(dto.getRecurring()));
    }

    /** 关联成员去重、去空(保序),返回不可变前的可写列表 */
    private List<Long> normalizeMemberIds(AnniversaryDTO dto) {
        List<Long> ids = dto.getMemberIds();
        if (ids == null || ids.isEmpty()) return new ArrayList<>();
        Set<Long> dedup = new LinkedHashSet<>();
        for (Long id : ids) {
            if (id != null) dedup.add(id);
        }
        return new ArrayList<>(dedup);
    }

    /** 关联账号必须都属于本家庭,否则按不存在处理(不泄露他家庭用户) */
    private void validateMembers(Long familyId, List<Long> memberIds) {
        if (memberIds.isEmpty()) return;
        if (familyId == null) throw new BizException(ResultCode.NOT_FOUND);
        Map<Long, SysUser> userMap = batchUsers(new HashSet<>(memberIds));
        for (Long uid : memberIds) {
            SysUser u = userMap.get(uid);
            if (u == null || !Objects.equals(familyId, u.getFamilyId())) {
                throw new BizException(ResultCode.NOT_FOUND);
            }
        }
    }

    /** 写入纪念日-成员关联行 */
    private void saveMembers(Long anniversaryId, Long familyId, List<Long> memberIds) {
        for (Long uid : memberIds) {
            FamilyAnniversaryMember rel = new FamilyAnniversaryMember();
            rel.setAnniversaryId(anniversaryId);
            rel.setUserId(uid);
            rel.setFamilyId(familyId);
            memberMapper.insert(rel);
        }
    }

    /** 校验纪念日存在且属于该家庭 */
    private Anniversary requireOwn(Long id, Long familyId) {
        Anniversary a = anniversaryMapper.selectById(id);
        if (a == null) throw new BizException(ResultCode.NOT_FOUND);
        if (familyId != null && !familyId.equals(a.getFamilyId())) throw new BizException(ResultCode.FORBIDDEN);
        return a;
    }
}
