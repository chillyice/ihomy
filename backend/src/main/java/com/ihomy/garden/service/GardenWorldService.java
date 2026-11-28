package com.ihomy.garden.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ihomy.common.BizException;
import com.ihomy.common.ResultCode;
import com.ihomy.common.UserNames;
import com.ihomy.entity.SysUser;
import com.ihomy.entity.SysUserRole;
import com.ihomy.garden.GardenConst;
import com.ihomy.garden.core.Ulid;
import com.ihomy.garden.content.GardenContent;
import com.ihomy.garden.content.VenueDef;
import com.ihomy.garden.entity.GardenMember;
import com.ihomy.garden.entity.GardenWorld;
import com.ihomy.garden.mapper.GardenMemberMapper;
import com.ihomy.garden.mapper.GardenPlotMapper;
import com.ihomy.garden.mapper.GardenWalletMapper;
import com.ihomy.garden.mapper.GardenWorldMapper;
import com.ihomy.mapper.SysUserMapper;
import com.ihomy.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 世界引导(架构分册 §25.4/§30.3):首次触达花园时幂等建号——
 * 世界(id=family_id 恒等映射)→ 成员档案(player_uid ↔ sys_user_id 单向绑定)→
 * 已解锁场域的地块行(个人 3 盆/人 + 共享按阶段)→ 零元钱包。
 * 所有补行走 INSERT IGNORE,唯一键兜底并发;成员退出不删行(保留只读)。
 * 世界阶段默认 WORLD_STAGE,settings JSON 可带 stage 覆盖(隔离测试夹具,正式世界不写)。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GardenWorldService {

    private final GardenWorldMapper worldMapper;
    private final GardenMemberMapper memberMapper;
    private final GardenPlotMapper plotMapper;
    private final GardenWalletMapper walletMapper;
    private final GardenContent content;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final SysUserMapper sysUserMapper;
    private final GardenGrowthService growth;

    /** 幂等引导:返回最新世界行 */
    @Transactional
    public GardenWorld ensureWorld(Long familyId) {
        GardenWorld world = worldMapper.selectById(familyId);
        if (world == null) {
            GardenWorld created = new GardenWorld();
            created.setId(familyId);
            created.setWorldId(Ulid.next());
            created.setKind("FAMILY");
            created.setOrigin("IHOMY");
            created.setAuthority("SERVER");
            created.setExternalRef("ihomy:family:" + familyId);
            created.setHostEpoch(0);
            created.setContentVersion(content.contentVersion());
            created.setSettings("{\"cycle_start_at\":\"" + LocalDate.now() + "\"}");
            try {
                worldMapper.insert(created);
                log.info("[garden] world created familyId={} worldId={}", familyId, created.getWorldId());
            } catch (DuplicateKeyException e) {
                // 并发建号:另一请求先建成,读回即可
            }
        }
        GardenWorld current = worldMapper.selectById(familyId);
        ensureMembersAndPlots(familyId, stageOf(current));
        return current;
    }

    /** 成员 / 地块 / 钱包补齐:任一成员缺行都补(新成员加入即获得该阶段个人地块) */
    private void ensureMembersAndPlots(Long familyId, int stage) {
        List<SysUserRole> roles = sysUserRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getFamilyId, familyId));
        if (roles.isEmpty()) {
            return;
        }
        List<GardenMember> existing = memberMapper.selectList(new LambdaQueryWrapper<GardenMember>()
                .eq(GardenMember::getFamilyId, familyId));
        Map<Long, String> bound = new HashMap<>();
        for (GardenMember m : existing) {
            if (m.getSysUserId() != null) {
                bound.put(m.getSysUserId(), m.getPlayerUid());
            }
        }
        for (SysUserRole role : roles) {
            GardenMember member = ensureMember(familyId, role.getUserId(), bound.get(role.getUserId()));
            ensurePersonalPlots(familyId, member.getPlayerUid(), stage);
            walletMapper.insertIgnore(familyId, member.getPlayerUid());
        }
        ensureSharedPlots(familyId, stage);
    }

    private GardenMember ensureMember(Long familyId, Long sysUserId, String knownUid) {
        GardenMember member = memberMapper.selectOne(new LambdaQueryWrapper<GardenMember>()
                .eq(GardenMember::getFamilyId, familyId)
                .eq(GardenMember::getSysUserId, sysUserId));
        if (member != null) {
            return member;
        }
        if (knownUid != null) {
            GardenMember byUid = memberMapper.selectOne(new LambdaQueryWrapper<GardenMember>()
                    .eq(GardenMember::getFamilyId, familyId)
                    .eq(GardenMember::getPlayerUid, knownUid));
            if (byUid != null) {
                return byUid;
            }
        }
        memberMapper.insertIgnore(familyId, Ulid.next(), sysUserId);
        return memberMapper.selectOne(new LambdaQueryWrapper<GardenMember>()
                .eq(GardenMember::getFamilyId, familyId)
                .eq(GardenMember::getSysUserId, sysUserId));
    }

    /** 个人地块:每个成员各一套已解锁个人场域的格子(最小闭环阶段 = 窗台 0-2 号盆) */
    private void ensurePersonalPlots(Long familyId, String playerUid, int stage) {
        for (VenueDef venue : content.venues()) {
            if (!GardenConst.isPersonalVenue(venue.code())) {
                continue;
            }
            venue.slots().stream()
                    .filter(s -> s.unlockStage() <= stage)
                    .forEach(s -> plotMapper.insertIgnore(familyId, playerUid, venue.code(), s.slotNo(),
                            venue.soilType(), playerUid));
        }
    }

    /** 共享地块:全家庭一份(owner_uid='') */
    private void ensureSharedPlots(Long familyId, int stage) {
        for (VenueDef venue : content.venues()) {
            if (!GardenConst.isSharedVenue(venue.code())) {
                continue;
            }
            venue.slots().stream()
                    .filter(s -> s.unlockStage() <= stage)
                    .forEach(s -> plotMapper.insertIgnore(familyId, GardenConst.OWNER_SHARED, venue.code(),
                            s.slotNo(), venue.soilType(), GardenConst.OWNER_SHARED));
        }
    }

    /**
     * 世界阶段:settings JSON 的 stage 夹具优先(隔离测试夹具,正式世界不写),
     * 否则 = 1 + STAGE_ADVANCE 次数、受节气日历上限约束(推导在 GardenGrowthService,详细设计 §2.1)。
     */
    public int stageOf(GardenWorld world) {
        return growth.stageOf(world == null ? null : world.getId(), world == null ? null : world.getSettings());
    }

    /** 解析当前登录成员在世界内的身份(player_uid);未加入家庭一律 403 */
    public GardenMember requireMember(Long familyId, Long userId) {
        GardenMember member = memberMapper.selectOne(new LambdaQueryWrapper<GardenMember>()
                .eq(GardenMember::getFamilyId, familyId)
                .eq(GardenMember::getSysUserId, userId));
        if (member == null) {
            // 可能是刚加入家庭的成员:补一次引导再取
            ensureWorld(familyId);
            member = memberMapper.selectOne(new LambdaQueryWrapper<GardenMember>()
                    .eq(GardenMember::getFamilyId, familyId)
                    .eq(GardenMember::getSysUserId, userId));
        }
        if (member == null) {
            throw new BizException(ResultCode.FORBIDDEN, "先加入家庭,才能走进花园");
        }
        return member;
    }

    /** playerUid → 成员昵称(写接口 403 文案「这是 {昵称} 的房间」用) */
    public Map<String, String> nicknames(Long familyId) {
        List<GardenMember> members = memberMapper.selectList(new LambdaQueryWrapper<GardenMember>()
                .eq(GardenMember::getFamilyId, familyId));
        Map<String, String> out = new HashMap<>();
        List<Long> sysUserIds = members.stream().map(GardenMember::getSysUserId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, SysUser> users = sysUserIds.isEmpty() ? Map.of()
                : sysUserMapper.selectBatchIds(sysUserIds).stream()
                .collect(java.util.stream.Collectors.toMap(SysUser::getId, u -> u));
        for (GardenMember m : members) {
            SysUser u = m.getSysUserId() == null ? null : users.get(m.getSysUserId());
            out.put(m.getPlayerUid(), UserNames.of(u));
        }
        return out;
    }
}
