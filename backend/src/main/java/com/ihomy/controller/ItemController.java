package com.ihomy.controller;

import com.ihomy.annotation.OperationLog;
import com.ihomy.common.Result;
import com.ihomy.dto.FurnitureDTO;
import com.ihomy.dto.HouseDTO;
import com.ihomy.dto.HouseShareDTO;
import com.ihomy.dto.ItemAiDTO;
import com.ihomy.dto.ItemBatchDTO;
import com.ihomy.dto.ItemDTO;
import com.ihomy.dto.ItemTakeDTO;
import com.ihomy.dto.RoomDTO;
import com.ihomy.entity.Furniture;
import com.ihomy.entity.House;
import com.ihomy.entity.Item;
import com.ihomy.entity.Room;
import com.ihomy.service.HouseShareService;
import com.ihomy.service.ItemAiService;
import com.ihomy.service.ItemService;
import com.ihomy.service.access.ViewerScope;
import com.ihomy.service.access.ViewerScopeResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 物品定位接口(V5.0,1期):房子/房间/家具/物品 CRUD + 跨级搜索。
 * 仅登录即可用(家庭内共享);房子可协作共享给另一个家庭,读写判定在 ItemService/HouseShareService。
 */
@Tag(name = "物品定位")
@RestController
@RequestMapping("/item")
@RequiredArgsConstructor
public class ItemController {

    private final ItemService itemService;
    private final ItemAiService itemAiService;
    private final HouseShareService houseShareService;
    private final ViewerScopeResolver viewerScopeResolver;

    private ViewerScope viewer() {
        return viewerScopeResolver.resolve();
    }

    // ---------- 房子 ----------

    @Operation(summary = "房子列表(本家 + 共享给我家的)")
    @GetMapping("/house/list")
    public Result<List<House>> houseList() {
        return Result.success(itemService.houseList(viewer()));
    }

    @Operation(summary = "新增房子")
    @OperationLog(module = "ITEM", operationType = "CREATE", description = "新增房子")
    @PostMapping("/house")
    public Result<House> houseCreate(@RequestBody HouseDTO dto) {
        return Result.success(itemService.houseCreate(viewer(), dto));
    }

    @Operation(summary = "编辑房子")
    @OperationLog(module = "ITEM", operationType = "UPDATE", description = "编辑房子")
    @PutMapping("/house/{id}")
    public Result<Void> houseUpdate(@PathVariable Long id, @RequestBody HouseDTO dto) {
        itemService.houseUpdate(id, viewer(), dto);
        return Result.success();
    }

    @Operation(summary = "删除房子")
    @OperationLog(module = "ITEM", operationType = "DELETE", description = "删除房子")
    @DeleteMapping("/house/{id}")
    public Result<Void> houseDelete(@PathVariable Long id) {
        itemService.houseDelete(id, viewer());
        return Result.success();
    }

    // ---------- 房子协作共享 ----------

    @Operation(summary = "房子的共享清单")
    @GetMapping("/house/{id}/shares")
    public Result<List<Map<String, Object>>> houseShares(@PathVariable Long id) {
        houseShareService.requireOwn(id, viewer());
        return Result.success(houseShareService.listShares(id));
    }

    @Operation(summary = "设置房子的共享家庭(全量替换)")
    @OperationLog(module = "ITEM", operationType = "UPDATE", description = "设置房子共享")
    @PutMapping("/house/{id}/shares")
    public Result<Void> houseSharesSave(@PathVariable Long id, @RequestBody HouseShareDTO dto) {
        ViewerScope v = viewer();
        houseShareService.replaceShares(id, v, v.userId(), dto.getFamilyIds());
        return Result.success();
    }

    @Operation(summary = "撤销某家庭的房子共享")
    @OperationLog(module = "ITEM", operationType = "UPDATE", description = "撤销房子共享")
    @DeleteMapping("/house/{id}/shares/{familyId}")
    public Result<Void> houseShareRevoke(@PathVariable Long id, @PathVariable Long familyId) {
        houseShareService.revokeShare(id, familyId, viewer());
        return Result.success();
    }

    // ---------- 房间 ----------

