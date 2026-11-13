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
import com.ihomy.service.access.ContentKind;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
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
 * 亲友圈分享(V12.21):接收家庭在列表里看到别人家分享来的纪念日,只读 + 带来源家庭;
 * 写侧永远只在所属家庭(跨家庭改删一律 404)。
 */
@Service
@RequiredArgsConstructor
public class AnniversaryService {

    private final AnniversaryMapper anniversaryMapper;
    private final FamilyAnniversaryMemberMapper memberMapper;
    private final SysUserMapper sysUserMapper;
    private final AudienceService audienceService;

    /**
     * 家庭纪念日列表:本家庭 + (可选)亲友圈里分享给本家庭的,按 id 倒序。
     * 被分享的条目标 readOnly 并带 sharedFrom(家庭名/圈内称呼),不返回对方的成员账号 id。
     *
     * @param includeShared 游客口径传 false:只出本家庭(演示家庭)的纪念日
     */
    public List<Map<String, Object>> list(Long familyId, boolean includeShared) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (familyId == null) return result;
        List<Anniversary> list = new ArrayList<>(anniversaryMapper.selectList(new LambdaQueryWrapper<Anniversary>()
                .eq(Anniversary::getFamilyId, familyId)
                .orderByDesc(Anniversary::getId)));
        Map<Long, Map<String, Object>> sharedSources = Map.of();
        if (includeShared) {
            List<Long> sharedIds = audienceService.sharedIds(ContentKind.ANNIVERSARY, familyId);
            if (!sharedIds.isEmpty()) {
                sharedSources = audienceService.sharedSources(ContentKind.ANNIVERSARY, sharedIds, familyId);
                List<Anniversary> shared = anniversaryMapper.selectBatchIds(sharedIds);
                shared.sort(Comparator.comparing(Anniversary::getId).reversed());
                list.addAll(shared);
            }
        }
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
            result.add(toView(a, familyId, memberIdsByAnniv.getOrDefault(a.getId(), List.of()),
                    userMap, sharedSources.get(a.getId())));
        }
        return result;
    }

    /** 单条纪念日 → 展示结构;跨家庭条目带 readOnly 与来源,不回传成员账号 id */
    private Map<String, Object> toView(Anniversary a, Long viewerFamilyId, List<Long> memberIds,
                                       Map<Long, SysUser> userMap, Map<String, Object> sharedFrom) {
        boolean readOnly = !Objects.equals(viewerFamilyId, a.getFamilyId());
        Map<String, Object> m = new HashMap<>();
        m.put("id", a.getId());
        m.put("name", a.getName());
        m.put("calendar", a.getCalendar());
        m.put("month", a.getMonth());
        m.put("day", a.getDay());
        m.put("isLeap", a.getIsLeap());
        m.put("recurring", a.getRecurring());
        m.put("remindDaysBefore", a.getRemindDaysBefore());
        m.put("remindTime", a.getRemindTime());
        // 别人的纪念日只给展示名(谁生日就是标题的一部分),不给账号 id,防止被拿去拼请求
        m.put("memberIds", readOnly ? List.of() : memberIds);
        List<String> names = new ArrayList<>(memberIds.size());
        for (Long uid : memberIds) {
            String n = UserNames.of(userMap.get(uid));
            if (n != null) names.add(n);
        }
        m.put("memberNames", names);
        m.put("readOnly", readOnly);
        if (readOnly) m.put("sharedFrom", sharedFrom);
        return m;
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

    /** 删除纪念日,仅限本家庭;关联行与分享范围一并清除 */
    @Transactional
    public void delete(Long id, Long familyId) {
        requireOwn(id, familyId);
        memberMapper.delete(new LambdaQueryWrapper<FamilyAnniversaryMember>()
                .eq(FamilyAnniversaryMember::getAnniversaryId, id));
        anniversaryMapper.deleteById(id);
        audienceService.revokeContent(ContentKind.ANNIVERSARY, id);
    }

    /** DTO 字段落库,isLeap/recurring 缺省补默认值;提醒天数为 0/空表示关闭,两列一并置空 */
    private void apply(Anniversary a, AnniversaryDTO dto) {
        a.setName(dto.getName());
        a.setCalendar(dto.getCalendar());
        a.setMonth(dto.getMonth());
        a.setDay(dto.getDay());
        a.setIsLeap(dto.getIsLeap() == null ? 0 : dto.getIsLeap());
        a.setRecurring(DictConst.annRecurring(dto.getRecurring()));
        Integer remindDays = dto.getRemindDaysBefore();
        if (remindDays != null && (remindDays < 0 || remindDays > 30)) {
            throw new BizException(ResultCode.BAD_REQUEST, "提前提醒天数请填 1-30 天");
        }
        boolean remind = remindDays != null && remindDays > 0;
        a.setRemindDaysBefore(remind ? remindDays : null);
        a.setRemindTime(remind ? dto.getRemindTime() : null);
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

    /** 校验纪念日存在且属于该家庭;跨家庭一律 404(不泄露存在性) */
    private Anniversary requireOwn(Long id, Long familyId) {
        Anniversary a = anniversaryMapper.selectById(id);
        if (a == null) throw new BizException(ResultCode.NOT_FOUND);
        if (familyId == null || !familyId.equals(a.getFamilyId())) throw new BizException(ResultCode.NOT_FOUND);
        return a;
    }
}
