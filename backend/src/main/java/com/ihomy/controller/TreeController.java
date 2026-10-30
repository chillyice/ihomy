package com.ihomy.controller;

import com.ihomy.annotation.OperationLog;
import com.ihomy.common.Result;
import com.ihomy.dto.TreeImportDTO;
import com.ihomy.dto.TreeMemberDTO;
import com.ihomy.dto.TreeRelationDTO;
import com.ihomy.entity.FamilyTreeMember;
import com.ihomy.security.LoginUser;
import com.ihomy.security.SecurityHelper;
import com.ihomy.service.FamilyTreeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 家谱接口:家庭成员树数据(列表返回全部成员+父母/配偶姓名,前端组世代视图)。
 * 家谱属家庭隐私数据,全部接口仅登录家庭成员可访问(访客不可见)。
 */
@Tag(name = "家谱")
@RestController
@RequestMapping("/tree")
@RequiredArgsConstructor
public class TreeController {

    private final FamilyTreeService treeService;
    private final SecurityHelper securityHelper;

    private Long familyId() {
        return securityHelper.current().getFamilyId();
    }

    @Operation(summary = "家谱成员列表(含关系姓名)")
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list() {
        return Result.success(treeService.list(familyId()));
    }

    @Operation(summary = "新增家谱成员")
    @OperationLog(module = "TREE", operationType = "CREATE", description = "新增家谱成员", saveArgs = false)
    @PostMapping
    public Result<FamilyTreeMember> create(@RequestBody TreeMemberDTO dto) {
        return Result.success(treeService.create(familyId(), dto));
    }

    @Operation(summary = "编辑家谱成员")
    @OperationLog(module = "TREE", operationType = "UPDATE", description = "编辑家谱成员", saveArgs = false)
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody TreeMemberDTO dto) {
        treeService.update(id, familyId(), dto);
        return Result.success();
    }

    @Operation(summary = "删除家谱成员(清空他人父/母/配偶引用)")
    @OperationLog(module = "TREE", operationType = "DELETE", description = "删除家谱成员", saveArgs = false)
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        treeService.delete(id, familyId());
        return Result.success();
    }

    @Operation(summary = "从家庭成员导入家谱")
    @OperationLog(module = "TREE", operationType = "IMPORT", description = "导入家庭成员到家谱", saveArgs = false)
    @PostMapping("/import-members")
    public Result<Map<String, Object>> importMembers(@RequestBody(required = false) TreeImportDTO dto) {
        int count = treeService.importMembers(familyId(), dto == null ? null : dto.getUserIds());
        return Result.success(Map.of("count", count));
    }

    @Operation(summary = "建立家谱成员关系(只改关系,不动其他资料)")
    @OperationLog(module = "TREE", operationType = "UPDATE", description = "建立家谱成员关系", saveArgs = false)
    @PostMapping("/relation")
    public Result<Void> link(@RequestBody @Valid TreeRelationDTO dto) {
        treeService.link(familyId(), dto.getFromId(), dto.getToId(), dto.getKind());
        return Result.success();
    }

    @Operation(summary = "解除家谱成员关系")
    @OperationLog(module = "TREE", operationType = "DELETE", description = "解除家谱成员关系", saveArgs = false)
    @DeleteMapping("/relation")
    public Result<Void> unlink(@RequestParam Long fromId, @RequestParam Long toId, @RequestParam String kind) {
        treeService.unlink(familyId(), fromId, toId, kind);
        return Result.success();
    }
}