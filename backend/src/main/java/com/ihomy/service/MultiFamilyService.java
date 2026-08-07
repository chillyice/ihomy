package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ihomy.common.BizException;
import com.ihomy.common.ResultCode;
import com.ihomy.entity.Family;
import com.ihomy.entity.FamilyApply;
import com.ihomy.entity.SysUser;
import com.ihomy.entity.SysUserRole;
import com.ihomy.mapper.FamilyApplyMapper;
import com.ihomy.mapper.FamilyMapper;
import com.ihomy.mapper.SysRoleMapper;
import com.ihomy.mapper.SysUserMapper;
import com.ihomy.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 多家庭业务:公开家庭搜索、入家申请与审核(通知联动)、
 * 以及成员关系判断。加入方式与注册带邀请码互补。
 */
@Service
@RequiredArgsConstructor
public class MultiFamilyService {

    private final FamilyMapper familyMapper;
    private final FamilyApplyMapper familyApplyMapper;
    private final SysUserMapper sysUserMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final SysRoleMapper sysRoleMapper;
    private final NotificationService notificationService;

    /** 用户是否已是该家庭成员(查角色绑定) */
    public boolean isMember(Long userId, Long familyId) {
        if (userId == null || familyId == null) return false;
        LambdaQueryWrapper<SysUserRole> qw = new LambdaQueryWrapper<>();
        qw.eq(SysUserRole::getUserId, userId).eq(SysUserRole::getFamilyId, familyId);
        return sysUserRoleMapper.selectCount(qw) > 0;
    }

