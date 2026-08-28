package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ihomy.common.BizException;
import com.ihomy.common.DictConst;
import com.ihomy.common.ResultCode;
import com.ihomy.dto.LibraryDTO;
import com.ihomy.entity.*;
import com.ihomy.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LibraryService {

    private final ContentBookMapper bookMapper;
    private final BookBorrowMapper borrowMapper;
    private final BookCategoryMapper categoryMapper;
    private final BookBookmarkMapper bookmarkMapper;
    private final FileService fileService;

    // ponytail: raw JDBC via MyBatis XML would be cleaner but this is simpler for small data

    public IPage<ContentBook> page(int current, int size, Long familyId, Long currentUserId, boolean isOwner,
                                   String keyword, Long categoryId, String fileFormat, String borrowStatus, String sortBy) {
        // If filtering by category, get book IDs from rel table first
        Set<Long> bookIds = null;
        if (categoryId != null) {
            bookIds = getBookIdsByCategory(categoryId);
            if (bookIds.isEmpty()) return new Page<>(current, size);
        }

        // If filtering by borrow status, get book IDs from borrow table
        if (StringUtils.hasText(borrowStatus) && currentUserId != null) {
            Set<Long> borrowBookIds = getBookIdsByBorrowStatus(currentUserId, borrowStatus);
            bookIds = bookIds == null ? borrowBookIds : intersection(bookIds, borrowBookIds);
            if (bookIds.isEmpty()) return new Page<>(current, size);
        }

        LambdaQueryWrapper<ContentBook> qw = new LambdaQueryWrapper<>();
        if (familyId != null) {
            qw.eq(ContentBook::getFamilyId, familyId);
            if (!isOwner) {
                if (currentUserId != null) {
                    qw.and(w -> w.eq(ContentBook::getUploaderId, currentUserId)
                            .or().in(ContentBook::getVisibility, DictConst.VIS_FAMILY, DictConst.VIS_PUBLIC));
                } else {
                    qw.eq(ContentBook::getVisibility, DictConst.VIS_PUBLIC);
                }
            }
        } else {
            qw.eq(ContentBook::getVisibility, DictConst.VIS_PUBLIC);
        }
        qw.eq(ContentBook::getStatus, DictConst.BLOG_PUBLISHED)
                .and(StringUtils.hasText(keyword), w -> w.like(ContentBook::getTitle, keyword).or().like(ContentBook::getAuthor, keyword))
                .eq(StringUtils.hasText(fileFormat), ContentBook::getFileFormat, fileFormat)
                .in(bookIds != null, ContentBook::getId, bookIds != null ? bookIds : List.of(-1L));

        if ("title".equals(sortBy)) {
            qw.orderByAsc(ContentBook::getTitle);
        } else if ("recent".equals(sortBy) && currentUserId != null) {
            // Sort by last read time — need custom SQL, fallback to created_at
            qw.orderByDesc(ContentBook::getCreatedAt);
        } else {
            qw.orderByDesc(ContentBook::getCreatedAt);
        }
        return bookMapper.selectPage(new Page<>(current, size), qw);
    }

    public Map<Long, List<Long>> getBookCategoryIds(List<Long> bookIds) {
        if (bookIds == null || bookIds.isEmpty()) return Map.of();
        // ponytail: raw query via mapper would be cleaner; using selectList on rel table
        Map<Long, List<Long>> result = new HashMap<>();
        for (Long bookId : bookIds) {
            result.put(bookId, getCategoryIdsByBookId(bookId));
        }
        return result;
    }

    public List<Long> getCategoryIdsByBookId(Long bookId) {
        // ponytail: should use a dedicated mapper for rel table, using JDBC via bookMapper custom XML
        return bookMapper.selectCategoryIdsByBookId(bookId);
    }

    public List<BookCategory> categoryTree(Long familyId) {
        LambdaQueryWrapper<BookCategory> qw = new LambdaQueryWrapper<>();
        qw.eq(BookCategory::getFamilyId, familyId).eq(BookCategory::getDeleted, 0).orderByAsc(BookCategory::getSortOrder);
        return categoryMapper.selectList(qw);
    }

    @Transactional
    public BookCategory addCategory(Long familyId, String name, Long parentId) {
        if (!StringUtils.hasText(name)) throw new BizException(ResultCode.BAD_REQUEST);
        BookCategory cat = new BookCategory();
        cat.setName(name.trim());
        cat.setFamilyId(familyId);
        cat.setParentId(parentId != null ? parentId : 0L);
        cat.setSortOrder(0);
        categoryMapper.insert(cat);
        return cat;
    }

    @Transactional
    public BookCategory updateCategory(Long id, Long familyId, String name, Long parentId) {
        BookCategory cat = categoryMapper.selectById(id);
        if (cat == null || !cat.getFamilyId().equals(familyId)) throw new BizException(ResultCode.NOT_FOUND);
        if (StringUtils.hasText(name)) cat.setName(name.trim());
        if (parentId != null) {
            if (parentId.equals(id)) throw new BizException(ResultCode.BAD_REQUEST);
            BookCategory parent = categoryMapper.selectById(parentId);
            if (parent == null || !parent.getFamilyId().equals(familyId)) throw new BizException(ResultCode.NOT_FOUND);
            cat.setParentId(parentId);
        } else {
            cat.setParentId(0L);
        }
        categoryMapper.updateById(cat);
        return cat;
    }

    @Transactional
    public void deleteCategory(Long id, Long familyId, String mode) {
        BookCategory cat = categoryMapper.selectById(id);
        if (cat == null || !cat.getFamilyId().equals(familyId)) throw new BizException(ResultCode.NOT_FOUND);
        // Delete children recursively
        List<BookCategory> children = categoryMapper.selectList(new LambdaQueryWrapper<BookCategory>()
                .eq(BookCategory::getParentId, id).eq(BookCategory::getDeleted, 0));
        for (BookCategory child : children) {
            deleteCategory(child.getId(), familyId, mode);
        }
        // Delete rel records
        bookMapper.deleteRelByCategory(id);
        if ("delete".equals(mode)) {
            // Soft-delete books that only have this category
            // ponytail: simplified — just remove rel, books become uncategorized
        }
        categoryMapper.deleteById(id);
    }

    @Transactional
    public ContentBook create(Long uploaderId, Long familyId, LibraryDTO dto) {
        ContentBook book = new ContentBook();
        book.setTitle(dto.getTitle());
        book.setAuthor(dto.getAuthor());
        book.setDescription(dto.getDescription());
        book.setCoverUrl(dto.getCoverUrl());
        book.setFileUrl(dto.getFileUrl());
        book.setFileFormat(dto.getFileFormat() != null ? dto.getFileFormat().toUpperCase() : detectFormat(dto.getFileUrl()));
        book.setFileSize(dto.getFileSize());
        book.setCategory(dto.getCategory());
        book.setTags(dto.getTags());
        book.setUploaderId(uploaderId);
        book.setFamilyId(familyId);
        book.setStatus(DictConst.blogStatus(dto.getStatus()));
        book.setVisibility(DictConst.visibility(dto.getVisibility()));
        book.setViewCount(0);
        book.setLikeCount(0);
        bookMapper.insert(book);
        saveCategoryRels(book.getId(), dto.getCategoryIds());
        return book;
    }

    @Transactional
    public ContentBook update(Long id, Long familyId, Long currentUserId, boolean isOwner, LibraryDTO dto) {
        ContentBook book = bookMapper.selectById(id);
        if (book == null) throw new BizException(ResultCode.NOT_FOUND);
        if (!isOwner && !book.getUploaderId().equals(currentUserId)) throw new BizException(ResultCode.FORBIDDEN);
        book.setTitle(dto.getTitle());
        book.setAuthor(dto.getAuthor());
        book.setDescription(dto.getDescription());
        if (dto.getCoverUrl() != null) book.setCoverUrl(dto.getCoverUrl());
        if (dto.getFileUrl() != null) {
            book.setFileUrl(dto.getFileUrl());
            book.setFileFormat(dto.getFileFormat() != null ? dto.getFileFormat().toUpperCase() : detectFormat(dto.getFileUrl()));
            book.setFileSize(dto.getFileSize());
        }
        if (dto.getCategory() != null) book.setCategory(dto.getCategory());
        if (dto.getTags() != null) book.setTags(dto.getTags());
        if (dto.getStatus() != null) book.setStatus(DictConst.blogStatus(dto.getStatus()));
        if (dto.getVisibility() != null) book.setVisibility(DictConst.visibility(dto.getVisibility()));
        bookMapper.updateById(book);
        if (dto.getCategoryIds() != null) {
            bookMapper.deleteRelByBookId(id);
            saveCategoryRels(id, dto.getCategoryIds());
        }
        return book;
    }

    @Transactional
    public void delete(Long id, Long familyId, Long currentUserId, boolean isOwner) {
        ContentBook book = bookMapper.selectById(id);
        if (book == null) throw new BizException(ResultCode.NOT_FOUND);
        if (!isOwner && !book.getUploaderId().equals(currentUserId)) throw new BizException(ResultCode.FORBIDDEN);
        bookMapper.deleteRelByBookId(id);
        bookMapper.deletePhysicalById(id);
        fileService.deleteByUrl(book.getFileUrl());
        fileService.deleteByUrl(book.getCoverUrl());
    }

    @Transactional
    public void batchDelete(List<Long> ids, Long familyId, Long currentUserId, boolean isOwner) {
        for (Long id : ids) {
            try { delete(id, familyId, currentUserId, isOwner); } catch (Exception ignored) {}
        }
    }

    @Transactional
    public void batchMoveCategory(List<Long> bookIds, Long categoryId) {
        for (Long bookId : bookIds) {
            bookMapper.deleteRelByBookId(bookId);
            if (categoryId != null) bookMapper.insertRel(bookId, categoryId);
        }
    }

    public ContentBook getDetail(Long id, Long familyId, Long currentUserId, boolean isOwner) {
        ContentBook book = bookMapper.selectById(id);
        if (book == null) throw new BizException(ResultCode.NOT_FOUND);
        boolean sameFamily = familyId != null && familyId.equals(book.getFamilyId());
        boolean isAuthor = currentUserId != null && currentUserId.equals(book.getUploaderId());
        boolean famOwner = isOwner && sameFamily;
        if (!DictConst.VIS_PUBLIC.equals(book.getVisibility()) && !sameFamily) throw new BizException(ResultCode.NOT_FOUND);
        if (DictConst.VIS_PRIVATE.equals(book.getVisibility()) && !isAuthor && !famOwner) throw new BizException(ResultCode.NOT_FOUND);
        if (!DictConst.BLOG_PUBLISHED.equals(book.getStatus()) && !isAuthor && !famOwner) throw new BizException(ResultCode.NOT_FOUND);
        bookMapper.incrViewCount(id);
        return book;
    }

    public BookBorrow updateBorrowStatus(Long bookId, Long userId, Long familyId, String status, Integer progress, String cfi) {
        LambdaQueryWrapper<BookBorrow> qw = new LambdaQueryWrapper<>();
        qw.eq(BookBorrow::getBookId, bookId).eq(BookBorrow::getUserId, userId).eq(BookBorrow::getDeleted, 0);
        BookBorrow borrow = borrowMapper.selectOne(qw);
        if (borrow == null) {
            borrow = new BookBorrow();
            borrow.setBookId(bookId);
            borrow.setUserId(userId);
            borrow.setFamilyId(familyId);
            borrow.setStatus(status != null ? status : DictConst.BORROW_WANT);
            borrow.setProgress(progress != null ? progress : 0);
            if (cfi != null) borrow.setCfi(cfi);
            borrowMapper.insert(borrow);
        } else {
            if (status != null) borrow.setStatus(status);
            if (progress != null) borrow.setProgress(progress);
            if (cfi != null) borrow.setCfi(cfi);
            borrowMapper.updateById(borrow);
        }
        return borrow;
    }

    public BookBorrow getBorrowStatus(Long bookId, Long userId) {
        if (userId == null) return null;
        LambdaQueryWrapper<BookBorrow> qw = new LambdaQueryWrapper<>();
        qw.eq(BookBorrow::getBookId, bookId).eq(BookBorrow::getUserId, userId).eq(BookBorrow::getDeleted, 0);
        return borrowMapper.selectOne(qw);
    }

    // === Bookmarks ===

    public List<BookBookmark> getBookmarks(Long bookId, Long userId) {
        LambdaQueryWrapper<BookBookmark> qw = new LambdaQueryWrapper<>();
        qw.eq(BookBookmark::getBookId, bookId).eq(BookBookmark::getUserId, userId).eq(BookBookmark::getDeleted, 0)
                .orderByDesc(BookBookmark::getCreatedAt);
        return bookmarkMapper.selectList(qw);
    }

    public BookBookmark addBookmark(Long bookId, Long userId, Long familyId, String cfi, String label) {
        BookBookmark bm = new BookBookmark();
        bm.setBookId(bookId);
        bm.setUserId(userId);
        bm.setFamilyId(familyId);
        bm.setCfi(cfi);
        bm.setLabel(label);
        bookmarkMapper.insert(bm);
        return bm;
    }

    public void deleteBookmark(Long id, Long userId) {
        BookBookmark bm = bookmarkMapper.selectById(id);
        if (bm == null || !bm.getUserId().equals(userId)) throw new BizException(ResultCode.NOT_FOUND);
        bookmarkMapper.deleteById(id);
    }

    // === Private helpers ===

    private void saveCategoryRels(Long bookId, List<Long> categoryIds) {
        if (categoryIds == null) return;
        for (Long catId : categoryIds) {
            bookMapper.insertRel(bookId, catId);
        }
    }

    private Set<Long> getBookIdsByCategory(Long categoryId) {
        // ponytail: using mapper custom XML
        List<Long> ids = bookMapper.selectBookIdsByCategory(categoryId);
        return new HashSet<>(ids);
    }

    private Set<Long> getBookIdsByBorrowStatus(Long userId, String status) {
        LambdaQueryWrapper<BookBorrow> qw = new LambdaQueryWrapper<>();
        qw.eq(BookBorrow::getUserId, userId).eq(BookBorrow::getStatus, status).eq(BookBorrow::getDeleted, 0);
        return borrowMapper.selectList(qw).stream().map(BookBorrow::getBookId).collect(Collectors.toSet());
    }

    private Set<Long> intersection(Set<Long> a, Set<Long> b) {
        Set<Long> r = new HashSet<>(a);
        r.retainAll(b);
        return r;
    }

    private String detectFormat(String fileUrl) {
        if (fileUrl == null) return DictConst.FMT_PDF;
        String lower = fileUrl.toLowerCase();
        if (lower.endsWith(".epub")) return DictConst.FMT_EPUB;
        if (lower.endsWith(".pdf")) return DictConst.FMT_PDF;
        if (lower.endsWith(".txt")) return DictConst.FMT_TXT;
        if (lower.endsWith(".mobi")) return DictConst.FMT_MOBI;
        return DictConst.FMT_PDF;
    }
}
