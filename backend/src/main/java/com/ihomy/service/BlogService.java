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
    public IPage<Blog> page(int current, int size, Long familyId, Long currentUserId, boolean isOwner, String keyword) {
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
          .like(StringUtils.hasText(keyword), Blog::getTitle, keyword)
          .orderByDesc(Blog::getCreatedAt);
        return blogMapper.selectPage(new Page<>(current, size), qw);
    }

    /** 详情:不存在抛 404,命中后累加浏览量 */
    public Blog getDetail(Long id) {
        Blog blog = blogMapper.selectById(id);
        if (blog == null) {
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
