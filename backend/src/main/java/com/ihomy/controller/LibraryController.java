package com.ihomy.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ihomy.annotation.OperationLog;
import com.ihomy.common.BizException;
import com.ihomy.common.Result;
import com.ihomy.common.ResultCode;
import com.ihomy.dto.LibraryDTO;
import com.ihomy.entity.*;
import com.ihomy.security.SecurityHelper;
import com.ihomy.service.FileService;
import com.ihomy.service.LibraryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

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
    public Result<Map<String, Object>> list(@RequestParam(defaultValue = "1") int current,
                                            @RequestParam(defaultValue = "50") int size,
                                            @RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) Long categoryId,
                                            @RequestParam(required = false) String fileFormat,
                                            @RequestParam(required = false) String borrowStatus,
                                            @RequestParam(required = false) String sortBy) {
        SysUser user = securityHelper.currentUser();
        Long familyId = user == null ? null : user.getFamilyId();
        Long userId = user == null ? null : user.getId();
        boolean isOwner = securityHelper.isOwner();
        IPage<ContentBook> page = libraryService.page(current, size, familyId, userId, isOwner, keyword, categoryId, fileFormat, borrowStatus, sortBy);
        List<ContentBook> books = page.getRecords();
        // Attach categoryIds to each book
        List<Long> bookIds = books.stream().map(ContentBook::getId).toList();
        Map<Long, List<Long>> bookCatMap = libraryService.getBookCategoryIds(bookIds);
        List<Map<String, Object>> records = new ArrayList<>();
        for (ContentBook b : books) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", b.getId());
            m.put("title", b.getTitle());
            m.put("author", b.getAuthor());
            m.put("description", b.getDescription());
            m.put("coverUrl", b.getCoverUrl());
            m.put("fileUrl", b.getFileUrl());
            m.put("fileFormat", b.getFileFormat());
            m.put("fileSize", b.getFileSize());
            m.put("category", b.getCategory());
            m.put("categoryIds", bookCatMap.getOrDefault(b.getId(), List.of()));
            m.put("tags", b.getTags());
            m.put("status", b.getStatus());
            m.put("visibility", b.getVisibility());
            m.put("uploaderId", b.getUploaderId());
            m.put("familyId", b.getFamilyId());
            m.put("viewCount", b.getViewCount());
            m.put("likeCount", b.getLikeCount());
            m.put("createdAt", b.getCreatedAt());
            m.put("updatedAt", b.getUpdatedAt());
            records.add(m);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("records", records);
        data.put("total", page.getTotal());
        data.put("current", page.getCurrent());
        data.put("size", page.getSize());
        return Result.success(data);
    }

    @Operation(summary = "分类树")
    @GetMapping("/categories")
    public Result<List<BookCategory>> categories() {
        SysUser user = securityHelper.currentUser();
        Long familyId = user == null ? 1L : user.getFamilyId();
        return Result.success(libraryService.categoryTree(familyId));
    }

    @Operation(summary = "新增分类")
    @PostMapping("/categories")
    public Result<BookCategory> addCategory(@RequestBody Map<String, Object> body) {
        SysUser user = securityHelper.currentUser();
        if (user == null) throw new BizException(ResultCode.UNAUTHORIZED);
        String name = (String) body.get("name");
        Long parentId = body.get("parentId") != null ? ((Number) body.get("parentId")).longValue() : null;
        return Result.success(libraryService.addCategory(user.getFamilyId(), name, parentId));
    }

    @Operation(summary = "更新分类")
    @PutMapping("/categories/{id}")
    public Result<BookCategory> updateCategory(@PathVariable Long id, @RequestBody Map<String, String> body) {
        SysUser user = securityHelper.currentUser();
        if (user == null) throw new BizException(ResultCode.UNAUTHORIZED);
        return Result.success(libraryService.updateCategory(id, user.getFamilyId(), body.get("name")));
    }

    @Operation(summary = "删除分类")
    @DeleteMapping("/categories/{id}")
    public Result<Void> deleteCategory(@PathVariable Long id, @RequestParam(defaultValue = "move") String mode) {
        SysUser user = securityHelper.currentUser();
        if (user == null) throw new BizException(ResultCode.UNAUTHORIZED);
        libraryService.deleteCategory(id, user.getFamilyId(), mode);
        return Result.success();
    }

    @Operation(summary = "图书详情")
    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        SysUser user = securityHelper.currentUser();
        Long familyId = user == null ? null : user.getFamilyId();
        Long userId = user == null ? null : user.getId();
        boolean isOwner = securityHelper.isOwner();
        ContentBook book = libraryService.getDetail(id, familyId, userId, isOwner);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", book.getId());
        data.put("title", book.getTitle());
        data.put("author", book.getAuthor());
        data.put("description", book.getDescription());
        data.put("coverUrl", book.getCoverUrl());
        data.put("fileUrl", book.getFileUrl());
        data.put("fileFormat", book.getFileFormat());
        data.put("fileSize", book.getFileSize());
        data.put("category", book.getCategory());
        data.put("categoryIds", libraryService.getCategoryIdsByBookId(book.getId()));
        data.put("tags", book.getTags());
        data.put("status", book.getStatus());
        data.put("visibility", book.getVisibility());
        data.put("uploaderId", book.getUploaderId());
        data.put("familyId", book.getFamilyId());
        data.put("viewCount", book.getViewCount());
        data.put("likeCount", book.getLikeCount());
        data.put("createdAt", book.getCreatedAt());
        data.put("updatedAt", book.getUpdatedAt());
        return Result.success(data);
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

    @Operation(summary = "批量删除图书")
    @OperationLog(module = "LIBRARY", operationType = "DELETE", description = "批量删除图书")
    @DeleteMapping("/batch")
    public Result<Void> batchDelete(@RequestBody Map<String, Object> body) {
        SysUser user = securityHelper.currentUser();
        if (user == null) throw new BizException(ResultCode.UNAUTHORIZED);
        @SuppressWarnings("unchecked")
        List<Number> ids = (List<Number>) body.get("ids");
        if (ids == null || ids.isEmpty()) throw new BizException(ResultCode.BAD_REQUEST);
        List<Long> longIds = ids.stream().map(Number::longValue).toList();
        libraryService.batchDelete(longIds, user.getFamilyId(), user.getId(), securityHelper.isOwner());
        return Result.success();
    }

    @Operation(summary = "批量移动分类")
    @OperationLog(module = "LIBRARY", operationType = "UPDATE", description = "批量移动分类")
    @PutMapping("/batch/move")
    public Result<Void> batchMoveCategory(@RequestBody Map<String, Object> body) {
        SysUser user = securityHelper.currentUser();
        if (user == null) throw new BizException(ResultCode.UNAUTHORIZED);
        @SuppressWarnings("unchecked")
        List<Number> idNums = (List<Number>) body.get("ids");
        Long categoryId = body.get("categoryId") != null ? ((Number) body.get("categoryId")).longValue() : null;
        if (idNums == null || idNums.isEmpty()) throw new BizException(ResultCode.BAD_REQUEST);
        List<Long> bookIds = idNums.stream().map(Number::longValue).toList();
        libraryService.batchMoveCategory(bookIds, categoryId);
        return Result.success();
    }

    @Operation(summary = "更新阅读状态")
    @PutMapping("/{id}/borrow")
    public Result<BookBorrow> updateBorrow(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        SysUser user = securityHelper.currentUser();
        if (user == null) throw new BizException(ResultCode.UNAUTHORIZED);
        String status = (String) body.get("status");
        Integer progress = body.get("progress") != null ? ((Number) body.get("progress")).intValue() : null;
        String cfi = (String) body.get("cfi");
        return Result.success(libraryService.updateBorrowStatus(id, user.getId(), user.getFamilyId(), status, progress, cfi));
    }

    @Operation(summary = "获取阅读状态")
    @GetMapping("/{id}/borrow")
    public Result<BookBorrow> getBorrow(@PathVariable Long id) {
        SysUser user = securityHelper.currentUser();
        if (user == null) return Result.success(null);
        return Result.success(libraryService.getBorrowStatus(id, user.getId()));
    }

    // === Bookmarks ===

    @Operation(summary = "书签列表")
    @GetMapping("/{id}/bookmarks")
    public Result<List<BookBookmark>> getBookmarks(@PathVariable Long id) {
        SysUser user = securityHelper.currentUser();
        if (user == null) return Result.success(List.of());
        return Result.success(libraryService.getBookmarks(id, user.getId()));
    }

    @Operation(summary = "新增书签")
    @PostMapping("/{id}/bookmarks")
    public Result<BookBookmark> addBookmark(@PathVariable Long id, @RequestBody Map<String, String> body) {
        SysUser user = securityHelper.currentUser();
        if (user == null) throw new BizException(ResultCode.UNAUTHORIZED);
        return Result.success(libraryService.addBookmark(id, user.getId(), user.getFamilyId(), body.get("cfi"), body.get("label")));
    }

    @Operation(summary = "删除书签")
    @DeleteMapping("/bookmarks/{bmId}")
    public Result<Void> deleteBookmark(@PathVariable Long bmId) {
        SysUser user = securityHelper.currentUser();
        if (user == null) throw new BizException(ResultCode.UNAUTHORIZED);
        libraryService.deleteBookmark(bmId, user.getId());
        return Result.success();
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
