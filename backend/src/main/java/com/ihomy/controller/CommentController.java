package com.ihomy.controller;

import com.ihomy.annotation.OperationLog;
import com.ihomy.annotation.RequirePermission;
import com.ihomy.common.Result;
import com.ihomy.dto.CommentDTO;
import com.ihomy.entity.Comment;
import com.ihomy.entity.SysUser;
import com.ihomy.security.SecurityHelper;
import com.ihomy.service.CommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 评论接口:评论树列表(游客可读)、发表/删除(需 comment 权限码)。
 */
@Tag(name = "评论")
@RestController
@RequestMapping("/comment")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;
    private final SecurityHelper securityHelper;

    @Operation(summary = "评论列表（含回复树）")
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list(@RequestParam String contentType, @RequestParam Long contentId) {
        return Result.success(commentService.list(contentType, contentId));
    }

    @Operation(summary = "发表评论/回复")
    @RequirePermission("comment:create")
    @OperationLog(module = "COMMENT", operationType = "CREATE", description = "发表评论", saveArgs = false)
    @PostMapping
    public Result<Comment> create(@RequestBody CommentDTO dto) {
        return Result.success(commentService.create(securityHelper.currentUser(), dto));
    }

    @Operation(summary = "删除评论")
    @RequirePermission("comment:delete")
    @OperationLog(module = "COMMENT", operationType = "DELETE", description = "删除评论", saveArgs = false)
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        commentService.delete(id, securityHelper.currentUser(), securityHelper.isOwner());
        return Result.success();
    }
}
