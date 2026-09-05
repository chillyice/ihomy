package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ihomy.common.BizException;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 物品定位业务(V5.0,1期):房子/房间/家具/物品 CRUD + 跨级搜索。
 * 五级粒度:家(family_id) > 房子 > 房间 > 家具 > 位置;家庭隔离由 family_id 统一约束。
 */
@Service
@RequiredArgsConstructor
public class ItemService {

    private final HouseMapper houseMapper;
    private final RoomMapper roomMapper;
    private final FurnitureMapper furnitureMapper;
    private final ItemMapper itemMapper;
    private final ObjectMapper mapper = new ObjectMapper();

    // ---------- 房子 ----------

    public List<House> houseList(Long familyId) {
        return houseMapper.selectList(new LambdaQueryWrapper<House>()
                .eq(House::getFamilyId, familyId)
                .orderByAsc(House::getSortOrder)
                .orderByAsc(House::getId));
    }

    public House houseCreate(Long userId, Long familyId, HouseDTO dto) {
        requireText(dto.getName(), "请填写房子名");
        House h = new House();
        h.setFamilyId(familyId);
        h.setName(dto.getName());
        h.setAddress(dto.getAddress());
        h.setFloorPlans(dto.getFloorPlans());
        h.setSortOrder(dto.getSortOrder() == null ? 0 : dto.getSortOrder());
        h.setCreatedBy(userId);
        houseMapper.insert(h);
        return h;
    }

    public void houseUpdate(Long id, Long familyId, HouseDTO dto) {
        House h = requireHouse(id, familyId);
        if (dto.getName() != null) h.setName(dto.getName());
        if (dto.getAddress() != null) h.setAddress(dto.getAddress());
        if (dto.getFloorPlans() != null) h.setFloorPlans(dto.getFloorPlans());
        if (dto.getSortOrder() != null) h.setSortOrder(dto.getSortOrder());
        houseMapper.updateById(h);
    }

