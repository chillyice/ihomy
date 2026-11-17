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

    /** 修改成员角色:目标须属于操作者家庭,先删旧绑定再插新角色 */
    @Transactional
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

    /** 移出成员:禁止移出自己;删角色绑定并清空其指向该家庭的主/默认家庭(授权残留) */
    @Transactional
    public void removeMember(Long operatorUserId, Long operatorFamilyId, Long targetUserId) {
        if (operatorUserId.equals(targetUserId)) throw new BizException(ResultCode.BAD_REQUEST);
        SysUser target = sysUserMapper.selectById(targetUserId);
        // 走到这里必有 operatorFamilyId == target.familyId,故清空主家庭无条件成立
        if (target == null || !operatorFamilyId.equals(target.getFamilyId())) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        LambdaQueryWrapper<SysUserRole> qw = new LambdaQueryWrapper<>();
        qw.eq(SysUserRole::getUserId, targetUserId).eq(SysUserRole::getFamilyId, operatorFamilyId);
        sysUserRoleMapper.delete(qw);
        // null 值必须用 wrapper 显式 SET:updateById 会跳过 null 字段,清空不生效
        sysUserMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, targetUserId)
                .set(SysUser::getFamilyId, null)
                // 默认家庭仍是别的家庭时保留(他还属于那个家庭),同为被移出家庭才清
                .set(operatorFamilyId.equals(target.getDefaultFamilyId()), SysUser::getDefaultFamilyId, null));
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