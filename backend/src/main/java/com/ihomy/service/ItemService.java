package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
        h.setSortOrder(dto.getSortOrder() == null ? 0 : dto.getSortOrder());
        h.setCreatedBy(userId);
        houseMapper.insert(h);
        return h;
    }

    public void houseUpdate(Long id, Long familyId, HouseDTO dto) {
        House h = requireHouse(id, familyId);
        if (dto.getName() != null) h.setName(dto.getName());
        if (dto.getAddress() != null) h.setAddress(dto.getAddress());
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
        roomMapper.updateById(r);
    }

    public void roomDelete(Long id, Long familyId) {
        requireRoom(id, familyId);
        if (furnitureMapper.selectCount(new LambdaQueryWrapper<Furniture>()
                .eq(Furniture::getRoomId, id)) > 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "该房间下还有家具,请先删除或转移家具");
        }
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
        if (dto.getRoomId() == null || requireRoom(dto.getRoomId(), familyId) == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "请选择所在房间");
        }
        Furniture f = new Furniture();
        f.setFamilyId(familyId);
        f.setRoomId(dto.getRoomId());
        f.setName(dto.getName());
        f.setNote(dto.getNote());
        f.setCreatedBy(userId);
        furnitureMapper.insert(f);
        return f;
    }

    public void furnitureUpdate(Long id, Long familyId, FurnitureDTO dto) {
        Furniture f = requireFurniture(id, familyId);
        if (dto.getName() != null) f.setName(dto.getName());
        if (dto.getRoomId() != null) requireRoom(dto.getRoomId(), familyId);
        if (dto.getRoomId() != null) f.setRoomId(dto.getRoomId());
        f.setNote(dto.getNote());
        furnitureMapper.updateById(f);
    }

    public void furnitureDelete(Long id, Long familyId) {
        requireFurniture(id, familyId);
        if (itemMapper.selectCount(new LambdaQueryWrapper<Item>()
                .eq(Item::getFurnitureId, id)) > 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "该家具上还有物品,请先删除或转移物品");
        }
        furnitureMapper.deleteById(id);
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
        Item i = new Item();
        i.setFamilyId(familyId);
        i.setFurnitureId(dto.getFurnitureId());
        i.setName(dto.getName());
        i.setAliases(dto.getAliases());
        i.setPosition(dto.getPosition());
        i.setImageUrl(dto.getImageUrl());
        i.setType(dto.getType() == null ? "OTHER" : dto.getType());
        i.setQuantity(dto.getQuantity());
        i.setUnit(dto.getUnit());
        i.setNote(dto.getNote());
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
        i.setAliases(dto.getAliases());
        i.setPosition(dto.getPosition());
        i.setImageUrl(dto.getImageUrl());
        if (dto.getType() != null) i.setType(dto.getType());
        i.setQuantity(dto.getQuantity());
        i.setUnit(dto.getUnit());
        i.setNote(dto.getNote());
        itemMapper.updateById(i);
    }

    public void itemDelete(Long id, Long familyId) {
        itemMapper.deleteById(requireItem(id, familyId).getId());
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
