package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ihomy.common.BizException;
import com.ihomy.common.DictConst;
import com.ihomy.common.ResultCode;
import com.ihomy.dto.LibraryDTO;
import com.ihomy.entity.BookBorrow;
import com.ihomy.entity.ContentBook;
import com.ihomy.mapper.BookBorrowMapper;
import com.ihomy.mapper.ContentBookMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 电子图书业务:家庭书架 CRUD + 阅读状态跟踪。
 * 可见性与博客一致:OWNER 见全家,成员见自己+家庭可见+公开,游客仅公开。
 */
@Service
@RequiredArgsConstructor
public class LibraryService {

    private final ContentBookMapper bookMapper;
    private final BookBorrowMapper borrowMapper;
    private final FileService fileService;

    /** 分页查询:按家庭/可见范围过滤,支持关键字+分类 */
    public IPage<ContentBook> page(int current, int size, Long familyId, Long currentUserId, boolean isOwner, String keyword, String category) {
        LambdaQueryWrapper<ContentBook> qw = new LambdaQueryWrapper<>();
        if (familyId != null) {
            qw.eq(ContentBook::getFamilyId, familyId);
            if (isOwner) {
            } else if (currentUserId != null) {
                qw.and(w -> w.eq(ContentBook::getUploaderId, currentUserId)
                            .or().in(ContentBook::getVisibility, DictConst.VIS_FAMILY, DictConst.VIS_PUBLIC));
            } else {
                qw.eq(ContentBook::getVisibility, DictConst.VIS_PUBLIC);
            }
        } else {
            qw.eq(ContentBook::getVisibility, DictConst.VIS_PUBLIC);
        }
        qw.eq(ContentBook::getStatus, DictConst.BLOG_PUBLISHED)
          .eq(StringUtils.hasText(category), ContentBook::getCategory, category)
          .and(StringUtils.hasText(keyword), w -> w.like(ContentBook::getTitle, keyword).or().like(ContentBook::getAuthor, keyword))
          .orderByDesc(ContentBook::getCreatedAt);
        return bookMapper.selectPage(new Page<>(current, size), qw);
    }

    /** 家庭级分类列表 */
    public List<String> categories(Long familyId) {
        return bookMapper.selectCategoriesByFamily(familyId);
    }

    /** 新增分类(仅校验,分类是 book.category 字段,无独立表) */
    public void addCategory(Long familyId, String name) {
        if (!StringUtils.hasText(name)) throw new BizException(ResultCode.BAD_REQUEST);
        List<String> existing = bookMapper.selectCategoriesByFamily(familyId);
        if (existing.contains(name)) throw new BizException(ResultCode.BAD_REQUEST);
    }

    /** 重命名分类 */
    public void renameCategory(Long familyId, String oldName, String newName) {
        if (!StringUtils.hasText(oldName) || !StringUtils.hasText(newName)) throw new BizException(ResultCode.BAD_REQUEST);
        List<String> existing = bookMapper.selectCategoriesByFamily(familyId);
        if (!existing.contains(oldName)) throw new BizException(ResultCode.NOT_FOUND);
        if (existing.contains(newName) && !oldName.equals(newName)) throw new BizException(ResultCode.BAD_REQUEST);
        bookMapper.renameCategory(familyId, oldName, newName);
    }

    /** 删除分类:mode=move 清空分类名,mode=delete 软删图书 */
    public void deleteCategory(Long familyId, String category, String mode) {
        if (!StringUtils.hasText(category)) throw new BizException(ResultCode.BAD_REQUEST);
        if ("delete".equals(mode)) {
            LambdaQueryWrapper<ContentBook> qw = new LambdaQueryWrapper<>();
            qw.eq(ContentBook::getFamilyId, familyId).eq(ContentBook::getCategory, category);
            bookMapper.delete(qw);
        } else {
            bookMapper.clearCategory(familyId, category);
        }
    }

    /** 详情:校验可见性与家庭归属后累加浏览量 */
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

    /** 新建图书 */
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
        return book;
    }

    /** 更新图书:仅上传者或家长 */
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
        return book;
    }

    /** 删除图书:硬删记录+删文件(上传者或家长) */
    public void delete(Long id, Long familyId, Long currentUserId, boolean isOwner) {
        ContentBook book = bookMapper.selectById(id);
        if (book == null) throw new BizException(ResultCode.NOT_FOUND);
        if (!isOwner && !book.getUploaderId().equals(currentUserId)) throw new BizException(ResultCode.FORBIDDEN);
        bookMapper.deletePhysicalById(id);
        fileService.deleteByUrl(book.getFileUrl());
        fileService.deleteByUrl(book.getCoverUrl());
    }

    /** 更新阅读状态 */
    public BookBorrow updateBorrowStatus(Long bookId, Long userId, Long familyId, String status, Integer progress) {
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
            borrowMapper.insert(borrow);
        } else {
            if (status != null) borrow.setStatus(status);
            if (progress != null) borrow.setProgress(progress);
            borrowMapper.updateById(borrow);
        }
        return borrow;
    }

    /** 获取当前用户的阅读状态 */
    public BookBorrow getBorrowStatus(Long bookId, Long userId) {
        if (userId == null) return null;
        LambdaQueryWrapper<BookBorrow> qw = new LambdaQueryWrapper<>();
        qw.eq(BookBorrow::getBookId, bookId).eq(BookBorrow::getUserId, userId).eq(BookBorrow::getDeleted, 0);
        return borrowMapper.selectOne(qw);
    }

    /** 从文件URL推断格式 */
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
