package com.ihomy.controller;

import com.ihomy.annotation.OperationLog;
import com.ihomy.annotation.RequirePermission;
import com.ihomy.common.Result;
import com.ihomy.dto.PointsProductDTO;
import com.ihomy.entity.PointsOrder;
import com.ihomy.entity.PointsProduct;
import com.ihomy.security.LoginUser;
import com.ihomy.security.SecurityHelper;
import com.ihomy.service.PointsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 积分商城接口(V3.4):签到/商品兑换/家长上架与核销。
 * 兑换与签到登录即可;上架/编辑/核销需 points:manage(家长)。
 */
@Tag(name = "积分商城")
@RestController
@RequestMapping("/points")
@RequiredArgsConstructor
public class PointsController {

    private final PointsService pointsService;
    private final SecurityHelper securityHelper;

    private LoginUser current() {
        return securityHelper.current();
    }

    @Operation(summary = "我的积分概览(总积分/今日签到状态/连续天数)")
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        return Result.success(pointsService.stats(current().getUserId()));
    }

    @Operation(summary = "每日签到")
    @OperationLog(module = "POINTS", operationType = "CREATE", description = "每日签到")
    @PostMapping("/checkin")
    public Result<Map<String, Object>> checkin() {
        LoginUser user = current();
        return Result.success(pointsService.checkin(user.getUserId(), user.getFamilyId()));
    }

    @Operation(summary = "本家庭商品列表(附我的已兑次数)")
    @GetMapping("/products")
    public Result<List<Map<String, Object>>> products() {
        LoginUser user = current();
        return Result.success(pointsService.products(user.getFamilyId(), user.getUserId()));
    }

    @Operation(summary = "上架商品(家长)")
    @RequirePermission("points:manage")
    @OperationLog(module = "POINTS", operationType = "CREATE", description = "上架积分商品")
    @PostMapping("/products")
    public Result<PointsProduct> createProduct(@RequestBody PointsProductDTO dto) {
        LoginUser user = current();
        return Result.success(pointsService.createProduct(user.getFamilyId(), user.getUserId(), dto));
    }

    @Operation(summary = "编辑商品(家长,含上下架)")
    @RequirePermission("points:manage")
    @OperationLog(module = "POINTS", operationType = "UPDATE", description = "编辑积分商品")
    @PutMapping("/products/{id}")
    public Result<Void> updateProduct(@PathVariable Long id, @RequestBody PointsProductDTO dto) {
        pointsService.updateProduct(id, current().getFamilyId(), dto);
        return Result.success();
    }

    @Operation(summary = "下架商品(家长)")
    @RequirePermission("points:manage")
    @OperationLog(module = "POINTS", operationType = "DELETE", description = "下架积分商品")
    @DeleteMapping("/products/{id}")
    public Result<Void> deleteProduct(@PathVariable Long id) {
        pointsService.deleteProduct(id, current().getFamilyId());
        return Result.success();
    }

    @Operation(summary = "兑换商品")
    @OperationLog(module = "POINTS", operationType = "CREATE", description = "兑换积分商品", saveArgs = false)
    @PostMapping("/products/{id}/redeem")
    public Result<PointsOrder> redeem(@PathVariable Long id) {
        LoginUser user = current();
        return Result.success(pointsService.redeem(id, user.getFamilyId(), user.getUserId()));
    }

    @Operation(summary = "我的兑换记录")
    @GetMapping("/orders")
    public Result<List<PointsOrder>> myOrders() {
        return Result.success(pointsService.myOrders(current().getUserId()));
    }

    @Operation(summary = "家庭兑换记录(家长核销用)")
    @RequirePermission("points:manage")
    @GetMapping("/orders/all")
    public Result<List<Map<String, Object>>> familyOrders() {
        return Result.success(pointsService.familyOrders(current().getFamilyId()));
    }

    @Operation(summary = "核销订单(家长确认商品已使用)")
    @RequirePermission("points:manage")
    @OperationLog(module = "POINTS", operationType = "UPDATE", description = "核销兑换订单")
    @PutMapping("/orders/{id}/taken")
    public Result<Void> markTaken(@PathVariable Long id) {
        pointsService.markTaken(id, current().getFamilyId());
        return Result.success();
    }
}