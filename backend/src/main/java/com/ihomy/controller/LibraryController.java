package com.ihomy.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ihomy.annotation.OperationLog;
import com.ihomy.common.BizException;
import com.ihomy.common.Result;
import com.ihomy.common.ResultCode;
import com.ihomy.dto.LibraryDTO;
import com.ihomy.entity.BookBorrow;
import com.ihomy.entity.ContentBook;
import com.ihomy.entity.SysUser;
import com.ihomy.security.SecurityHelper;
import com.ihomy.service.FileService;
import com.ihomy.service.LibraryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 电子图书接口:书架列表/上传/增删改/在线阅读/阅读状态。
 */
@Tag(name = "书架")
@RestController
@RequestMapping("/library")
@RequiredArgsConstructor
public class LibraryController {

    private final LibraryService libraryService;
    private final FileService fileService;
    private final SecurityHelper securityHelper;

    @Operation(summary = "图书分页列表")
    @GetMapping("/list")
    public Result<IPage<ContentBook>> list(@RequestParam(defaultValue = "1") int current,
                                           @RequestParam(defaultValue = "20") int size,
                                           @RequestParam(required = false) String keyword,
                                           @RequestParam(required = false) String category) {
        SysUser user = securityHelper.currentUser();
        Long familyId = user == null ? null : user.getFamilyId();
        Long userId = user == null ? null : user.getId();
        boolean isOwner = securityHelper.isOwner();
        return Result.success(libraryService.page(current, size, familyId, userId, isOwner, keyword, category));
    }

    @Operation(summary = "分类列表")
    @GetMapping("/categories")
    public Result<List<String>> categories() {
        SysUser user = securityHelper.currentUser();
        Long familyId = user == null ? 1L : user.getFamilyId();
        return Result.success(libraryService.categories(familyId));
    }

    @Operation(summary = "新增分类")
    @PostMapping("/categories")
    public Result<Void> addCategory(@RequestBody Map<String, String> body) {
        SysUser user = securityHelper.currentUser();
        if (user == null) throw new BizException(ResultCode.UNAUTHORIZED);
        libraryService.addCategory(user.getFamilyId(), body.get("name"));
        return Result.success();
    }

    @Operation(summary = "重命名分类")
    @PutMapping("/categories")
    public Result<Void> renameCategory(@RequestBody Map<String, String> body) {
        SysUser user = securityHelper.currentUser();
        if (user == null) throw new BizException(ResultCode.UNAUTHORIZED);
        libraryService.renameCategory(user.getFamilyId(), body.get("oldName"), body.get("newName"));
        return Result.success();
    }

    @Operation(summary = "删除分类")
    @DeleteMapping("/categories")
    public Result<Void> deleteCategory(@RequestParam String category, @RequestParam(defaultValue = "move") String mode) {
        SysUser user = securityHelper.currentUser();
        if (user == null) throw new BizException(ResultCode.UNAUTHORIZED);
        libraryService.deleteCategory(user.getFamilyId(), category, mode);
        return Result.success();
    }

    @Operation(summary = "图书详情")
    @GetMapping("/{id}")
    public Result<ContentBook> detail(@PathVariable Long id) {
        SysUser user = securityHelper.currentUser();
        Long familyId = user == null ? null : user.getFamilyId();
        Long userId = user == null ? null : user.getId();
        boolean isOwner = securityHelper.isOwner();
        return Result.success(libraryService.getDetail(id, familyId, userId, isOwner));
    }

    @Operation(summary = "上传电子书文件")
    @OperationLog(module = "LIBRARY", operationType = "CREATE", description = "上传电子书", saveArgs = false)
    @PostMapping("/upload")
    public Result<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) {
        String url = fileService.uploadBook(file, file.getOriginalFilename(), file.getContentType());
        Map<String, Object> data = new HashMap<>();
        data.put("url", url);
        data.put("fileSize", file.getSize());
        data.put("fileFormat", detectFormat(file.getOriginalFilename()));
        return Result.success(data);
    }

    @Operation(summary = "新增图书")
    @OperationLog(module = "LIBRARY", operationType = "CREATE", description = "新增图书")
    @PostMapping
    public Result<ContentBook> create(@Valid @RequestBody LibraryDTO dto) {
        SysUser user = securityHelper.currentUser();
        return Result.success(libraryService.create(user.getId(), user.getFamilyId(), dto));
    }

    @Operation(summary = "更新图书")
    @OperationLog(module = "LIBRARY", operationType = "UPDATE", description = "编辑图书")
    @PutMapping("/{id}")
    public Result<ContentBook> update(@PathVariable Long id, @Valid @RequestBody LibraryDTO dto) {
        SysUser user = securityHelper.currentUser();
        return Result.success(libraryService.update(id, user.getFamilyId(), user.getId(), securityHelper.isOwner(), dto));
    }

    @Operation(summary = "删除图书")
    @OperationLog(module = "LIBRARY", operationType = "DELETE", description = "删除图书")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        SysUser user = securityHelper.currentUser();
        libraryService.delete(id, user.getFamilyId(), user.getId(), securityHelper.isOwner());
        return Result.success();
    }

    @Operation(summary = "更新阅读状态")
    @PutMapping("/{id}/borrow")
    public Result<BookBorrow> updateBorrow(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        SysUser user = securityHelper.currentUser();
        if (user == null) throw new BizException(ResultCode.UNAUTHORIZED);
        String status = (String) body.get("status");
        Integer progress = body.get("progress") != null ? ((Number) body.get("progress")).intValue() : null;
        return Result.success(libraryService.updateBorrowStatus(id, user.getId(), user.getFamilyId(), status, progress));
    }

    @Operation(summary = "获取阅读状态")
    @GetMapping("/{id}/borrow")
    public Result<BookBorrow> getBorrow(@PathVariable Long id) {
        SysUser user = securityHelper.currentUser();
        if (user == null) return Result.success(null);
        return Result.success(libraryService.getBorrowStatus(id, user.getId()));
    }

    private String detectFormat(String filename) {
        if (filename == null) return "PDF";
        String lower = filename.toLowerCase();
        if (lower.endsWith(".epub")) return "EPUB";
        if (lower.endsWith(".pdf")) return "PDF";
        if (lower.endsWith(".txt")) return "TXT";
        if (lower.endsWith(".mobi")) return "MOBI";
        return "PDF";
    }
}
