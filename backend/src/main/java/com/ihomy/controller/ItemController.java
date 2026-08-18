package com.ihomy.controller;

import com.ihomy.annotation.OperationLog;
import com.ihomy.common.Result;
import com.ihomy.dto.FurnitureDTO;
import com.ihomy.dto.HouseDTO;
import com.ihomy.dto.ItemDTO;
import com.ihomy.dto.RoomDTO;
import com.ihomy.entity.Furniture;
import com.ihomy.entity.House;
import com.ihomy.entity.Item;
import com.ihomy.entity.Room;
import com.ihomy.security.LoginUser;
import com.ihomy.security.SecurityHelper;
import com.ihomy.service.ItemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 物品定位接口(V5.0,1期):房子/房间/家具/物品 CRUD + 跨级搜索。
 * 仅登录即可用(家庭内共享),3期 AI 语义拆解将复用本组接口。
 */
@Tag(name = "物品定位")
@RestController
@RequestMapping("/item")
@RequiredArgsConstructor
public class ItemController {

    private final ItemService itemService;
    private final SecurityHelper securityHelper;

    private LoginUser current() {
        return securityHelper.current();
    }

    // ---------- 房子 ----------

    @Operation(summary = "房子列表")
    @GetMapping("/house/list")
    public Result<List<House>> houseList() {
        return Result.success(itemService.houseList(current().getFamilyId()));
    }

    @Operation(summary = "新增房子")
    @OperationLog(module = "ITEM", operationType = "CREATE", description = "新增房子")
    @PostMapping("/house")
    public Result<House> houseCreate(@RequestBody HouseDTO dto) {
        LoginUser u = current();
        return Result.success(itemService.houseCreate(u.getUserId(), u.getFamilyId(), dto));
    }

    @Operation(summary = "编辑房子")
    @OperationLog(module = "ITEM", operationType = "UPDATE", description = "编辑房子")
    @PutMapping("/house/{id}")
    public Result<Void> houseUpdate(@PathVariable Long id, @RequestBody HouseDTO dto) {
        itemService.houseUpdate(id, current().getFamilyId(), dto);
        return Result.success();
    }

    @Operation(summary = "删除房子")
    @OperationLog(module = "ITEM", operationType = "DELETE", description = "删除房子")
    @DeleteMapping("/house/{id}")
    public Result<Void> houseDelete(@PathVariable Long id) {
        itemService.houseDelete(id, current().getFamilyId());
        return Result.success();
    }

    // ---------- 房间 ----------

    @Operation(summary = "房间列表")
    @GetMapping("/room/list")
    public Result<List<Room>> roomList(@RequestParam(required = false) Long houseId) {
        return Result.success(itemService.roomList(current().getFamilyId(), houseId));
    }

    @Operation(summary = "新增房间")
    @OperationLog(module = "ITEM", operationType = "CREATE", description = "新增房间")
    @PostMapping("/room")
    public Result<Room> roomCreate(@RequestBody RoomDTO dto) {
        LoginUser u = current();
        return Result.success(itemService.roomCreate(u.getUserId(), u.getFamilyId(), dto));
    }

    @Operation(summary = "编辑房间")
    @OperationLog(module = "ITEM", operationType = "UPDATE", description = "编辑房间")
    @PutMapping("/room/{id}")
    public Result<Void> roomUpdate(@PathVariable Long id, @RequestBody RoomDTO dto) {
        itemService.roomUpdate(id, current().getFamilyId(), dto);
        return Result.success();
    }

    @Operation(summary = "删除房间")
    @OperationLog(module = "ITEM", operationType = "DELETE", description = "删除房间")
    @DeleteMapping("/room/{id}")
    public Result<Void> roomDelete(@PathVariable Long id) {
        itemService.roomDelete(id, current().getFamilyId());
        return Result.success();
    }

    // ---------- 家具 ----------

    @Operation(summary = "家具列表")
    @GetMapping("/furniture/list")
    public Result<List<Furniture>> furnitureList(@RequestParam(required = false) Long roomId) {
        return Result.success(itemService.furnitureList(current().getFamilyId(), roomId));
    }

    @Operation(summary = "新增家具")
    @OperationLog(module = "ITEM", operationType = "CREATE", description = "新增家具")
    @PostMapping("/furniture")
    public Result<Furniture> furnitureCreate(@RequestBody FurnitureDTO dto) {
        LoginUser u = current();
        return Result.success(itemService.furnitureCreate(u.getUserId(), u.getFamilyId(), dto));
    }

    @Operation(summary = "编辑家具")
    @OperationLog(module = "ITEM", operationType = "UPDATE", description = "编辑家具")
    @PutMapping("/furniture/{id}")
    public Result<Void> furnitureUpdate(@PathVariable Long id, @RequestBody FurnitureDTO dto) {
        itemService.furnitureUpdate(id, current().getFamilyId(), dto);
        return Result.success();
    }

    @Operation(summary = "删除家具")
    @OperationLog(module = "ITEM", operationType = "DELETE", description = "删除家具")
    @DeleteMapping("/furniture/{id}")
    public Result<Void> furnitureDelete(@PathVariable Long id) {
        itemService.furnitureDelete(id, current().getFamilyId());
        return Result.success();
    }

    // ---------- 物品 ----------

    @Operation(summary = "物品列表/搜索")
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> itemList(@RequestParam(required = false) String keyword,
                                                       @RequestParam(required = false) Long roomId,
                                                       @RequestParam(required = false) Long furnitureId,
                                                       @RequestParam(required = false) String type) {
        return Result.success(itemService.itemList(current().getFamilyId(), keyword, roomId, furnitureId, type));
    }

    @Operation(summary = "新增物品")
    @OperationLog(module = "ITEM", operationType = "CREATE", description = "新增物品")
    @PostMapping
    public Result<Item> itemCreate(@RequestBody ItemDTO dto) {
        LoginUser u = current();
        return Result.success(itemService.itemCreate(u.getUserId(), u.getFamilyId(), dto));
    }

    @Operation(summary = "编辑物品")
    @OperationLog(module = "ITEM", operationType = "UPDATE", description = "编辑物品")
    @PutMapping("/{id}")
    public Result<Void> itemUpdate(@PathVariable Long id, @RequestBody ItemDTO dto) {
        itemService.itemUpdate(id, current().getFamilyId(), dto);
        return Result.success();
    }

    @Operation(summary = "删除物品")
    @OperationLog(module = "ITEM", operationType = "DELETE", description = "删除物品")
    @DeleteMapping("/{id}")
    public Result<Void> itemDelete(@PathVariable Long id) {
        itemService.itemDelete(id, current().getFamilyId());
        return Result.success();
    }
}
