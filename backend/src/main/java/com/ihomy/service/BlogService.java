package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ihomy.common.BizException;
import com.ihomy.common.DictConst;
import com.ihomy.common.ResultCode;
import com.ihomy.dto.BlogDTO;
import com.ihomy.entity.Blog;
import com.ihomy.mapper.BlogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * 博客业务:分页查询按家庭/可见范围过滤,增删改校验归属与权限。
 * 可见性:0仅自己/1指定成员/2指定群组/3家庭可见/4公开。
 */
@Service
@RequiredArgsConstructor
public class BlogService {

    private final BlogMapper blogMapper;
    private final PointsService pointsService;

    /** 分页查询:OWNER 见全家;成员见自己的+家庭可见/公开;游客仅公开 */
    public IPage<Blog> page(int current, int size, Long familyId, Long currentUserId, boolean isOwner, String keyword, String category) {
        LambdaQueryWrapper<Blog> qw = new LambdaQueryWrapper<>();
        if (familyId != null) {
            qw.eq(Blog::getFamilyId, familyId);
            if (isOwner) {
                // 家长可见全部
            } else if (currentUserId != null) {
                qw.and(w -> w.eq(Blog::getAuthorId, currentUserId)
                            .or().in(Blog::getVisibility, DictConst.VIS_FAMILY, DictConst.VIS_PUBLIC));
            } else {
                qw.eq(Blog::getVisibility, DictConst.VIS_PUBLIC);
            }
        } else {
            qw.eq(Blog::getVisibility, DictConst.VIS_PUBLIC);
        }
        qw.eq(Blog::getStatus, DictConst.BLOG_PUBLISHED)
          .eq(StringUtils.hasText(category), Blog::getCategory, category)
          .like(StringUtils.hasText(keyword), Blog::getTitle, keyword)
          .orderByDesc(Blog::getCreatedAt);
        return blogMapper.selectPage(new Page<>(current, size), qw);
    }

    /** 家庭级分类列表:从已发布博客中 DISTINCT 拉取,空分类不返回 */
    public List<String> categories(Long familyId) {
        return blogMapper.selectCategoriesByFamily(familyId);
    }

    /** 分类计数:按权限过滤后的全量数据统计每个分类的文章数 */
    public List<Map<String, Object>> categoryCounts(Long familyId, Long authorId, boolean isOwner) {
        return blogMapper.selectCategoryCounts(familyId, authorId, isOwner);
    }

    /** 新增分类:直接返回成功(分类是 blog.category 字段,无独立表;创建博客时自然产生) */
    public void addCategory(Long familyId, String name) {
        if (!StringUtils.hasText(name)) throw new BizException(ResultCode.BAD_REQUEST);
        List<String> existing = blogMapper.selectCategoriesByFamily(familyId);
        if (existing.contains(name)) throw new BizException(ResultCode.BAD_REQUEST);
    }

    /** 重命名分类:批量更新该家庭下所有博客的 category 字段 */
    public void renameCategory(Long familyId, String oldName, String newName) {
        if (!StringUtils.hasText(oldName) || !StringUtils.hasText(newName)) throw new BizException(ResultCode.BAD_REQUEST);
        List<String> existing = blogMapper.selectCategoriesByFamily(familyId);
        if (!existing.contains(oldName)) throw new BizException(ResultCode.NOT_FOUND);
        if (existing.contains(newName) && !oldName.equals(newName)) throw new BizException(ResultCode.BAD_REQUEST);
        blogMapper.renameCategory(familyId, oldName, newName);
    }

    /** 删除分类:mode=move 清空分类名(博客移入全部),mode=delete 删除该分类下所有博客 */
    public void deleteCategory(Long familyId, String category, String mode) {
        if (!StringUtils.hasText(category)) throw new BizException(ResultCode.BAD_REQUEST);
        if ("delete".equals(mode)) {
            blogMapper.deleteByCategory(familyId, category);
        } else {
            blogMapper.clearCategory(familyId, category);
        }
    }

    /** 详情:校验可见性与家庭归属后累加浏览量(跨家庭或不可见返回 404) */
    public Blog getDetail(Long id, Long familyId, Long currentUserId, boolean isOwner) {
        Blog blog = blogMapper.selectById(id);
        if (blog == null) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        boolean sameFamily = familyId != null && familyId.equals(blog.getFamilyId());
        boolean isAuthor = currentUserId != null && currentUserId.equals(blog.getAuthorId());
        boolean famOwner = isOwner && sameFamily;
        if (!DictConst.VIS_PUBLIC.equals(blog.getVisibility()) && !sameFamily) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        if (DictConst.VIS_PRIVATE.equals(blog.getVisibility()) && !isAuthor && !famOwner) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        if (!DictConst.BLOG_PUBLISHED.equals(blog.getStatus()) && !isAuthor && !famOwner) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        blogMapper.incrViewCount(id);
        return blog;
    }

    /** 新建博客:归属当前家庭,默认草稿状态、家庭可见 */
    public Blog create(Long authorId, Long familyId, BlogDTO dto) {
        Blog blog = new Blog();
        blog.setTitle(dto.getTitle());
        blog.setContent(dto.getContent());
        blog.setCoverImage(dto.getCoverImage());
        blog.setTags(dto.getTags());
        blog.setCategory(dto.getCategory());
        blog.setAuthorId(authorId);
        blog.setFamilyId(familyId);
        blog.setStatus(DictConst.blogStatus(dto.getStatus()));
        blog.setVisibility(DictConst.visibility(dto.getVisibility()));
        blog.setViewCount(0);
        blogMapper.insert(blog);
        pointsService.addRecord(authorId, familyId, "REWARD", PointsService.REWARD_BLOG, "发布博客");
        return blog;
    }

    /** 更新博客:仅作者本人可改,可部分更新标签/状态/可见性 */
    public Blog update(Long id, Long currentUserId, BlogDTO dto) {
        Blog blog = blogMapper.selectById(id);
        if (blog == null) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        if (!blog.getAuthorId().equals(currentUserId)) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        blog.setTitle(dto.getTitle());
        blog.setContent(dto.getContent());
        blog.setCoverImage(dto.getCoverImage());
        if (dto.getTags() != null) blog.setTags(dto.getTags());
        if (dto.getCategory() != null) blog.setCategory(dto.getCategory());
        if (dto.getStatus() != null) blog.setStatus(DictConst.blogStatus(dto.getStatus()));
        if (dto.getVisibility() != null) blog.setVisibility(DictConst.visibility(dto.getVisibility()));
        blogMapper.updateById(blog);
        return blog;
    }

    /** 删除博客:作者本人或家长可删 */
    public void delete(Long id, Long currentUserId, boolean isOwner) {
        Blog blog = blogMapper.selectById(id);
        if (blog == null) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        if (!isOwner && !blog.getAuthorId().equals(currentUserId)) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        blogMapper.deleteById(id);
    }
}
