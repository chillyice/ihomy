package com.ihomy.controller;

import com.ihomy.annotation.OperationLog;
import com.ihomy.common.Result;
import com.ihomy.dto.MindMapDTO;
import com.ihomy.entity.ContentMindmap;
import com.ihomy.entity.ContentMindmapSnapshot;
import com.ihomy.security.LoginUser;
import com.ihomy.security.SecurityHelper;
import com.ihomy.service.MindMapService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 工具箱-脑图设计:家庭级共享思维导图,家庭成员均可查看与编辑。
 */
@Tag(name = "工具箱-脑图")
@RestController
@RequestMapping("/mindmap")
@RequiredArgsConstructor
public class MindMapController {

    private final MindMapService mindMapService;
    private final SecurityHelper securityHelper;

    private LoginUser current() {
        return securityHelper.current();
    }

    @Operation(summary = "脑图列表(不含图数据)")
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list() {
        return Result.success(mindMapService.list(current().getFamilyId()));
    }

    @Operation(summary = "脑图详情(含图数据)")
    @GetMapping("/{id}")
    public Result<ContentMindmap> get(@PathVariable Long id) {
        return Result.success(mindMapService.get(id, current().getFamilyId()));
    }

    @Operation(summary = "创建脑图")
    @OperationLog(module = "MINDMAP", operationType = "CREATE", description = "创建脑图")
    @PostMapping
    public Result<ContentMindmap> create(@RequestBody MindMapDTO dto) {
        LoginUser user = current();
        return Result.success(mindMapService.create(user.getUserId(), user.getFamilyId(), dto));
    }

    @Operation(summary = "更新脑图(标题/图数据)")
    @OperationLog(module = "MINDMAP", operationType = "UPDATE", description = "更新脑图")
    @PutMapping("/{id}")
    public Result<ContentMindmap> update(@PathVariable Long id, @RequestBody MindMapDTO dto) {
        return Result.success(mindMapService.update(id, current().getFamilyId(), dto));
    }

    @Operation(summary = "删除脑图")
    @OperationLog(module = "MINDMAP", operationType = "DELETE", description = "删除脑图")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        mindMapService.delete(id, current().getFamilyId());
        return Result.success();
    }

    @Operation(summary = "回收站列表(已删除脑图)")
    @GetMapping("/trash")
    public Result<List<Map<String, Object>>> trash() {
        return Result.success(mindMapService.trash(current().getFamilyId()));
    }

    @Operation(summary = "从回收站恢复脑图")
    @OperationLog(module = "MINDMAP", operationType = "UPDATE", description = "恢复脑图")
    @PutMapping("/{id}/restore")
    public Result<Void> restore(@PathVariable Long id) {
        mindMapService.restore(id, current().getFamilyId());
        return Result.success();
    }

    @Operation(summary = "彻底删除脑图(物理删)")
    @OperationLog(module = "MINDMAP", operationType = "DELETE", description = "彻底删除脑图")
    @DeleteMapping("/{id}/purge")
    public Result<Void> purge(@PathVariable Long id) {
        mindMapService.purge(id, current().getFamilyId());
        return Result.success();
    }

    // ---------- 历史版本快照 ----------

    @Operation(summary = "创建快照(source=MANUAL手动/AUTO自动,默认MANUAL)")
    @OperationLog(module = "MINDMAP", operationType = "CREATE", description = "创建脑图快照")
    @PostMapping("/{id}/snapshot")
    public Result<Void> snapshot(@PathVariable Long id, @RequestParam(defaultValue = "MANUAL") String source) {
        LoginUser user = current();
        mindMapService.snapshot(id, user.getUserId(), user.getFamilyId(), source);
        return Result.success();
    }

    @Operation(summary = "快照列表(不含 data 大字段)")
    @GetMapping("/{id}/snapshot/list")
    public Result<List<Map<String, Object>>> listSnapshots(@PathVariable Long id) {
        return Result.success(mindMapService.listSnapshots(id, current().getFamilyId()));
    }

    @Operation(summary = "快照详情(含 data,回滚预览用)")
    @GetMapping("/{id}/snapshot/{snapshotId}")
    public Result<ContentMindmapSnapshot> getSnapshot(@PathVariable Long id, @PathVariable Long snapshotId) {
        return Result.success(mindMapService.getSnapshot(id, snapshotId, current().getFamilyId()));
    }

    @Operation(summary = "回滚到指定快照(当前内容自动备份为快照)")
    @OperationLog(module = "MINDMAP", operationType = "UPDATE", description = "回滚脑图快照")
    @PutMapping("/{id}/snapshot/{snapshotId}/restore")
    public Result<ContentMindmap> restoreSnapshot(@PathVariable Long id, @PathVariable Long snapshotId) {
        LoginUser user = current();
        return Result.success(mindMapService.restoreSnapshot(id, snapshotId, user.getUserId(), user.getFamilyId()));
    }

    @Operation(summary = "删除单份快照")
    @OperationLog(module = "MINDMAP", operationType = "DELETE", description = "删除脑图快照")
    @DeleteMapping("/{id}/snapshot/{snapshotId}")
    public Result<Void> deleteSnapshot(@PathVariable Long id, @PathVariable Long snapshotId) {
        mindMapService.deleteSnapshot(id, snapshotId, current().getFamilyId());
        return Result.success();
    }
}