    @Operation(summary = "房间列表")
    @GetMapping("/room/list")
    public Result<List<Room>> roomList(@RequestParam(required = false) Long houseId) {
        return Result.success(itemService.roomList(viewer(), houseId));
    }

    @Operation(summary = "新增房间")
    @OperationLog(module = "ITEM", operationType = "CREATE", description = "新增房间")
    @PostMapping("/room")
    public Result<Room> roomCreate(@RequestBody RoomDTO dto) {
        return Result.success(itemService.roomCreate(viewer(), dto));
    }

    @Operation(summary = "编辑房间")
    @OperationLog(module = "ITEM", operationType = "UPDATE", description = "编辑房间")
    @PutMapping("/room/{id}")
    public Result<Void> roomUpdate(@PathVariable Long id, @RequestBody RoomDTO dto) {
        itemService.roomUpdate(id, viewer(), dto);
        return Result.success();
    }

    @Operation(summary = "删除房间")
    @OperationLog(module = "ITEM", operationType = "DELETE", description = "删除房间")
    @DeleteMapping("/room/{id}")
    public Result<Void> roomDelete(@PathVariable Long id) {
        itemService.roomDelete(id, viewer());
        return Result.success();
    }

    // ---------- 家具 ----------

    @Operation(summary = "家具列表")
    @GetMapping("/furniture/list")
    public Result<List<Furniture>> furnitureList(@RequestParam(required = false) Long roomId) {
        return Result.success(itemService.furnitureList(viewer(), roomId));
    }

    @Operation(summary = "家庭默认冰箱(无则创建)")
    @GetMapping("/furniture/default-fridge")
    public Result<Furniture> furnitureDefaultFridge() {
        return Result.success(itemService.getDefaultFridge(viewer()));
    }

    @Operation(summary = "新增家具")
    @OperationLog(module = "ITEM", operationType = "CREATE", description = "新增家具")
    @PostMapping("/furniture")
    public Result<Furniture> furnitureCreate(@RequestBody FurnitureDTO dto) {
        return Result.success(itemService.furnitureCreate(viewer(), dto));
    }

    @Operation(summary = "编辑家具")
    @OperationLog(module = "ITEM", operationType = "UPDATE", description = "编辑家具")
    @PutMapping("/furniture/{id}")
    public Result<Void> furnitureUpdate(@PathVariable Long id, @RequestBody FurnitureDTO dto) {
        itemService.furnitureUpdate(id, viewer(), dto);
        return Result.success();
    }

    @Operation(summary = "删除家具")
    @OperationLog(module = "ITEM", operationType = "DELETE", description = "删除家具")
    @DeleteMapping("/furniture/{id}")
    public Result<Void> furnitureDelete(@PathVariable Long id) {
        itemService.furnitureDelete(id, viewer());
        return Result.success();
    }

    @Operation(summary = "家具移入家具库(画板移除,物品归属不变)")
    @OperationLog(module = "ITEM", operationType = "UPDATE", description = "家具移入家具库(画板移除)")
    @PutMapping("/furniture/{id}/unplace")
    public Result<Void> furnitureUnplace(@PathVariable Long id) {
        itemService.unplaceFurniture(id, viewer());
        return Result.success();
    }

    // ---------- 物品 ----------

    @Operation(summary = "物品列表/搜索")
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> itemList(@RequestParam(required = false) String keyword,
                                                       @RequestParam(required = false) Long roomId,
                                                       @RequestParam(required = false) Long furnitureId,
                                                       @RequestParam(required = false) String type,
                                                       @RequestParam(required = false) Integer limit) {
        return Result.success(itemService.itemList(viewer(), keyword, roomId, furnitureId, type, limit));
    }

    @Operation(summary = "新增物品")
    @OperationLog(module = "ITEM", operationType = "CREATE", description = "新增物品")
    @PostMapping
    public Result<Item> itemCreate(@RequestBody ItemDTO dto) {
        return Result.success(itemService.itemCreate(viewer(), dto));
    }

    @Operation(summary = "编辑物品")
    @OperationLog(module = "ITEM", operationType = "UPDATE", description = "编辑物品")
    @PutMapping("/{id}")
    public Result<Void> itemUpdate(@PathVariable Long id, @RequestBody ItemDTO dto) {
        itemService.itemUpdate(id, viewer(), dto);
        return Result.success();
    }

