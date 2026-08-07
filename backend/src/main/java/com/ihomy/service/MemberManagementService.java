package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ihomy.common.BizException;
import com.ihomy.common.ResultCode;
import com.ihomy.entity.InvitationCode;
import com.ihomy.entity.SysRole;
import com.ihomy.entity.SysUser;
import com.ihomy.entity.SysUserRole;
import com.ihomy.mapper.InvitationCodeMapper;
import com.ihomy.mapper.SysRoleMapper;
import com.ihomy.mapper.SysUserMapper;
import com.ihomy.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 成员管理业务:家庭内改角色/移出成员,以及邀请码的生成、核销、列表。
 * 移出仅解绑家庭角色关系,不删除用户账号。
 */
@Service
@RequiredArgsConstructor
public class MemberManagementService {

    private final SysUserMapper sysUserMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final SysRoleMapper sysRoleMapper;
    private final InvitationCodeMapper invitationCodeMapper;

    /** 修改成员角色:目标须属于操作者家庭,先删旧绑定再插新角色 */
    public void setRole(Long operatorFamilyId, Long targetUserId, String roleCode) {
        SysRole role = findRole(roleCode);
        if (targetUserId == null || operatorFamilyId == null) throw new BizException(ResultCode.BAD_REQUEST);
        SysUser target = sysUserMapper.selectById(targetUserId);
        if (target == null || !operatorFamilyId.equals(target.getFamilyId())) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        LambdaQueryWrapper<SysUserRole> qw = new LambdaQueryWrapper<>();
        qw.eq(SysUserRole::getUserId, targetUserId).eq(SysUserRole::getFamilyId, operatorFamilyId);
        sysUserRoleMapper.delete(qw);
        SysUserRole ur = new SysUserRole();
        ur.setUserId(targetUserId);
        ur.setRoleId(role.getId());
        ur.setFamilyId(operatorFamilyId);
        sysUserRoleMapper.insert(ur);
    }

    /** 移出成员:禁止移出自己;若该家庭是目标主家庭则清空其 family_id */
    public void removeMember(Long operatorUserId, Long operatorFamilyId, Long targetUserId) {
        if (operatorUserId.equals(targetUserId)) throw new BizException(ResultCode.BAD_REQUEST);
        SysUser target = sysUserMapper.selectById(targetUserId);
        if (target == null || !operatorFamilyId.equals(target.getFamilyId())) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        LambdaQueryWrapper<SysUserRole> qw = new LambdaQueryWrapper<>();
        qw.eq(SysUserRole::getUserId, targetUserId).eq(SysUserRole::getFamilyId, operatorFamilyId);
        sysUserRoleMapper.delete(qw);
        if (operatorFamilyId.equals(target.getFamilyId())) {
            target.setFamilyId(null);
            sysUserMapper.updateById(target);
        }
    }

    /** 生成邀请码:12 位随机码,预设角色(默认 MEMBER),7 天有效、最多使用 10 次 */
    public Map<String, Object> createInvite(Long familyId, Long creatorId, String roleCode) {
        SysRole role = findRole(roleCode == null ? "MEMBER" : roleCode);
        InvitationCode ic = new InvitationCode();
        ic.setCode(UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        ic.setFamilyId(familyId);
        ic.setPresetRoleId(role.getId());
        ic.setMaxUses(10);
        ic.setUsedCount(0);
        ic.setExpiresAt(LocalDateTime.now().plusDays(7));
        ic.setStatus(1);
        ic.setCreatedBy(creatorId);
        invitationCodeMapper.insert(ic);
        return Map.of("code", ic.getCode(), "expiresAt", ic.getExpiresAt());
    }

    /** 核销邀请码:校验有效性与未加入过该家庭后绑定角色 */
    @Transactional
    public SysUser acceptInvite(SysUser user, String code) {
        if (code == null || code.isBlank()) throw new BizException(ResultCode.BAD_REQUEST);
        InvitationCode ic = invitationCodeMapper.selectByCode(code);
        if (ic == null || ic.getStatus() != 1) throw new BizException(ResultCode.NOT_FOUND);
        if (ic.getExpiresAt() != null && ic.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BizException(ResultCode.CONFLICT);
        }
        if (ic.getUsedCount() >= ic.getMaxUses()) throw new BizException(ResultCode.CONFLICT);
        if (user.getFamilyId() != null && !user.getFamilyId().equals(ic.getFamilyId())) {
            throw new BizException(ResultCode.CONFLICT);
        }
        ic.setUsedCount(ic.getUsedCount() + 1);
        invitationCodeMapper.updateById(ic);

        LambdaQueryWrapper<SysUserRole> qw = new LambdaQueryWrapper<>();
        qw.eq(SysUserRole::getUserId, user.getId()).eq(SysUserRole::getFamilyId, ic.getFamilyId());
        if (sysUserRoleMapper.selectCount(qw) == 0) {
            SysUserRole ur = new SysUserRole();
            ur.setUserId(user.getId());
            ur.setRoleId(ic.getPresetRoleId());
            ur.setFamilyId(ic.getFamilyId());
            sysUserRoleMapper.insert(ur);
        }
        user.setFamilyId(ic.getFamilyId());
        sysUserMapper.updateById(user);
        return user;
    }

    /** 家庭已生成的邀请码列表(倒序) */
    public List<InvitationCode> inviteList(Long familyId) {
        LambdaQueryWrapper<InvitationCode> qw = new LambdaQueryWrapper<>();
        qw.eq(InvitationCode::getFamilyId, familyId).orderByDesc(InvitationCode::getId);
        return invitationCodeMapper.selectList(qw);
    }

    /** 按角色码查启用角色,不存在抛 400 */
    private SysRole findRole(String roleCode) {
        LambdaQueryWrapper<SysRole> qw = new LambdaQueryWrapper<>();
        qw.eq(SysRole::getRoleCode, roleCode).eq(SysRole::getStatus, 1);
        SysRole role = sysRoleMapper.selectOne(qw);
        if (role == null) throw new BizException(ResultCode.BAD_REQUEST);
        return role;
    }
}