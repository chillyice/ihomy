package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.ihomy.common.BizException;
import com.ihomy.common.DictConst;
import com.ihomy.common.ResultCode;
import com.ihomy.dto.FurnitureDTO;
import com.ihomy.dto.HouseDTO;
import com.ihomy.dto.ItemDTO;
import com.ihomy.dto.RoomDTO;
import com.ihomy.entity.Furniture;
import com.ihomy.entity.House;
import com.ihomy.entity.Item;
import com.ihomy.entity.Room;
import com.ihomy.mapper.FurnitureMapper;
import com.ihomy.mapper.HouseMapper;
import com.ihomy.mapper.ItemMapper;
import com.ihomy.mapper.RoomMapper;
import com.ihomy.service.access.ContentAccessService;
import com.ihomy.service.access.ContentKind;
import com.ihomy.service.access.ViewerScope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 物品定位业务(V5.0,1期):房子/房间/家具/物品 CRUD + 跨级搜索。
 * 五级粒度:家(family_id) > 房子 > 房间 > 家具 > 位置。
 *
 * <p>权限:所有读写都按「房子」判定({@link HouseShareService}),本家房子的人可见可改,
 * 被共享房子的家庭成员同样可增删改查;房间/家具/物品的行仍落房主家庭,
 * 这样户型图、食材、统计口径不会因协作分叉。没挂进房子的物品(家具库/散放)只归本家。
 */
@Service
@RequiredArgsConstructor
public class ItemService {

    private final HouseMapper houseMapper;
    private final RoomMapper roomMapper;
    private final FurnitureMapper furnitureMapper;
    private final ItemMapper itemMapper;
    private final ContentAccessService contentAccess;
    private final HouseShareService houseShareService;
    private final ObjectMapper mapper = new ObjectMapper();

    // ---------- 房子 ----------

    /** 房子列表:本家在前,共享给我家的追加在后(带房主家庭名,前端标「共享自 X」) */
    public List<House> houseList(ViewerScope viewer) {
        Long familyId = viewer.familyId();
        if (familyId == null) return List.of();
        List<House> houses = new ArrayList<>(houseMapper.selectList(new LambdaQueryWrapper<House>()
                .eq(House::getFamilyId, familyId)
                .orderByAsc(House::getSortOrder)
                .orderByAsc(House::getId)));
        houses.addAll(houseShareService.sharedHouses(familyId));
        return houses;
    }

    public House houseCreate(ViewerScope viewer, HouseDTO dto) {
        requireText(dto.getName(), "请填写房子名");
        House h = new House();
        h.setFamilyId(viewer.familyId());
        h.setName(dto.getName());
        h.setAddress(dto.getAddress());
        h.setFloorPlans(dto.getFloorPlans());
        h.setSortOrder(dto.getSortOrder() == null ? 0 : dto.getSortOrder());
        h.setCreatedBy(viewer.userId());
        houseMapper.insert(h);
        return h;
    }

    /** 改房子:本家成员与共享家庭都可改(协作维护房名/地址/户型图) */
    public void houseUpdate(Long id, ViewerScope viewer, HouseDTO dto) {
        House h = houseShareService.requireWritable(id, viewer);
        if (dto.getName() != null) h.setName(dto.getName());
        if (dto.getAddress() != null) h.setAddress(dto.getAddress());
        if (dto.getFloorPlans() != null) h.setFloorPlans(dto.getFloorPlans());
        if (dto.getSortOrder() != null) h.setSortOrder(dto.getSortOrder());
        houseMapper.updateById(h);
    }

