package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ihomy.common.BizException;
import com.ihomy.common.DictConst;
import com.ihomy.common.ResultCode;
import com.ihomy.entity.InvitationCode;
import com.ihomy.entity.SysRole;
import com.ihomy.entity.SysUser;
import com.ihomy.entity.SysUserRole;
import com.ihomy.mapper.InvitationCodeMapper;
import com.ihomy.mapper.SysRoleMapper;
import com.ihomy.mapper.SysUserMapper;
import com.ihomy.mapper.SysUserRoleMapper;
import com.ihomy.websocket.ChatWebSocketHandler;
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
    private final ChatWebSocketHandler wsHandler;
    private final com.ihomy.security.SecurityHelper securityHelper;

    /** 家庭成员列表:SQL 返回角色/标签/状态偏好,在线与否取 WS 连接态(成员数个位,循环可接受) */
    public List<Map<String, Object>> listMembers(Long familyId) {
        List<Map<String, Object>> rows = sysUserMapper.selectMembersByFamily(familyId);
        for (Map<String, Object> row : rows) {
            Object id = row.get("id");
            row.put("online", id instanceof Number n && wsHandler.isOnline(n.longValue()));
        }
        return rows;
    }

    /** 本人切换在线状态偏好(ONLINE/BUSY/AWAY):落库并广播给同家庭在线成员;离线态只由连接态决定 */
    public void setPresence(Long userId, Long familyId, String status) {
        if (!DictConst.PRESENCE_ONLINE.equals(status) && !DictConst.PRESENCE_BUSY.equals(status)
                && !DictConst.PRESENCE_AWAY.equals(status)) {
            throw new BizException(ResultCode.BAD_REQUEST);
        }
        SysUser patch = new SysUser();
        patch.setId(userId);
        patch.setPresenceStatus(status);
        sysUserMapper.updateById(patch);
        if (familyId != null) {
            wsHandler.broadcastPresence(familyId, userId, status);
        }
    }

    /** 修改成员角色:目标须在本家庭有角色绑定,先删旧绑定再插新角色 */
    @Transactional
    public void setRole(Long operatorFamilyId, Long targetUserId, String roleCode) {
        SysRole role = findRole(roleCode);
        if (targetUserId == null || operatorFamilyId == null) throw new BizException(ResultCode.BAD_REQUEST);
        if (!isMember(operatorFamilyId, targetUserId)) {
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

    /** 移出成员:禁止移出自己;删角色绑定后,主/默认家庭仍指向本家时改指他仍加入的家庭(没有才清空) */
    @Transactional
    public void removeMember(Long operatorUserId, Long operatorFamilyId, Long targetUserId) {
        if (operatorUserId.equals(targetUserId)) throw new BizException(ResultCode.BAD_REQUEST);
        if (operatorFamilyId == null || targetUserId == null || !isMember(operatorFamilyId, targetUserId)) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        SysUser target = sysUserMapper.selectById(targetUserId);
        if (target == null) throw new BizException(ResultCode.NOT_FOUND);
        LambdaQueryWrapper<SysUserRole> qw = new LambdaQueryWrapper<>();
        qw.eq(SysUserRole::getUserId, targetUserId).eq(SysUserRole::getFamilyId, operatorFamilyId);
        sysUserRoleMapper.delete(qw);
        // 可能还在别的家庭:主家庭优先落到剩下的绑定,一个都没有才置空
        Long next = firstRemainingFamily(targetUserId);
        if (operatorFamilyId.equals(target.getFamilyId())) {
            // null 值必须用 wrapper 显式 SET:updateById 会跳过 null 字段,清空不生效
            sysUserMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                    .eq(SysUser::getId, targetUserId)
                    .set(SysUser::getFamilyId, next));
            securityHelper.invalidateUser(targetUserId);
        }
        if (operatorFamilyId.equals(target.getDefaultFamilyId())) {
            sysUserMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                    .eq(SysUser::getId, targetUserId)
                    .set(SysUser::getDefaultFamilyId, next));
        }
    }

    /** 目标用户是否在指定家庭有角色绑定(与成员列表同一口径) */
    private boolean isMember(Long familyId, Long userId) {
        return sysUserRoleMapper.selectCount(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, userId)
                .eq(SysUserRole::getFamilyId, familyId)) > 0;
    }

    /** 用户剩余角色绑定里的第一个家庭(任意一个即可,用于主家庭回退),没有返回 null */
    private Long firstRemainingFamily(Long userId) {
        SysUserRole row = sysUserRoleMapper.selectOne(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, userId)
                .orderByAsc(SysUserRole::getFamilyId)
                .last("LIMIT 1"));
        return row == null ? null : row.getFamilyId();
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
        ic.setStatus(DictConst.INVITE_UNUSED);
        ic.setCreatedBy(creatorId);
        invitationCodeMapper.insert(ic);
        return Map.of("code", ic.getCode(), "expiresAt", ic.getExpiresAt());
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
        qw.eq(SysRole::getRoleCode, roleCode).eq(SysRole::getStatus, DictConst.ROLE_ENABLED);
        SysRole role = sysRoleMapper.selectOne(qw);
        if (role == null) throw new BizException(ResultCode.BAD_REQUEST);
        return role;
    }
}