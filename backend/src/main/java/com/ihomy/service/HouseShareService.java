package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ihomy.common.BizException;
import com.ihomy.common.ResultCode;
import com.ihomy.entity.Family;
import com.ihomy.entity.Furniture;
import com.ihomy.entity.House;
import com.ihomy.entity.HouseShare;
import com.ihomy.entity.Room;
import com.ihomy.entity.SysUserRole;
import com.ihomy.mapper.FamilyMapper;
import com.ihomy.mapper.FurnitureMapper;
import com.ihomy.mapper.HouseMapper;
import com.ihomy.mapper.HouseShareMapper;
import com.ihomy.mapper.RoomMapper;
import com.ihomy.mapper.SysUserRoleMapper;
import com.ihomy.service.access.ViewerScope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 房子协作共享(物品定位):判定「这所房子我家能不能看/能不能改」的唯一来源,以及共享授权的读写。
 *
 * <p>口径:
 * <ul>
 *   <li>共享单元是房子:房间/家具/物品按所属房子继承权限;</li>
 *   <li>本家房子:本家成员可读写(与原行为一致);</li>
 *   <li>共享房子:被授权家庭成员可读写房子内的房间/家具/物品与户型图,
 *       但删房子与调整共享只归房主家庭家长;</li>
 *   <li>授权对象必须是授权人自己所属的其他家庭,避免按家庭 id 越权共享;</li>
 *   <li>未挂进房子的物品(家具库/散放)没有共享语义,仍只归本家。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class HouseShareService {

    private final HouseMapper houseMapper;
    private final RoomMapper roomMapper;
    private final FurnitureMapper furnitureMapper;
    private final HouseShareMapper houseShareMapper;
    private final FamilyMapper familyMapper;
    private final SysUserRoleMapper sysUserRoleMapper;

    // ---------- 读取 ----------

    /** 共享给我家的房子 id(不含本家) */
    public List<Long> sharedHouseIds(Long familyId) {
        if (familyId == null) return List.of();
        return houseShareMapper.selectList(new LambdaQueryWrapper<HouseShare>()
                        .eq(HouseShare::getGranteeFamilyId, familyId))
                .stream().map(HouseShare::getHouseId).distinct().toList();
    }

    /** 共享给我家的房子:按排序追加在本家房子之后,并带上房主家庭(前端展示「共享自 X」) */
    public List<House> sharedHouses(Long familyId) {
        List<Long> ids = sharedHouseIds(familyId);
        if (ids.isEmpty()) return List.of();
        List<House> houses = houseMapper.selectBatchIds(ids).stream()
                .sorted(Comparator.comparing(House::getSortOrder, Comparator.nullsFirst(Integer::compareTo))
                        .thenComparing(House::getId))
                .collect(Collectors.toList());
        Map<Long, String> names = familyNames(houses.stream().map(House::getFamilyId).collect(Collectors.toSet()));
        for (House h : houses) {
            h.setShared(true);
            h.setOwnerFamilyId(h.getFamilyId());
            h.setOwnerFamilyName(names.get(h.getFamilyId()));
        }
        return houses;
    }

    /** 读:本家房子,或被共享给我家的房子;其余 404(不泄露存在性) */
    public House requireReadable(Long houseId, ViewerScope viewer) {
        House h = houseId == null ? null : houseMapper.selectById(houseId);
        if (h == null || !canRead(h, viewer)) throw new BizException(ResultCode.NOT_FOUND);
        return h;
    }

    /** 写:本家房子任意成员;共享房子被授权家庭任意成员(删房子/改共享另见 {@link #requireOwnerFamily}) */
    public House requireWritable(Long houseId, ViewerScope viewer) {
        House h = houseId == null ? null : houseMapper.selectById(houseId);
        if (h == null || !canWrite(h.getId(), h.getFamilyId(), viewer)) throw new BizException(ResultCode.NOT_FOUND);
        return h;
    }

    public boolean canRead(House h, ViewerScope viewer) {
        if (h == null || viewer.familyId() == null) return false;
        return h.getFamilyId().equals(viewer.familyId()) || granted(h.getId(), viewer.familyId());
    }

    public boolean canWrite(Long houseId, Long houseFamilyId, ViewerScope viewer) {
        if (houseId == null || houseFamilyId == null || viewer.familyId() == null) return false;
        return houseFamilyId.equals(viewer.familyId()) || granted(houseId, viewer.familyId());
    }

    /** 房子里的子行(房间/家具/物品)按所属房子判定;拿不到房子(家具库/散放物品)时返回 null */
    public House houseOfRoom(Long roomId) {
        Room r = roomId == null ? null : roomMapper.selectById(roomId);
        return r == null || r.getHouseId() == null ? null : houseMapper.selectById(r.getHouseId());
    }

    public House houseOfFurniture(Long furnitureId) {
        Furniture f = furnitureId == null ? null : furnitureMapper.selectById(furnitureId);
        return f == null ? null : houseOfRoom(f.getRoomId());
    }

    /** 房间/家具两个锚点都可能给(物品页会同时带),优先家具,再回退房间 */
    public House houseOf(Long furnitureId, Long roomId) {
        House h = houseOfFurniture(furnitureId);
        return h != null ? h : houseOfRoom(roomId);
    }

    // ---------- 共享授权 ----------

    /** 房子的共享清单(房主家庭可查看):被共享家庭 + 授权时间 */
    public List<Map<String, Object>> listShares(Long houseId) {
        List<HouseShare> shares = houseShareMapper.selectList(new LambdaQueryWrapper<HouseShare>()
                .eq(HouseShare::getHouseId, houseId)
                .orderByAsc(HouseShare::getId));
        if (shares.isEmpty()) return List.of();
        Map<Long, String> names = familyNames(shares.stream()
                .map(HouseShare::getGranteeFamilyId).collect(Collectors.toSet()));
        List<Map<String, Object>> result = new ArrayList<>();
        for (HouseShare s : shares) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("familyId", s.getGranteeFamilyId());
            m.put("familyName", names.getOrDefault(s.getGranteeFamilyId(), ""));
            m.put("createdAt", s.getCreatedAt());
            result.add(m);
        }
        return result;
    }

    /**
     * 全量替换一所房子的共享清单(家长操作):先删后插,幂等。
     * 授权对象必须是授权人自己所属的其他家庭;被共享家庭的一方始终是「被授权方」,不能反向授权自己家。
     */
    @Transactional
    public void replaceShares(Long houseId, ViewerScope viewer, Long userId, List<Long> granteeFamilyIds) {
        requireOwnerFamily(houseId, viewer);
        Set<Long> target = new LinkedHashSet<>();
        if (granteeFamilyIds != null) {
            for (Long id : granteeFamilyIds) if (id != null) target.add(id);
        }
        target.remove(viewer.familyId());
        if (!target.isEmpty()) {
            Set<Long> mine = memberFamilyIds(userId);
            for (Long id : target) {
                if (!mine.contains(id)) {
                    throw new BizException(ResultCode.FORBIDDEN, "只能共享给你自己所属的家庭");
                }
            }
        }
        houseShareMapper.delete(new LambdaQueryWrapper<HouseShare>().eq(HouseShare::getHouseId, houseId));
        for (Long id : target) {
            HouseShare s = new HouseShare();
            s.setHouseId(houseId);
            s.setGranteeFamilyId(id);
            s.setCreatedBy(userId);
            houseShareMapper.insert(s);
        }
    }

    /** 删房子/调整共享的准入:房主家庭 + 家长 */
    public House requireOwnerFamily(Long houseId, ViewerScope viewer) {
        House h = requireOwn(houseId, viewer);
        if (!viewer.owner()) throw new BizException(ResultCode.FORBIDDEN, "只有家长可以调整房子共享");
        return h;
    }

    /** 房主家庭成员(用于查看共享清单等只读场景;共享家庭一律 404) */
    public House requireOwn(Long houseId, ViewerScope viewer) {
        House h = houseId == null ? null : houseMapper.selectById(houseId);
        if (h == null || viewer.familyId() == null || !h.getFamilyId().equals(viewer.familyId())) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        return h;
    }

    /** 撤销单个家庭的共享(家长操作,幂等) */
    @Transactional
    public void revokeShare(Long houseId, Long granteeFamilyId, ViewerScope viewer) {
        requireOwnerFamily(houseId, viewer);
        if (granteeFamilyId == null) return;
        houseShareMapper.delete(new LambdaQueryWrapper<HouseShare>()
                .eq(HouseShare::getHouseId, houseId)
                .eq(HouseShare::getGranteeFamilyId, granteeFamilyId));
    }

    /** 删房子前清掉授权行(房子没了共享自然失效) */
    public void purge(Long houseId) {
        if (houseId == null) return;
        houseShareMapper.delete(new LambdaQueryWrapper<HouseShare>().eq(HouseShare::getHouseId, houseId));
    }

    /** 房子是否已共享给某家庭 */
    private boolean granted(Long houseId, Long familyId) {
        if (houseId == null || familyId == null) return false;
        return houseShareMapper.selectCount(new LambdaQueryWrapper<HouseShare>()
                .eq(HouseShare::getHouseId, houseId)
                .eq(HouseShare::getGranteeFamilyId, familyId)) > 0;
    }

    private Set<Long> memberFamilyIds(Long userId) {
        if (userId == null) return Set.of();
        return sysUserRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getUserId, userId))
                .stream().map(SysUserRole::getFamilyId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
    }

    private Map<Long, String> familyNames(Set<Long> familyIds) {
        Set<Long> ids = familyIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) return Map.of();
        return familyMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Family::getId, f -> f.getName() == null ? "" : f.getName(), (a, b) -> a));
    }
}
