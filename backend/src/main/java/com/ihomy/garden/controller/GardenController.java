package com.ihomy.garden.controller;

import com.ihomy.annotation.OperationLog;
import com.ihomy.common.BizException;
import com.ihomy.common.Result;
import com.ihomy.common.ResultCode;
import com.ihomy.garden.dto.BatchCareRequest;
import com.ihomy.garden.dto.BatchSowRequest;
import com.ihomy.garden.dto.GardenEntryVO;
import com.ihomy.garden.dto.GardenMetaVO;
import com.ihomy.garden.dto.GardenPanoramaVO;
import com.ihomy.garden.dto.MailOpenVO;
import com.ihomy.garden.dto.PlotActionVO;
import com.ihomy.garden.dto.SowRequest;
import com.ihomy.garden.entity.GardenWorld;
import com.ihomy.garden.service.GardenGrowthService;
import com.ihomy.garden.service.GardenMailService;
import com.ihomy.garden.service.GardenPlotService;
import com.ihomy.garden.service.GardenQueryService;
import com.ihomy.garden.service.GardenMarketService;
import com.ihomy.garden.service.GardenSellService;
import com.ihomy.garden.service.GardenTaskService;
import com.ihomy.garden.service.GardenWorldService;
import com.ihomy.security.LoginUser;
import com.ihomy.security.SecurityHelper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 家圃时光(/api/garden):登录 + 家庭隔离,权限沿用「登录即可」,不新增权限码。
 * 所有写接口接受 Idempotency-Key 头(值 = op_id,ULID),由 garden_log.op_id 唯一索引兜底重放(架构分册 §17)。
 * 地块归属是统一前置校验:共享放行、本人放行、其余 403「这是 {昵称} 的房间」。
 */
@Tag(name = "家圃时光")
@RestController
@RequestMapping("/garden")
@RequiredArgsConstructor
public class GardenController {

    private final GardenQueryService queryService;
    private final GardenMailService mailService;
    private final GardenPlotService plotService;
    private final GardenTaskService taskService;
    private final GardenGrowthService growthService;
    private final GardenWorldService worldService;
    private final GardenSellService sellService;
    private final GardenMarketService marketService;
    private final SecurityHelper securityHelper;

    /** 当前登录上下文;未登录返回 null(meta 用) */
    private LoginUser currentOrNull() {
        return securityHelper.current();
    }

