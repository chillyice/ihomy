package com.ihomy.controller;

import com.ihomy.annotation.OperationLog;
import com.ihomy.common.Result;
import com.ihomy.dto.MindMapDTO;
import com.ihomy.entity.ContentMindmap;
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
}