    /** 搜索公开家庭(最多 20 个):纯数字关键字按 ID 匹配,否则按名称模糊 */
    public List<Map<String, Object>> search(String keyword, Long currentUserId) {
        LambdaQueryWrapper<Family> qw = new LambdaQueryWrapper<>();
        qw.eq(Family::getIsPublic, 1).orderByAsc(Family::getId).last("LIMIT 20");
        if (StringUtils.hasText(keyword)) {
            String k = keyword.trim();
            if (k.matches("\\d+")) {
                qw.and(w -> w.eq(Family::getId, Long.valueOf(k))
                        .or().like(Family::getName, k));
            } else {
                qw.like(Family::getName, k);
            }
        }
        List<Family> list = familyMapper.selectList(qw);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Family f : list) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", f.getId());
            m.put("name", f.getName());
            m.put("coverText", f.getCoverText());
            m.put("coverSubtitle", f.getCoverSubtitle());
            m.put("description", f.getDescription());
            m.put("isDemo", f.getIsDemo());
            m.put("memberCount", sysUserRoleMapper.selectCount(
                    new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getFamilyId, f.getId())));
            m.put("joined", currentUserId != null && isMember(currentUserId, f.getId()));
            m.put("pending", currentUserId != null && hasPendingApply(currentUserId, f.getId()));
            result.add(m);
        }
        return result;
    }

    /** 是否存在待审核的入家申请 */
    public boolean hasPendingApply(Long userId, Long familyId) {
        LambdaQueryWrapper<FamilyApply> qw = new LambdaQueryWrapper<>();
        qw.eq(FamilyApply::getUserId, userId)
          .eq(FamilyApply::getFamilyId, familyId)
          .eq(FamilyApply::getStatus, 0);
        return familyApplyMapper.selectCount(qw) > 0;
    }

    /** 提交入家申请:家庭须公开且未加入、无重复申请;写入申请并通知该家庭全部家长 */
    @Transactional
    public void apply(Long userId, Long familyId, String message) {
        Family family = familyMapper.selectById(familyId);
        if (family == null) throw new BizException(ResultCode.NOT_FOUND);
        if (family.getIsPublic() == null || family.getIsPublic() != 1) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        if (isMember(userId, familyId)) throw new BizException(ResultCode.CONFLICT);
        if (hasPendingApply(userId, familyId)) throw new BizException(ResultCode.CONFLICT);

        FamilyApply apply = new FamilyApply();
        apply.setUserId(userId);
        apply.setFamilyId(familyId);
        apply.setMessage(message);
        apply.setStatus(0);
        familyApplyMapper.insert(apply);

        SysUser applicant = sysUserMapper.selectById(userId);
        String applicantName = applicant == null ? "用户" : (applicant.getNickname() != null ? applicant.getNickname() : applicant.getUsername());
        for (SysUserRole ur : listManagers(familyId)) {
            notificationService.create(ur.getUserId(), "system",
                    applicantName + " 申请加入家庭「" + family.getName() + "」", apply.getId(), "family_apply", familyId);
        }
    }

    /** 家庭内具备管理权限(OWNER)的角色绑定列表 */
    private List<SysUserRole> listManagers(Long familyId) {
        LambdaQueryWrapper<SysUserRole> qw = new LambdaQueryWrapper<>();
        qw.eq(SysUserRole::getFamilyId, familyId).in(SysUserRole::getRoleId, managerRoleIds());
        return sysUserRoleMapper.selectList(qw);
    }

    private List<Long> managerRoleIds() {
        List<Long> ids = new ArrayList<>();
        sysRoleMapper.selectList(null).forEach(r -> {
            if ("OWNER".equals(r.getRoleCode())) {
                ids.add(r.getId());
            }
        });
        return ids;
    }

    /** 申请列表:待审核在前,按提交时间倒序,附申请人昵称 */
    public List<Map<String, Object>> applyList(Long familyId) {
        LambdaQueryWrapper<FamilyApply> qw = new LambdaQueryWrapper<>();
        qw.eq(FamilyApply::getFamilyId, familyId)
          .orderByAsc(FamilyApply::getStatus)
          .orderByDesc(FamilyApply::getCreatedAt);
        List<Map<String, Object>> result = new ArrayList<>();
        for (FamilyApply a : familyApplyMapper.selectList(qw)) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", a.getId());
            m.put("message", a.getMessage());
            m.put("status", a.getStatus());
            m.put("createdAt", a.getCreatedAt());
            SysUser u = sysUserMapper.selectById(a.getUserId());
            m.put("applicantId", a.getUserId());
            m.put("applicantName", u == null ? "未知" : (u.getNickname() != null ? u.getNickname() : u.getUsername()));
            result.add(m);
        }
        return result;
    }

    /** 处理申请:approve 绑定 MEMBER 角色并通知通过,否则标记拒绝并通知 */
    @Transactional
    public void handleApply(Long applyId, Long familyId, Long handlerId, String action) {
        FamilyApply a = familyApplyMapper.selectById(applyId);
        if (a == null || !a.getFamilyId().equals(familyId)) throw new BizException(ResultCode.NOT_FOUND);
        if (a.getStatus() != 0) throw new BizException(ResultCode.CONFLICT);

        Family family = familyMapper.selectById(familyId);
        String familyName = family == null ? "家庭" : family.getName();

        if ("approve".equals(action)) {
            a.setStatus(1);
            a.setHandledBy(handlerId);
            a.setHandledAt(LocalDateTime.now());
            familyApplyMapper.updateById(a);

            if (!isMember(a.getUserId(), familyId)) {
                Long memberRoleId = sysRoleMapper.selectList(null).stream()
                        .filter(r -> "MEMBER".equals(r.getRoleCode()))
                        .findFirst().map(r -> r.getId()).orElse(null);
                if (memberRoleId != null) {
                    SysUserRole ur = new SysUserRole();
                    ur.setUserId(a.getUserId());
                    ur.setRoleId(memberRoleId);
                    ur.setFamilyId(familyId);
                    sysUserRoleMapper.insert(ur);
                }
            }
            notificationService.create(a.getUserId(), "system",
                    "你的入家申请已通过，欢迎加入「" + familyName + "」", applyId, "family_apply", familyId);
        } else {
            a.setStatus(2);
            a.setHandledBy(handlerId);
            a.setHandledAt(LocalDateTime.now());
            familyApplyMapper.updateById(a);
            notificationService.create(a.getUserId(), "system",
                    "你的入家申请被拒绝：家庭「" + familyName + "」", applyId, "family_apply", familyId);
        }
    }
}