    public void houseDelete(Long id, Long familyId) {
        requireHouse(id, familyId);
        if (roomMapper.selectCount(new LambdaQueryWrapper<Room>()
                .eq(Room::getHouseId, id)) > 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "该房子下还有房间,请先删除或转移房间");
        }
        houseMapper.deleteById(id);
    }

    // ---------- 房间 ----------

    public List<Room> roomList(Long familyId, Long houseId) {
        return roomMapper.selectList(new LambdaQueryWrapper<Room>()
                .eq(Room::getFamilyId, familyId)
                .eq(houseId != null, Room::getHouseId, houseId)
                .orderByAsc(Room::getFloor)
                .orderByAsc(Room::getSortOrder)
                .orderByAsc(Room::getId));
    }

    public Room roomCreate(Long userId, Long familyId, RoomDTO dto) {
        requireText(dto.getName(), "请填写房间名");
        if (dto.getHouseId() == null || requireHouse(dto.getHouseId(), familyId) == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "请选择所在房子");
        }
        Room r = new Room();
        r.setFamilyId(familyId);
        r.setHouseId(dto.getHouseId());
        r.setName(dto.getName());
        r.setFloor(dto.getFloor() == null ? 1 : dto.getFloor());
        r.setSortOrder(dto.getSortOrder() == null ? 0 : dto.getSortOrder());
        r.setNote(dto.getNote());
        r.setGeometry(dto.getGeometry());
        r.setCreatedBy(userId);
        roomMapper.insert(r);
        return r;
    }

    public void roomUpdate(Long id, Long familyId, RoomDTO dto) {
        Room r = requireRoom(id, familyId);
        if (dto.getName() != null) r.setName(dto.getName());
        if (dto.getHouseId() != null) requireHouse(dto.getHouseId(), familyId);
        if (dto.getHouseId() != null) r.setHouseId(dto.getHouseId());
        if (dto.getFloor() != null) r.setFloor(dto.getFloor());
        if (dto.getSortOrder() != null) r.setSortOrder(dto.getSortOrder());
        r.setNote(dto.getNote());
        if (dto.getGeometry() != null) r.setGeometry(dto.getGeometry());
        roomMapper.updateById(r);
    }

    public void roomDelete(Long id, Long familyId) {
        requireRoom(id, familyId);
        // 家具进家具库(room_id 置空),与物品绑定不动;散放物品(room_id 指向本房间)同步清空
        furnitureMapper.update(null, new LambdaUpdateWrapper<Furniture>()
                .eq(Furniture::getFamilyId, familyId)
                .eq(Furniture::getRoomId, id)
                .set(Furniture::getRoomId, null));
        itemMapper.update(null, new LambdaUpdateWrapper<Item>()
                .eq(Item::getFamilyId, familyId)
                .eq(Item::getRoomId, id)
                .set(Item::getRoomId, null));
        roomMapper.deleteById(id);
    }

    // ---------- 家具 ----------

    public List<Furniture> furnitureList(Long familyId, Long roomId) {
        return furnitureMapper.selectList(new LambdaQueryWrapper<Furniture>()
                .eq(Furniture::getFamilyId, familyId)
                .eq(roomId != null, Furniture::getRoomId, roomId)
                .orderByAsc(Furniture::getId));
    }

    public Furniture furnitureCreate(Long userId, Long familyId, FurnitureDTO dto) {
        requireText(dto.getName(), "请填写家具名");
        if (dto.getRoomId() != null) {
            requireRoom(dto.getRoomId(), familyId);
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
        f.setCreatedBy(userId);
        furnitureMapper.insert(f);
        return f;
    }

    public void furnitureUpdate(Long id, Long familyId, FurnitureDTO dto) {
        Furniture f = requireFurniture(id, familyId);
        if (dto.getName() != null) f.setName(dto.getName());
        if (dto.getType() != null) f.setType(dto.getType());
        if (dto.getRoomId() != null) {
            requireRoom(dto.getRoomId(), familyId);
            f.setRoomId(dto.getRoomId());
        }
        if (dto.getX() != null) f.setX(dto.getX());
        if (dto.getY() != null) f.setY(dto.getY());
        if (dto.getW() != null) f.setW(dto.getW());
        if (dto.getH() != null) f.setH(dto.getH());
        f.setNote(dto.getNote());
        furnitureMapper.updateById(f);
    }

    public void furnitureDelete(Long id, Long familyId) {
        requireFurniture(id, familyId);
        // 库里删除才是真正删除:家具上的物品不删除,仅解除归属(furniture_id/相对坐标置空)
        itemMapper.update(null, new LambdaUpdateWrapper<Item>()
                .eq(Item::getFamilyId, familyId)
                .eq(Item::getFurnitureId, id)
                .set(Item::getFurnitureId, null)
                .set(Item::getRelX, null)
                .set(Item::getRelY, null));
        furnitureMapper.deleteById(id);
    }

    /**
     * 画板中移除家具:家具回库(room_id/画布几何置空),物品归属不变(furniture_id 不动)。
     */
    public void unplaceFurniture(Long id, Long familyId) {
        requireFurniture(id, familyId);
        furnitureMapper.update(null, new LambdaUpdateWrapper<Furniture>()
                .eq(Furniture::getId, id)
                .eq(Furniture::getFamilyId, familyId)
                .set(Furniture::getRoomId, null)
                .set(Furniture::getX, null)
                .set(Furniture::getY, null)
                .set(Furniture::getW, null)
                .set(Furniture::getH, null));
    }

    // ---------- 物品 ----------

    public List<Map<String, Object>> itemList(Long familyId, String keyword, Long roomId, Long furnitureId, String type) {
        return itemMapper.selectItemByFamily(familyId,
                keyword == null || keyword.isBlank() ? null : keyword.trim(),
                roomId, furnitureId, type);
    }

    public Item itemCreate(Long userId, Long familyId, ItemDTO dto) {
        requireText(dto.getName(), "请填写物品名");
        if (dto.getFurnitureId() != null) {
            requireFurniture(dto.getFurnitureId(), familyId);
        }
        if (dto.getRoomId() != null) {
            requireRoom(dto.getRoomId(), familyId);
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
        i.setQuantity(dto.getQuantity());
        i.setUnit(dto.getUnit());
        i.setNote(dto.getNote());
        i.setRelX(dto.getRelX());
        i.setRelY(dto.getRelY());
        i.setCreatedBy(userId);
        itemMapper.insert(i);
        return i;
    }

    public void itemUpdate(Long id, Long familyId, ItemDTO dto) {
        Item i = requireItem(id, familyId);
        if (dto.getName() != null) i.setName(dto.getName());
        if (dto.getFurnitureId() != null) {
            requireFurniture(dto.getFurnitureId(), familyId);
            i.setFurnitureId(dto.getFurnitureId());
        }
        if (dto.getRoomId() != null) {
            requireRoom(dto.getRoomId(), familyId);
            i.setRoomId(dto.getRoomId());
        }
        i.setAliases(dto.getAliases());
        i.setPosition(dto.getPosition());
        i.setImageUrl(dto.getImageUrl());
        if (dto.getType() != null) i.setType(dto.getType());
        i.setQuantity(dto.getQuantity());
        i.setUnit(dto.getUnit());
        i.setNote(dto.getNote());
        if (dto.getRelX() != null) i.setRelX(dto.getRelX());
        if (dto.getRelY() != null) i.setRelY(dto.getRelY());
        itemMapper.updateById(i);
    }

    public void itemDelete(Long id, Long familyId) {
        itemMapper.deleteById(requireItem(id, familyId).getId());
    }

    /**
     * 批量设置物品所属家具:furnitureId 为空时解除归属;非空时物品归入该家具(散放房间与相对坐标随之重置)。
     */
    public void itemBatchAssignFurniture(Long familyId, List<Long> ids, Long furnitureId) {
        if (ids == null || ids.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "请选择物品");
        }
        if (furnitureId != null) {
            requireFurniture(furnitureId, familyId);
        }
        long distinct = ids.stream().distinct().count();
        List<Item> found = itemMapper.selectList(new LambdaQueryWrapper<Item>()
                .eq(Item::getFamilyId, familyId)
                .in(Item::getId, ids));
        if (found.size() != distinct) {
            throw new BizException(ResultCode.NOT_FOUND, "部分物品不存在或不属于当前家庭");
        }
        LambdaUpdateWrapper<Item> uw = new LambdaUpdateWrapper<Item>()
                .eq(Item::getFamilyId, familyId)
                .in(Item::getId, ids);
        if (furnitureId != null) {
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

    public Map<String, Object> floorPlan(Long familyId, Long houseId, Integer floor) {
        House h = requireHouse(houseId, familyId);
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
                .eq(Room::getFamilyId, familyId)
                .eq(Room::getHouseId, houseId)
                .eq(Room::getFloor, fl)
                .orderByAsc(Room::getSortOrder)
                .orderByAsc(Room::getId));
        result.put("rooms", rooms);

        List<Long> roomIds = rooms.stream().map(Room::getId).toList();
        List<Furniture> furnitures = roomIds.isEmpty() ? List.of()
                : furnitureMapper.selectList(new LambdaQueryWrapper<Furniture>()
                .eq(Furniture::getFamilyId, familyId)
                .in(Furniture::getRoomId, roomIds));
        result.put("furnitures", furnitures);

        List<Long> furnitureIds = furnitures.stream().map(Furniture::getId).toList();
        List<Item> items;
        if (!furnitureIds.isEmpty() && !roomIds.isEmpty()) {
            items = itemMapper.selectList(new LambdaQueryWrapper<Item>()
                    .eq(Item::getFamilyId, familyId)
                    .and(q -> q.in(Item::getFurnitureId, furnitureIds).or().in(Item::getRoomId, roomIds)));
        } else if (!furnitureIds.isEmpty()) {
            items = itemMapper.selectList(new LambdaQueryWrapper<Item>()
                    .eq(Item::getFamilyId, familyId).in(Item::getFurnitureId, furnitureIds));
        } else if (!roomIds.isEmpty()) {
            items = itemMapper.selectList(new LambdaQueryWrapper<Item>()
                    .eq(Item::getFamilyId, familyId).in(Item::getRoomId, roomIds));
        } else {
            items = List.of();
        }
        result.put("items", items);
        return result;
    }

    public void saveFloorPlans(Long houseId, Long familyId, String floorPlans) {
        requireHouse(houseId, familyId);
        houseMapper.update(null, new LambdaUpdateWrapper<House>()
                .eq(House::getId, houseId)
                .set(House::getFloorPlans, floorPlans));
    }

    public void saveRoomGeometry(Long id, Long familyId, String geometry) {
        requireRoom(id, familyId);
        roomMapper.update(null, new LambdaUpdateWrapper<Room>()
                .eq(Room::getId, id)
                .set(Room::getGeometry, geometry));
    }

    public void saveFurnitureGeometry(Long id, Long familyId, FurnitureDTO dto) {
        requireFurniture(id, familyId);
        LambdaUpdateWrapper<Furniture> uw = new LambdaUpdateWrapper<Furniture>()
                .eq(Furniture::getId, id)
                .set(Furniture::getX, dto.getX())
                .set(Furniture::getY, dto.getY())
                .set(Furniture::getW, dto.getW())
                .set(Furniture::getH, dto.getH());
        if (dto.getType() != null) uw.set(Furniture::getType, dto.getType());
        if (dto.getRoomId() != null) {
            requireRoom(dto.getRoomId(), familyId);
            uw.set(Furniture::getRoomId, dto.getRoomId());
        }
        furnitureMapper.update(null, uw);
    }

    public void saveItemPlace(Long id, Long familyId, ItemDTO dto) {
        requireItem(id, familyId);
        itemMapper.update(null, new LambdaUpdateWrapper<Item>()
                .eq(Item::getId, id)
                .set(Item::getRelX, dto.getRelX())
                .set(Item::getRelY, dto.getRelY()));
    }

    // ---------- 校验 ----------

    private void requireText(String s, String msg) {
        if (s == null || s.isBlank()) throw new BizException(ResultCode.BAD_REQUEST, msg);
    }

    private House requireHouse(Long id, Long familyId) {
        House h = houseMapper.selectById(id);
        if (h == null || !h.getFamilyId().equals(familyId)) throw new BizException(ResultCode.NOT_FOUND);
        return h;
    }

    private Room requireRoom(Long id, Long familyId) {
        Room r = roomMapper.selectById(id);
        if (r == null || !r.getFamilyId().equals(familyId)) throw new BizException(ResultCode.NOT_FOUND);
        return r;
    }

    private Furniture requireFurniture(Long id, Long familyId) {
        Furniture f = furnitureMapper.selectById(id);
        if (f == null || !f.getFamilyId().equals(familyId)) throw new BizException(ResultCode.NOT_FOUND);
        return f;
    }

    private Item requireItem(Long id, Long familyId) {
        Item i = itemMapper.selectById(id);
        if (i == null || !i.getFamilyId().equals(familyId)) throw new BizException(ResultCode.NOT_FOUND);
        return i;
    }
}