    /** 删房子只归房主家庭(共享家庭看不到删除入口,直接调也 404) */
    @Transactional
    public void houseDelete(Long id, ViewerScope viewer) {
        House h = houseMapper.selectById(id);
        if (h == null || viewer.familyId() == null || !h.getFamilyId().equals(viewer.familyId())) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        if (roomMapper.selectCount(new LambdaQueryWrapper<Room>()
                .eq(Room::getHouseId, id)) > 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "该房子下还有房间,请先删除或转移房间");
        }
        houseShareService.purge(id);
        houseMapper.deleteById(id);
    }

    // ---------- 房间 ----------

    public List<Room> roomList(ViewerScope viewer, Long houseId) {
        if (houseId != null) {
            houseShareService.requireReadable(houseId, viewer);
            return roomMapper.selectList(new LambdaQueryWrapper<Room>()
                    .eq(Room::getHouseId, houseId)
                    .orderByAsc(Room::getFloor)
                    .orderByAsc(Room::getSortOrder)
                    .orderByAsc(Room::getId));
        }
        List<Long> houseIds = accessibleHouseIds(viewer);
        if (houseIds.isEmpty()) return List.of();
        return roomMapper.selectList(new LambdaQueryWrapper<Room>()
                .in(Room::getHouseId, houseIds)
                .orderByAsc(Room::getFloor)
                .orderByAsc(Room::getSortOrder)
                .orderByAsc(Room::getId));
    }

    public Room roomCreate(ViewerScope viewer, RoomDTO dto) {
        requireText(dto.getName(), "请填写房间名");
        if (dto.getHouseId() == null) throw new BizException(ResultCode.BAD_REQUEST, "请选择所在房子");
        House house = houseShareService.requireWritable(dto.getHouseId(), viewer);
        Room r = new Room();
        r.setFamilyId(house.getFamilyId());
        r.setHouseId(dto.getHouseId());
        r.setName(dto.getName());
        r.setFloor(dto.getFloor() == null ? 1 : dto.getFloor());
        r.setSortOrder(dto.getSortOrder() == null ? 0 : dto.getSortOrder());
        r.setNote(dto.getNote());
        r.setGeometry(dto.getGeometry());
        r.setCreatedBy(viewer.userId());
        roomMapper.insert(r);
        return r;
    }

    public void roomUpdate(Long id, ViewerScope viewer, RoomDTO dto) {
        Room r = requireRoom(id);
        requireRoomWritable(r, viewer);
        if (dto.getName() != null) r.setName(dto.getName());
        if (dto.getHouseId() != null) {
            House house = houseShareService.requireWritable(dto.getHouseId(), viewer);
            r.setHouseId(dto.getHouseId());
            r.setFamilyId(house.getFamilyId());
        }
        if (dto.getFloor() != null) r.setFloor(dto.getFloor());
        if (dto.getSortOrder() != null) r.setSortOrder(dto.getSortOrder());
        r.setNote(dto.getNote());
        if (dto.getGeometry() != null) r.setGeometry(dto.getGeometry());
        roomMapper.updateById(r);
    }

    @Transactional
    public void roomDelete(Long id, ViewerScope viewer) {
        Room r = requireRoom(id);
        requireRoomWritable(r, viewer);
        // 家具进家具库(room_id 置空),与物品绑定不动;散放物品(room_id 指向本房间)同步清空
        furnitureMapper.update(null, new LambdaUpdateWrapper<Furniture>()
                .eq(Furniture::getFamilyId, r.getFamilyId())
                .eq(Furniture::getRoomId, id)
                .set(Furniture::getRoomId, null));
        itemMapper.update(null, new LambdaUpdateWrapper<Item>()
                .eq(Item::getFamilyId, r.getFamilyId())
                .eq(Item::getRoomId, id)
                .set(Item::getRoomId, null));
        roomMapper.deleteById(id);
    }

    // ---------- 家具 ----------

    /** 家具列表:按房间过滤时校验房间所属房子;不带房间=本家全部(含家具库)+ 共享房子里的家具 */
    public List<Furniture> furnitureList(ViewerScope viewer, Long roomId) {
        if (roomId != null) {
            requireRoomReadable(requireRoom(roomId), viewer);
            return furnitureMapper.selectList(new LambdaQueryWrapper<Furniture>()
                    .eq(Furniture::getRoomId, roomId)
                    .orderByAsc(Furniture::getId));
        }
        if (viewer.familyId() == null) return List.of();
        List<Furniture> result = new ArrayList<>(furnitureMapper.selectList(new LambdaQueryWrapper<Furniture>()
                .eq(Furniture::getFamilyId, viewer.familyId())
                .orderByAsc(Furniture::getId)));
        List<Long> sharedHouseIds = houseShareService.sharedHouseIds(viewer.familyId());
        if (!sharedHouseIds.isEmpty()) {
            List<Long> sharedRoomIds = roomMapper.selectList(new LambdaQueryWrapper<Room>()
                            .in(Room::getHouseId, sharedHouseIds))
                    .stream().map(Room::getId).toList();
            if (!sharedRoomIds.isEmpty()) {
                result.addAll(furnitureMapper.selectList(new LambdaQueryWrapper<Furniture>()
                        .in(Furniture::getRoomId, sharedRoomIds)
                        .orderByAsc(Furniture::getId)));
            }
        }
        return result;
    }

    /**
     * 家庭默认冰箱(食材默认存放位置):按类型/名称找已有冰箱,无则创建。
     * 优先本家已摆放的冰箱,其次本家家具库,再退到共享房子里的冰箱(协作做饭不重复建档),
     * 都没有才在本家创建(有厨房房间则放入厨房,否则进家具库)。
     */
    public Furniture getDefaultFridge(ViewerScope viewer) {
        Long familyId = viewer.familyId();
        List<Furniture> fridges = furnitureMapper.selectList(new LambdaQueryWrapper<Furniture>()
                .eq(Furniture::getFamilyId, familyId)
                .and(w -> w.eq(Furniture::getType, "冰箱").or().like(Furniture::getName, "冰箱"))
                .orderByAsc(Furniture::getId));
        Furniture fridge = fridges.stream()
                .filter(f -> f.getRoomId() != null)
                .findFirst()
                .orElse(fridges.isEmpty() ? null : fridges.get(0));
        if (fridge != null) return fridge;

        for (House shared : houseShareService.sharedHouses(familyId)) {
            List<Long> roomIds = roomMapper.selectList(new LambdaQueryWrapper<Room>()
                            .eq(Room::getHouseId, shared.getId()))
                    .stream().map(Room::getId).toList();
            if (roomIds.isEmpty()) continue;
            fridge = furnitureMapper.selectList(new LambdaQueryWrapper<Furniture>()
                            .in(Furniture::getRoomId, roomIds)
                            .and(w -> w.eq(Furniture::getType, "冰箱").or().like(Furniture::getName, "冰箱"))
                            .orderByAsc(Furniture::getId))
                    .stream().findFirst().orElse(null);
            if (fridge != null) return fridge;
        }

        Room kitchen = roomMapper.selectList(new LambdaQueryWrapper<Room>()
                        .eq(Room::getFamilyId, familyId)
                        .like(Room::getName, "厨房")
                        .orderByAsc(Room::getId))
                .stream().findFirst().orElse(null);
        fridge = new Furniture();
        fridge.setFamilyId(familyId);
        fridge.setRoomId(kitchen != null ? kitchen.getId() : null);
        fridge.setName("冰箱");
        fridge.setType("冰箱");
        furnitureMapper.insert(fridge);
        return fridge;
    }

    public Furniture furnitureCreate(ViewerScope viewer, FurnitureDTO dto) {
        requireText(dto.getName(), "请填写家具名");
        Long familyId = viewer.familyId();
        if (dto.getRoomId() != null) {
            Room r = requireRoom(dto.getRoomId());
            requireRoomWritable(r, viewer);
            familyId = r.getFamilyId();
        }
        Furniture f = new Furniture();
        f.setFamilyId(familyId);
        f.setRoomId(dto.getRoomId());
        f.setName(dto.getName());
        f.setType(dto.getType());
        f.setX(dto.getX());
        f.setY(dto.getY());
        f.setW(dto.getW());
        f.setH(dto.getH());
        f.setNote(dto.getNote());
        f.setCreatedBy(viewer.userId());
        furnitureMapper.insert(f);
        return f;
    }

    public void furnitureUpdate(Long id, ViewerScope viewer, FurnitureDTO dto) {
        Furniture f = requireFurniture(id);
        requireFurnitureWritable(f, viewer);
        if (dto.getName() != null) f.setName(dto.getName());
        if (dto.getType() != null) f.setType(dto.getType());
        if (dto.getRoomId() != null) {
            Room r = requireRoom(dto.getRoomId());
            requireRoomWritable(r, viewer);
            f.setRoomId(dto.getRoomId());
            f.setFamilyId(r.getFamilyId());
        }
        if (dto.getX() != null) f.setX(dto.getX());
        if (dto.getY() != null) f.setY(dto.getY());
        if (dto.getW() != null) f.setW(dto.getW());
        if (dto.getH() != null) f.setH(dto.getH());
        f.setNote(dto.getNote());
        furnitureMapper.updateById(f);
    }

    @Transactional
    public void furnitureDelete(Long id, ViewerScope viewer) {
        Furniture f = requireFurniture(id);
        requireFurnitureWritable(f, viewer);
        // 库里删除才是真正删除:家具上的物品不删除,仅解除归属(furniture_id/相对坐标置空)
        itemMapper.update(null, new LambdaUpdateWrapper<Item>()
                .eq(Item::getFamilyId, f.getFamilyId())
                .eq(Item::getFurnitureId, id)
                .set(Item::getFurnitureId, null)
                .set(Item::getRelX, null)
                .set(Item::getRelY, null));
        furnitureMapper.deleteById(id);
    }

    /**
     * 画板中移除家具:家具回库(room_id/画布几何置空),物品归属不变(furniture_id 不动)。
     */
    public void unplaceFurniture(Long id, ViewerScope viewer) {
        Furniture f = requireFurniture(id);
        requireFurnitureWritable(f, viewer);
        furnitureMapper.update(null, new LambdaUpdateWrapper<Furniture>()
                .eq(Furniture::getId, id)
                .set(Furniture::getRoomId, null)
                .set(Furniture::getX, null)
                .set(Furniture::getY, null)
                .set(Furniture::getW, null)
                .set(Furniture::getH, null));
    }

    // ---------- 物品 ----------

    /** 物品列表/搜索:自家按可见性过滤,并混入分享给我家的物品与共享房子里的物品 */
    public List<Map<String, Object>> itemList(ViewerScope viewer, String keyword, Long roomId, Long furnitureId, String type, Integer limit) {
        List<Long> sharedHouseIds = viewer.familyId() == null
                ? List.of() : houseShareService.sharedHouseIds(viewer.familyId());
        return itemMapper.selectItemByFamily(viewer.familyId(),
                keyword == null || keyword.isBlank() ? null : keyword.trim(),
                roomId, furnitureId, type, contentAccess.visibleFilter(viewer),
                sharedHouseIds, limit == null ? null : Math.clamp(limit, 1, 100));
    }

    public Item itemCreate(ViewerScope viewer, ItemDTO dto) {
        requireText(dto.getName(), "请填写物品名");
        Long familyId = viewer.familyId();
        if (dto.getFurnitureId() != null) {
            Furniture f = requireFurniture(dto.getFurnitureId());
            requireFurnitureWritable(f, viewer);
            familyId = f.getFamilyId();
        }
        if (dto.getRoomId() != null) {
            Room r = requireRoom(dto.getRoomId());
            requireRoomWritable(r, viewer);
            if (dto.getFurnitureId() != null && !Objects.equals(r.getFamilyId(), familyId)) {
                throw new BizException(ResultCode.BAD_REQUEST, "家具与房间不属于同一家庭");
            }
            familyId = r.getFamilyId();
        }
        Item i = new Item();
        i.setFamilyId(familyId);
        i.setFurnitureId(dto.getFurnitureId());
        i.setRoomId(dto.getRoomId());
        i.setName(dto.getName());
        i.setAliases(dto.getAliases());
        i.setPosition(dto.getPosition());
        i.setImageUrl(dto.getImageUrl());
        i.setType(dto.getType() == null ? "OTHER" : dto.getType());
        i.setVisibility(DictConst.visibility(dto.getVisibility()));
        i.setQuantity(dto.getQuantity());
        i.setUnit(dto.getUnit());
        i.setNote(dto.getNote());
        i.setStoredAt(dto.getStoredAt());
        if (i.getStoredAt() == null && "INGREDIENT".equals(i.getType())) {
            i.setStoredAt(java.time.LocalDateTime.now());
        }
        i.setShelfLife(dto.getShelfLife());
        i.setShelfLifeUnit(dto.getShelfLifeUnit());
        i.setRelX(dto.getRelX());
        i.setRelY(dto.getRelY());
        i.setCreatedBy(viewer.userId());
        itemMapper.insert(i);
        return i;
    }

    public void itemUpdate(Long id, ViewerScope viewer, ItemDTO dto) {
        Item i = requireEditable(id, viewer);
        if (dto.getName() != null) i.setName(dto.getName());
        if (dto.getFurnitureId() != null) {
            Furniture f = requireFurniture(dto.getFurnitureId());
            requireFurnitureWritable(f, viewer);
            i.setFurnitureId(dto.getFurnitureId());
            i.setFamilyId(f.getFamilyId());
        }
        if (dto.getRoomId() != null) {
            Room r = requireRoom(dto.getRoomId());
            requireRoomWritable(r, viewer);
            i.setRoomId(dto.getRoomId());
            i.setFamilyId(r.getFamilyId());
        }
        i.setAliases(dto.getAliases());
        i.setPosition(dto.getPosition());
        i.setImageUrl(dto.getImageUrl());
        if (dto.getType() != null) i.setType(dto.getType());
        if (dto.getVisibility() != null && !dto.getVisibility().isBlank()) {
            i.setVisibility(DictConst.visibility(dto.getVisibility()));
        }
        i.setQuantity(dto.getQuantity());
        i.setUnit(dto.getUnit());
        i.setNote(dto.getNote());
        i.setStoredAt(dto.getStoredAt());
        i.setShelfLife(dto.getShelfLife());
        i.setShelfLifeUnit(dto.getShelfLifeUnit());
        if (dto.getRelX() != null) i.setRelX(dto.getRelX());
        if (dto.getRelY() != null) i.setRelY(dto.getRelY());
        itemMapper.updateById(i);
    }

    public void itemDelete(Long id, ViewerScope viewer) {
        itemMapper.deleteById(requireEditable(id, viewer).getId());
    }

    /**
     * 取出食材:原子扣减库存(quantity = quantity - amount)。amount 必须 > 0,且不超过现有数量。
     */
    public void takeItem(Long id, java.math.BigDecimal amount, ViewerScope viewer) {
        Item item = requireEditable(id, viewer);
        if (amount == null || amount.compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "取出数量必须大于 0");
        }
        int updated = itemMapper.takeAmount(id, item.getFamilyId(), amount);
        if (updated == 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "取出数量超过现有库存");
        }
    }

    /**
     * 批量设置物品所属家具:furnitureId 为空时解除归属;非空时物品归入该家具(散放房间与相对坐标随之重置)。
     * 跨家庭挪动一律拒绝(物品只能归入同一家庭的家具)。
     */
    public void itemBatchAssignFurniture(ViewerScope viewer, List<Long> ids, Long furnitureId) {
        if (ids == null || ids.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "请选择物品");
        }
        long distinct = ids.stream().distinct().count();
        List<Item> found = itemMapper.selectBatchIds(ids.stream().distinct().toList());
        if (found.size() != distinct) {
            throw new BizException(ResultCode.NOT_FOUND, "部分物品不存在或不属于当前家庭");
        }
        found.forEach(i -> assertEditable0(i, viewer));
        if (furnitureId != null) {
            Furniture f = requireFurniture(furnitureId);
            requireFurnitureWritable(f, viewer);
            for (Item i : found) {
                if (!Objects.equals(i.getFamilyId(), f.getFamilyId())) {
                    throw new BizException(ResultCode.BAD_REQUEST, "只能把物品归入同一家庭的家具");
                }
            }
        }
        LambdaUpdateWrapper<Item> uw = new LambdaUpdateWrapper<Item>()
                .in(Item::getId, ids.stream().distinct().toList());
        if (furnitureId != null) {
            // relX/relY 是家具画布内的归一化坐标(0–1),批量归入家具时置 0.5 即摆到画布中心,
            // 避免所有物品堆在左上角;离开家具则清空坐标(回到房间级,无相对坐标)
            uw.set(Item::getFurnitureId, furnitureId)
                    .set(Item::getRoomId, null)
                    .set(Item::getRelX, new java.math.BigDecimal("0.5"))
                    .set(Item::getRelY, new java.math.BigDecimal("0.5"));
        } else {
            uw.set(Item::getFurnitureId, null)
                    .set(Item::getRelX, null)
                    .set(Item::getRelY, null);
        }
        itemMapper.update(null, uw);
    }

    // ---------- 户型图(2期) ----------

    public Map<String, Object> floorPlan(ViewerScope viewer, Long houseId, Integer floor) {
        House h = houseShareService.requireReadable(houseId, viewer);
        int fl = floor == null ? 1 : floor;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("houseId", h.getId());
        result.put("houseName", h.getName());
        result.put("floor", fl);

        String imageUrl = null;
        double scale = 100.0;
        if (h.getFloorPlans() != null && !h.getFloorPlans().isBlank()) {
            try {
                JsonNode fp = mapper.readTree(h.getFloorPlans());
                JsonNode level = fp.get(String.valueOf(fl));
                if (level != null) {
                    if (level.hasNonNull("imageUrl")) imageUrl = level.get("imageUrl").asText();
                    if (level.hasNonNull("scale")) scale = level.get("scale").asDouble(100.0);
                }
            } catch (Exception ignored) {
                // 配置损坏回退默认 100px/m
            }
        }
        result.put("imageUrl", imageUrl);
        result.put("scale", scale);

        List<Room> rooms = roomMapper.selectList(new LambdaQueryWrapper<Room>()
                .eq(Room::getHouseId, houseId)
                .eq(Room::getFloor, fl)
                .orderByAsc(Room::getSortOrder)
                .orderByAsc(Room::getId));
        result.put("rooms", rooms);

        List<Long> roomIds = rooms.stream().map(Room::getId).toList();
        List<Furniture> furnitures = roomIds.isEmpty() ? List.of()
                : furnitureMapper.selectList(new LambdaQueryWrapper<Furniture>()
                .in(Furniture::getRoomId, roomIds));
        result.put("furnitures", furnitures);

        List<Long> furnitureIds = furnitures.stream().map(Furniture::getId).toList();
        LambdaQueryWrapper<Item> iq = new LambdaQueryWrapper<>();
        iq.eq(Item::getFamilyId, h.getFamilyId());
        if (!furnitureIds.isEmpty() && !roomIds.isEmpty()) {
            iq.and(q -> q.in(Item::getFurnitureId, furnitureIds).or().in(Item::getRoomId, roomIds));
        } else if (!furnitureIds.isEmpty()) {
            iq.in(Item::getFurnitureId, furnitureIds);
        } else if (!roomIds.isEmpty()) {
            iq.in(Item::getRoomId, roomIds);
        } else {
            result.put("items", List.of());
            return result;
        }
        if (h.getFamilyId().equals(viewer.familyId())) {
            // 本家房子:PRIVATE 只对作者本人与家长显示,不混入圈内分享来的物品
            contentAccess.applyVisible(iq, ContentKind.ITEM, viewer,
                    Item::getFamilyId, Item::getCreatedBy, Item::getVisibility);
        } else {
            // 共享房里的物品:别人录的 PRIVATE 不展示(仅作者本人可见)
            iq.and(q -> q.eq(Item::getCreatedBy, viewer.userId())
                    .or().ne(Item::getVisibility, DictConst.VIS_PRIVATE));
        }
        result.put("items", itemMapper.selectList(iq));
        return result;
    }

    public void saveFloorPlans(Long houseId, ViewerScope viewer, String floorPlans) {
        houseShareService.requireWritable(houseId, viewer);
        houseMapper.update(null, new LambdaUpdateWrapper<House>()
                .eq(House::getId, houseId)
                .set(House::getFloorPlans, floorPlans));
    }

    public void saveRoomGeometry(Long id, ViewerScope viewer, String geometry) {
        requireRoomWritable(requireRoom(id), viewer);
        roomMapper.update(null, new LambdaUpdateWrapper<Room>()
                .eq(Room::getId, id)
                .set(Room::getGeometry, geometry));
    }

    public void saveFurnitureGeometry(Long id, ViewerScope viewer, FurnitureDTO dto) {
        requireFurnitureWritable(requireFurniture(id), viewer);
        LambdaUpdateWrapper<Furniture> uw = new LambdaUpdateWrapper<Furniture>()
                .eq(Furniture::getId, id)
                .set(Furniture::getX, dto.getX())
                .set(Furniture::getY, dto.getY())
                .set(Furniture::getW, dto.getW())
                .set(Furniture::getH, dto.getH());
        if (dto.getType() != null) uw.set(Furniture::getType, dto.getType());
        if (dto.getRoomId() != null) {
            Room r = requireRoom(dto.getRoomId());
            requireRoomWritable(r, viewer);
            uw.set(Furniture::getRoomId, dto.getRoomId()).set(Furniture::getFamilyId, r.getFamilyId());
        }
        furnitureMapper.update(null, uw);
    }

    public void saveItemPlace(Long id, ViewerScope viewer, ItemDTO dto) {
        requireEditable(id, viewer);
        itemMapper.update(null, new LambdaUpdateWrapper<Item>()
                .eq(Item::getId, id)
                .set(Item::getRelX, dto.getRelX())
                .set(Item::getRelY, dto.getRelY()));
    }

    // ---------- 校验 ----------

    private void requireText(String s, String msg) {
        if (s == null || s.isBlank()) throw new BizException(ResultCode.BAD_REQUEST, msg);
    }

    /** 本家 + 共享给我的房子 id(房间/家具列表用) */
    private List<Long> accessibleHouseIds(ViewerScope viewer) {
        Long familyId = viewer.familyId();
        if (familyId == null) return List.of();
        List<Long> ids = new ArrayList<>(houseMapper.selectList(new LambdaQueryWrapper<House>()
                        .eq(House::getFamilyId, familyId))
                .stream().map(House::getId).toList());
        ids.addAll(houseShareService.sharedHouseIds(familyId));
        return ids;
    }

    private Room requireRoom(Long id) {
        Room r = id == null ? null : roomMapper.selectById(id);
        if (r == null) throw new BizException(ResultCode.NOT_FOUND);
        return r;
    }

    private Furniture requireFurniture(Long id) {
        Furniture f = id == null ? null : furnitureMapper.selectById(id);
        if (f == null) throw new BizException(ResultCode.NOT_FOUND);
        return f;
    }

    private Item requireItem(Long id) {
        Item i = id == null ? null : itemMapper.selectById(id);
        if (i == null) throw new BizException(ResultCode.NOT_FOUND);
        return i;
    }

    /** 房间可读:挂在房子下按房子判定;理论上房间必挂房子,防御性回退到本家 */
    private void requireRoomReadable(Room r, ViewerScope viewer) {
        House h = houseShareService.houseOfRoom(r.getId());
        if (h == null) {
            requireOwnFamily(r.getFamilyId(), viewer);
            return;
        }
        if (!houseShareService.canRead(h, viewer)) throw new BizException(ResultCode.NOT_FOUND);
    }

    private void requireRoomWritable(Room r, ViewerScope viewer) {
        House h = houseShareService.houseOfRoom(r.getId());
        if (h == null) {
            requireOwnFamily(r.getFamilyId(), viewer);
            return;
        }
        if (!houseShareService.canWrite(h.getId(), h.getFamilyId(), viewer)) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
    }

    private void requireFurnitureWritable(Furniture f, ViewerScope viewer) {
        House h = houseShareService.houseOfFurniture(f.getId());
        if (h == null) {
            requireOwnFamily(f.getFamilyId(), viewer);
            return;
        }
        if (!houseShareService.canWrite(h.getId(), h.getFamilyId(), viewer)) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
    }

    private void requireOwnFamily(Long familyId, ViewerScope viewer) {
        if (familyId == null || !familyId.equals(viewer.familyId())) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
    }

    /** 改/删/取用物品:同家庭按可见性(PRIVATE 只作者本人与家长);共享房子里别人录的 PRIVATE 同样不可动 */
    private Item requireEditable(Long id, ViewerScope viewer) {
        Item i = requireItem(id);
        assertEditable0(i, viewer);
        return i;
    }

    private void assertEditable0(Item i, ViewerScope viewer) {
        House house = houseShareService.houseOf(i.getFurnitureId(), i.getRoomId());
        if (house != null && !house.getFamilyId().equals(viewer.familyId())) {
            // 协作:房子得写权限,且不能动别人录的私密物品
            if (!houseShareService.canWrite(house.getId(), house.getFamilyId(), viewer)) {
                throw new BizException(ResultCode.NOT_FOUND);
            }
            if (DictConst.VIS_PRIVATE.equals(i.getVisibility())
                    && !Objects.equals(viewer.userId(), i.getCreatedBy())) {
                throw new BizException(ResultCode.NOT_FOUND);
            }
            return;
        }
        requireOwnFamily(i.getFamilyId(), viewer);
        if (!contentAccess.canView(ContentKind.ITEM, i.getFamilyId(), i.getCreatedBy(), i.getVisibility(), viewer)) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
    }
}