    @Operation(summary = "删除物品")
    @OperationLog(module = "ITEM", operationType = "DELETE", description = "删除物品")
    @DeleteMapping("/{id}")
    public Result<Void> itemDelete(@PathVariable Long id) {
        itemService.itemDelete(id, viewer());
        return Result.success();
    }

    @Operation(summary = "取出食材")
    @OperationLog(module = "ITEM", operationType = "UPDATE", description = "取出食材")
    @PostMapping("/{id}/take")
    public Result<Void> itemTake(@PathVariable Long id, @RequestBody ItemTakeDTO dto) {
        itemService.takeItem(id, dto.getAmount(), viewer());
        return Result.success();
    }

    @Operation(summary = "批量设置物品所属家具")
    @OperationLog(module = "ITEM", operationType = "UPDATE", description = "批量设置物品所属家具")
    @PutMapping("/batch/furniture")
    public Result<Void> itemBatchAssignFurniture(@RequestBody ItemBatchDTO dto) {
        itemService.itemBatchAssignFurniture(viewer(), dto.getIds(), dto.getFurnitureId());
        return Result.success();
    }

    // ---------- 户型图(2期) ----------

    @Operation(summary = "户型图数据(房子元信息+房间+家具+物品)")
    @GetMapping("/floor-plan")
    public Result<Map<String, Object>> floorPlan(@RequestParam Long houseId,
                                                 @RequestParam(required = false) Integer floor) {
        return Result.success(itemService.floorPlan(viewer(), houseId, floor));
    }

    @Operation(summary = "保存楼层配置(底图/比例尺)")
    @OperationLog(module = "ITEM", operationType = "UPDATE", description = "保存楼层配置(底图/比例尺)")
    @PutMapping("/house/{id}/floor-plans")
    public Result<Void> houseFloorPlans(@PathVariable Long id, @RequestBody HouseDTO dto) {
        itemService.saveFloorPlans(id, viewer(), dto.getFloorPlans());
        return Result.success();
    }

    @Operation(summary = "保存房间几何")
    @OperationLog(module = "ITEM", operationType = "UPDATE", description = "保存房间几何")
    @PutMapping("/room/{id}/geometry")
    public Result<Void> roomGeometry(@PathVariable Long id, @RequestBody RoomDTO dto) {
        itemService.saveRoomGeometry(id, viewer(), dto.getGeometry());
        return Result.success();
    }

    @Operation(summary = "保存家具几何")
    @OperationLog(module = "ITEM", operationType = "UPDATE", description = "保存家具几何")
    @PutMapping("/furniture/{id}/geometry")
    public Result<Void> furnitureGeometry(@PathVariable Long id, @RequestBody FurnitureDTO dto) {
        itemService.saveFurnitureGeometry(id, viewer(), dto);
        return Result.success();
    }

    @Operation(summary = "保存物品相对坐标")
    @OperationLog(module = "ITEM", operationType = "UPDATE", description = "保存物品相对坐标")
    @PutMapping("/{id}/place")
    public Result<Void> itemPlace(@PathVariable Long id, @RequestBody ItemDTO dto) {
        itemService.saveItemPlace(id, viewer(), dto);
        return Result.success();
    }

    // ---------- AI 语义(3期:自然语言找物/放物,前端入口计入规划) ----------

    @Operation(summary = "AI 找物(自然语言搜索)")
    @OperationLog(module = "ITEM", operationType = "QUERY", description = "AI 找物")
    @PostMapping("/ai/find")
    public Result<Map<String, Object>> aiFind(@RequestBody ItemAiDTO dto) {
        return Result.success(itemAiService.find(viewer(), dto.getQuery()));
    }

    @Operation(summary = "AI 放物(自然语言登记位置)")
    @OperationLog(module = "ITEM", operationType = "CREATE", description = "AI 放物")
    @PostMapping("/ai/put")
    public Result<Map<String, Object>> aiPut(@RequestBody ItemAiDTO dto) {
        return Result.success(itemAiService.put(viewer(), dto.getText()));
    }
}
