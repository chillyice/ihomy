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

/**
 * 书架服务:图书分页/增删改、分类树(删分类递归)、借阅状态、书签(EPUB CFI 定位)、在线阅读。
 * 书与分类是多对多:分类树在 content_book_category,归属关系在 content_book_category_rel;
 * 删分类只清关系行,书籍本身保留(变成未分类),不因删类而删书。
 * 可见性口径:家长(isOwner)看全部;非家长看自己上传的 + 家庭/公开的;未登录只看公开。
 */
@Service
@RequiredArgsConstructor
public class LibraryService {

    private final ContentBookMapper bookMapper;
    private final BookBorrowMapper borrowMapper;
    private final BookCategoryMapper categoryMapper;
    private final BookBookmarkMapper bookmarkMapper;
    private final FileService fileService;

    // ponytail: 关系表查询本可拆独立 Mapper 更规整,家庭数据量小,先复用 bookMapper 的自定义 XML

    public IPage<ContentBook> page(int current, int size, Long familyId, Long currentUserId, boolean isOwner,
                                   String keyword, Long categoryId, String fileFormat, String borrowStatus, String sortBy) {
        // 按分类筛选:先从关系表取出书 ID 集,再与后面的查询条件取交集
        Set<Long> bookIds = null;
        if (categoryId != null) {
            bookIds = getBookIdsByCategory(categoryId);
            if (bookIds.isEmpty()) return new Page<>(current, size);
        }

        // 按借阅状态筛选:同样先查借阅表拿到书 ID 集,与分类条件取交集(取交集是因为两个条件都要满足)
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
            // 按最近阅读时间排序需 JOIN 阅读进度表,当前回退为按创建时间倒序
            qw.orderByDesc(ContentBook::getCreatedAt);
        } else {
            qw.orderByDesc(ContentBook::getCreatedAt);
        }
        return bookMapper.selectPage(new Page<>(current, size), qw);
    }

    public Map<Long, List<Long>> getBookCategoryIds(List<Long> bookIds) {
        if (bookIds == null || bookIds.isEmpty()) return Map.of();
        // ponytail: 逐本查关系表(未批量),家庭书架数据量小,先不优化
        Map<Long, List<Long>> result = new HashMap<>();
        for (Long bookId : bookIds) {
            result.put(bookId, getCategoryIdsByBookId(bookId));
        }
        return result;
    }

    public List<Long> getCategoryIdsByBookId(Long bookId) {
        // ponytail: 关系表未单独建 Mapper,复用 bookMapper 的自定义 XML
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
        // 递归删子分类(先深后浅),否则子分类会挂在已删父节点下成为孤儿
        List<BookCategory> children = categoryMapper.selectList(new LambdaQueryWrapper<BookCategory>()
                .eq(BookCategory::getParentId, id).eq(BookCategory::getDeleted, 0));
        for (BookCategory child : children) {
            deleteCategory(child.getId(), familyId, mode);
        }
        // 先清关系行,避免中间表留下指向已删分类的悬空引用
        bookMapper.deleteRelByCategory(id);
        // mode=delete 时按设计应连同「只属于本分类」的书一并处理,当前简化实现只清关系(书变未分类)
        if ("delete".equals(mode)) {
            // ponytail: 简化处理——只删关系,书本身保留为未分类
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
        // 逻辑删入回收站;分类关系与文件保留至彻底删除
        bookMapper.softDeleteById(id);
    }

    /** 批量删除:返回未能删除的 id 列表(无权限/已不存在),供前端提示,不再静默吞掉失败 */
    @Transactional
    public List<Long> batchDelete(List<Long> ids, Long familyId, Long currentUserId, boolean isOwner) {
        List<Long> failed = new ArrayList<>();
        for (Long id : ids) {
            // delete 在写入前完成归属/权限校验,抛错即跳过(不污染本事务)
            try {
                delete(id, familyId, currentUserId, isOwner);
            } catch (Exception e) {
                failed.add(id);
            }
        }
        return failed;
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

    // ========== 书签(仅本人可见) ==========

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

    // ========== 私有辅助 ==========

    private void saveCategoryRels(Long bookId, List<Long> categoryIds) {
        if (categoryIds == null) return;
        for (Long catId : categoryIds) {
            bookMapper.insertRel(bookId, catId);
        }
    }

    private Set<Long> getBookIdsByCategory(Long categoryId) {
        // ponytail: 走 bookMapper 的自定义 XML
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