    private LoginUser requireCurrent() {
        LoginUser user = securityHelper.current();
        if (user == null || user.getFamilyId() == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        return user;
    }

    private String opIdOf(String header) {
        return header;
    }

    @Operation(summary = "首页便签状态(有信/待照料/平静)")
    @GetMapping("/entry")
    public Result<GardenEntryVO> entry() {
        LoginUser user = requireCurrent();
        return Result.success(queryService.entry(user.getFamilyId(), user.getUserId()));
    }

    @Operation(summary = "能力协商(免登录)")
    @GetMapping("/meta")
    public Result<GardenMetaVO> meta() {
        LoginUser user = currentOrNull();
        return Result.success(queryService.meta(
                user == null ? null : user.getFamilyId(),
                user == null ? null : user.getUserId()));
    }

    @Operation(summary = "花园全景")
    @GetMapping
    public Result<GardenPanoramaVO> panorama() {
        LoginUser user = requireCurrent();
        return Result.success(queryService.panorama(user.getFamilyId(), user.getUserId()));
    }

    @Operation(summary = "窗台影子快照(本人窗台,含空盆)")
    @GetMapping("/silhouette")
    public Result<Map<String, Object>> silhouette() {
        LoginUser user = requireCurrent();
        return Result.success(queryService.silhouette(user.getFamilyId(), user.getUserId()));
    }

    @Operation(summary = "拆每日信封(按世界一天一封,可补 3 天)")
    @OperationLog(module = "GARDEN", operationType = "CREATE", description = "拆每日信封")
    @PostMapping("/mail/open")
    public Result<MailOpenVO> openMail(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        LoginUser user = requireCurrent();
        return Result.success(mailService.open(user.getFamilyId(), user.getUserId(), opIdOf(idempotencyKey)));
    }

    @Operation(summary = "播种")
    @OperationLog(module = "GARDEN", operationType = "CREATE", description = "播种")
    @PostMapping("/plot")
    public Result<PlotActionVO> sow(@RequestBody SowRequest dto,
                                    @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        LoginUser user = requireCurrent();
        return Result.success(plotService.sow(user.getFamilyId(), user.getUserId(), dto, opIdOf(idempotencyKey)));
    }

    @Operation(summary = "备盆(一次性,首盆免单)")
    @OperationLog(module = "GARDEN", operationType = "UPDATE", description = "备盆")
    @PostMapping("/plot/{id}/prepare")
    public Result<PlotActionVO> prepare(@PathVariable Long id,
                                        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        LoginUser user = requireCurrent();
        return Result.success(plotService.prepare(user.getFamilyId(), user.getUserId(), id, opIdOf(idempotencyKey)));
    }

    @Operation(summary = "浇水")
    @OperationLog(module = "GARDEN", operationType = "UPDATE", description = "给地块浇水")
    @PostMapping("/plot/{id}/water")
    public Result<PlotActionVO> water(@PathVariable Long id,
                                      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        LoginUser user = requireCurrent();
        return Result.success(plotService.water(user.getFamilyId(), user.getUserId(), id, opIdOf(idempotencyKey)));
    }

    @Operation(summary = "晒太阳")
    @OperationLog(module = "GARDEN", operationType = "UPDATE", description = "给地块晒太阳")
    @PostMapping("/plot/{id}/sun")
    public Result<PlotActionVO> sun(@PathVariable Long id,
                                    @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        LoginUser user = requireCurrent();
        return Result.success(plotService.sun(user.getFamilyId(), user.getUserId(), id, opIdOf(idempotencyKey)));
    }

    @Operation(summary = "施肥")
    @OperationLog(module = "GARDEN", operationType = "UPDATE", description = "给地块施肥")
    @PostMapping("/plot/{id}/fertilize")
    public Result<PlotActionVO> fertilize(@PathVariable Long id,
                                          @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        LoginUser user = requireCurrent();
        return Result.success(plotService.fertilize(user.getFamilyId(), user.getUserId(), id, opIdOf(idempotencyKey)));
    }

    @Operation(summary = "收获入仓")
    @OperationLog(module = "GARDEN", operationType = "UPDATE", description = "收获地块")
    @PostMapping("/plot/{id}/harvest")
    public Result<PlotActionVO> harvest(@PathVariable Long id,
                                        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        LoginUser user = requireCurrent();
        return Result.success(plotService.harvest(user.getFamilyId(), user.getUserId(), id, opIdOf(idempotencyKey)));
    }

    // ---- 任务链与节气推进(详细设计 §3/§4/§5;写入用服务端确定性 op_id 幂等,Idempotency-Key 头可省)----

    private String worldSettingsOf(LoginUser user) {
        GardenWorld world = worldService.ensureWorld(user.getFamilyId());
        return world == null ? null : world.getSettings();
    }

    @Operation(summary = "任务面板(五层任务 + 推进面板)")
    @GetMapping("/tasks")
    public Result<Map<String, Object>> tasks() {
        LoginUser user = requireCurrent();
        return Result.success(taskService.panel(user.getFamilyId(), worldSettingsOf(user), user.getUserId() == null
                ? null : worldService.requireMember(user.getFamilyId(), user.getUserId()).getPlayerUid()));
    }

    @Operation(summary = "领取任务奖励")
    @PostMapping("/tasks/{code}/claim")
    public Result<Map<String, Object>> claimTask(@PathVariable String code) {
        LoginUser user = requireCurrent();
        var me = worldService.requireMember(user.getFamilyId(), user.getUserId());
        return Result.success(taskService.claim(user.getFamilyId(), me, worldSettingsOf(user), code));
    }

    @Operation(summary = "市集在售挂单")
    @GetMapping("/market")
    public Result<List<Map<String, Object>>> market() {
        LoginUser user = requireCurrent();
        var me = worldService.requireMember(user.getFamilyId(), user.getUserId());
        return Result.success(marketService.listings(user.getFamilyId(), me.getPlayerUid()));
    }

    @Operation(summary = "上架挂单(成员互市)")
    @OperationLog(module = "GARDEN", operationType = "CREATE", description = "市集上架")
    @PostMapping("/market/list")
    public Result<Map<String, Object>> marketList(@RequestBody Map<String, Object> body,
                                                  @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        LoginUser user = requireCurrent();
        var me = worldService.requireMember(user.getFamilyId(), user.getUserId());
        String itemCode = body == null ? null : (String) body.get("itemCode");
        Integer qty = body == null || body.get("qty") == null ? null : Integer.valueOf(String.valueOf(body.get("qty")));
        Integer price = body == null || body.get("unitPrice") == null ? null : Integer.valueOf(String.valueOf(body.get("unitPrice")));
        return Result.success(marketService.list(user.getFamilyId(), me, itemCode, qty, price, opIdOf(idempotencyKey)));
    }

    @Operation(summary = "买下挂单(家庭内循环)")
    @OperationLog(module = "GARDEN", operationType = "UPDATE", description = "市集购买")
    @PostMapping("/market/{id}/buy")
    public Result<Map<String, Object>> marketBuy(@PathVariable Long id,
                                                 @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        LoginUser user = requireCurrent();
        var me = worldService.requireMember(user.getFamilyId(), user.getUserId());
        return Result.success(marketService.buy(user.getFamilyId(), me, id, opIdOf(idempotencyKey)));
    }

    @Operation(summary = "下架自己的挂单(货回仓库)")
    @PostMapping("/market/{id}/cancel")
    public Result<Map<String, Object>> marketCancel(@PathVariable Long id,
                                                    @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        LoginUser user = requireCurrent();
        var me = worldService.requireMember(user.getFamilyId(), user.getUserId());
        return Result.success(marketService.cancel(user.getFamilyId(), me, id, opIdOf(idempotencyKey)));
    }

    @Operation(summary = "系统代售:园币买种子包")
    @OperationLog(module = "GARDEN", operationType = "CREATE", description = "购买种子")
    @PostMapping("/shop/buy")
    public Result<Map<String, Object>> shopBuy(@RequestBody Map<String, Object> body,
                                               @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        LoginUser user = requireCurrent();
        var me = worldService.requireMember(user.getFamilyId(), user.getUserId());
        String cropCode = body == null ? null : (String) body.get("cropCode");
        Integer qty = body == null || body.get("qty") == null ? null : Integer.valueOf(String.valueOf(body.get("qty")));
        GardenWorld world = worldService.ensureWorld(user.getFamilyId());
        int stage = worldService.stageOf(world);
        return Result.success(marketService.shopBuy(user.getFamilyId(), me, cropCode, qty, stage,
                opIdOf(idempotencyKey)));
    }

    @Operation(summary = "卖出给系统(系统收购,参考价)")
    @OperationLog(module = "GARDEN", operationType = "UPDATE", description = "卖出果实")
    @PostMapping("/sell")
    public Result<Map<String, Object>> sell(@RequestBody Map<String, Object> body,
                                            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        LoginUser user = requireCurrent();
        var me = worldService.requireMember(user.getFamilyId(), user.getUserId());
        String itemCode = body == null ? null : (String) body.get("itemCode");
        Integer qty = body == null || body.get("qty") == null ? null
                : Integer.valueOf(String.valueOf(body.get("qty")));
        return Result.success(sellService.sell(user.getFamilyId(), me, itemCode, qty, opIdOf(idempotencyKey)));
    }

    @Operation(summary = "批量播种(一畦齐播):一次确认播同种,逐盆校验扣费流水")
    @OperationLog(module = "GARDEN", operationType = "CREATE", description = "批量播种")
    @PostMapping("/plot/batch-sow")
    public Result<Map<String, Object>> batchSow(@RequestBody BatchSowRequest dto,
                                                @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        LoginUser user = requireCurrent();
        return Result.success(plotService.batchSow(user.getFamilyId(), user.getUserId(), dto, opIdOf(idempotencyKey)));
    }

    @Operation(summary = "批量照料(巡园):同一动作一次确认,逐盆校验冷却与费用")
    @OperationLog(module = "GARDEN", operationType = "UPDATE", description = "批量照料")
    @PostMapping("/plot/batch-care")
    public Result<Map<String, Object>> batchCare(@RequestBody BatchCareRequest dto,
                                                 @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        LoginUser user = requireCurrent();
        return Result.success(plotService.batchCare(user.getFamilyId(), user.getUserId(), dto, opIdOf(idempotencyKey)));
    }

    @Operation(summary = "推进阶段(主线链 + 节气 + 园币三闸门)")
    @PostMapping("/stage/advance")
    public Result<Map<String, Object>> advanceStage() {
        LoginUser user = requireCurrent();
        var me = worldService.requireMember(user.getFamilyId(), user.getUserId());
        return Result.success(taskService.advance(user.getFamilyId(), me, worldSettingsOf(user)));
    }

    @Operation(summary = "技能树(六树 P1 节点 + 点数)")
    @GetMapping("/skills")
    public Result<Map<String, Object>> skills() {
        LoginUser user = requireCurrent();
        worldService.ensureWorld(user.getFamilyId());
        return Result.success(growthService.skillsView(user.getFamilyId()));
    }

    @Operation(summary = "学手艺:提升一级(按层价扣点)")
    @PostMapping("/skill/{tree}/{node}")
    public Result<Map<String, Object>> allocateSkill(@PathVariable String tree, @PathVariable String node) {
        LoginUser user = requireCurrent();
        var me = worldService.requireMember(user.getFamilyId(), user.getUserId());
        worldService.ensureWorld(user.getFamilyId());
        return Result.success(growthService.allocate(user.getFamilyId(), me, tree, node));
    }

    @Operation(summary = "重置技能(每季免费一次)")
    @PostMapping("/skill/reset")
    public Result<Map<String, Object>> resetSkills() {
        LoginUser user = requireCurrent();
        var me = worldService.requireMember(user.getFamilyId(), user.getUserId());
        worldService.ensureWorld(user.getFamilyId());
        return Result.success(growthService.reset(user.getFamilyId(), me));
    }
}
