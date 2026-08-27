package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ihomy.common.BizException;
import com.ihomy.common.DictConst;
import com.ihomy.common.ResultCode;
import com.ihomy.dto.BlogDTO;
import com.ihomy.entity.Blog;
import com.ihomy.entity.BlogCategory;
import com.ihomy.mapper.BlogCategoryMapper;
import com.ihomy.mapper.BlogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * 博客业务:分页查询按家庭/可见范围过滤,增删改校验归属与权限。
 * 可见性:0仅自己/1指定成员/2指定群组/3家庭可见/4公开。
 */
@Service
@RequiredArgsConstructor
public class BlogService {

    private final BlogMapper blogMapper;
    private final BlogCategoryMapper blogCategoryMapper;
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

    /** 家庭级分类列表:从分类表拉取,按 name 排序(子分类自动排在父分类后面) */
    public List<String> categories(Long familyId) {
        LambdaQueryWrapper<BlogCategory> qw = new LambdaQueryWrapper<>();
        qw.eq(BlogCategory::getFamilyId, familyId).orderByAsc(BlogCategory::getName);
        return blogCategoryMapper.selectList(qw).stream().map(BlogCategory::getName).toList();
    }

    /** 分类计数:分类表全量 + 博客表按权限统计,合并后返回(空分类count=0) */
    public List<Map<String, Object>> categoryCounts(Long familyId, Long authorId, boolean isOwner) {
        // 1. 从分类表拿全量分类名
        LambdaQueryWrapper<BlogCategory> cqw = new LambdaQueryWrapper<>();
        cqw.eq(BlogCategory::getFamilyId, familyId).orderByAsc(BlogCategory::getName);
        List<String> allCats = blogCategoryMapper.selectList(cqw).stream().map(BlogCategory::getName).toList();

        // 2. 从博客表按权限统计每个分类文章数
        List<Map<String, Object>> counts = blogMapper.selectCategoryCounts(familyId, authorId, isOwner);
        Map<String, Long> countMap = new HashMap<>();
        for (Map<String, Object> row : counts) {
            countMap.put((String) row.get("category"), ((Number) row.get("cnt")).longValue());
        }

        // 3. 合并:分类表全量 + 博客表统计(博客表可能有分类表中不存在的旧分类)
        Set<String> seen = new HashSet<>();
        List<Map<String, Object>> result = new ArrayList<>();
        for (String cat : allCats) {
            result.add(Map.of("category", cat, "cnt", countMap.getOrDefault(cat, 0L)));
            seen.add(cat);
        }
        for (Map<String, Object> row : counts) {
            String cat = (String) row.get("category");
            if (!seen.contains(cat)) {
                result.add(row);
                seen.add(cat);
            }
        }
        return result;
    }

    /** 新增分类:持久化到分类表 */
    public void addCategory(Long familyId, String name) {
        if (!StringUtils.hasText(name)) throw new BizException(ResultCode.BAD_REQUEST);
        LambdaQueryWrapper<BlogCategory> qw = new LambdaQueryWrapper<>();
        qw.eq(BlogCategory::getFamilyId, familyId).eq(BlogCategory::getName, name);
        if (blogCategoryMapper.selectCount(qw) > 0) throw new BizException(ResultCode.BAD_REQUEST);
        BlogCategory cat = new BlogCategory();
        cat.setName(name);
        cat.setFamilyId(familyId);
        blogCategoryMapper.insert(cat);
    }

    /** 重命名分类:更新分类表 + 批量更新博客表 */
    public void renameCategory(Long familyId, String oldName, String newName) {
        if (!StringUtils.hasText(oldName) || !StringUtils.hasText(newName)) throw new BizException(ResultCode.BAD_REQUEST);
        LambdaQueryWrapper<BlogCategory> qw = new LambdaQueryWrapper<>();
        qw.eq(BlogCategory::getFamilyId, familyId).eq(BlogCategory::getName, oldName);
        BlogCategory cat = blogCategoryMapper.selectOne(qw);
        if (cat == null) throw new BizException(ResultCode.NOT_FOUND);
        LambdaQueryWrapper<BlogCategory> eqw = new LambdaQueryWrapper<>();
        eqw.eq(BlogCategory::getFamilyId, familyId).eq(BlogCategory::getName, newName);
        if (blogCategoryMapper.selectCount(eqw) > 0 && !oldName.equals(newName)) throw new BizException(ResultCode.BAD_REQUEST);
        cat.setName(newName);
        blogCategoryMapper.updateById(cat);
        blogMapper.renameCategory(familyId, oldName, newName);
    }

    /** 删除分类:删分类表记录 + 按mode处理博客表(mode=move清空分类,mode=delete删博客) */
    public void deleteCategory(Long familyId, String category, String mode) {
        if (!StringUtils.hasText(category)) throw new BizException(ResultCode.BAD_REQUEST);
        LambdaQueryWrapper<BlogCategory> qw = new LambdaQueryWrapper<>();
        qw.eq(BlogCategory::getFamilyId, familyId).eq(BlogCategory::getName, category);
        blogCategoryMapper.delete(qw);
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
