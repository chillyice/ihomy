package com.ihomy.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ihomy.common.BizException;
import com.ihomy.common.ResultCode;
import com.ihomy.dto.BlogDTO;
import com.ihomy.entity.Blog;
import com.ihomy.mapper.BlogMapper;
import com.ihomy.service.BlogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class BlogServiceImpl implements BlogService {

    private final BlogMapper blogMapper;

    @Override
    public IPage<Blog> page(int current, int size, Long familyId, String keyword) {
        LambdaQueryWrapper<Blog> qw = new LambdaQueryWrapper<>();
        qw.eq(familyId != null, Blog::getFamilyId, familyId)
          .eq(Blog::getStatus, 1)
          .like(StringUtils.hasText(keyword), Blog::getTitle, keyword)
          .orderByDesc(Blog::getCreatedAt);
        return blogMapper.selectPage(new Page<>(current, size), qw);
    }

    @Override
    public Blog getDetail(Long id) {
        Blog blog = blogMapper.selectById(id);
        if (blog == null) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        blogMapper.incrViewCount(id);
        return blog;
    }

    @Override
    public Blog create(Long authorId, Long familyId, BlogDTO dto) {
        Blog blog = new Blog();
        blog.setTitle(dto.getTitle());
        blog.setContent(dto.getContent());
        blog.setCoverImage(dto.getCoverImage());
        blog.setAuthorId(authorId);
        blog.setFamilyId(familyId);
        blog.setStatus(dto.getStatus() == null ? 0 : dto.getStatus());
        blog.setVisibility(dto.getVisibility() == null ? 0 : dto.getVisibility());
        blog.setViewCount(0);
        blogMapper.insert(blog);
        return blog;
    }

    @Override
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
        if (dto.getStatus() != null) blog.setStatus(dto.getStatus());
        if (dto.getVisibility() != null) blog.setVisibility(dto.getVisibility());
        blogMapper.updateById(blog);
        return blog;
    }

    @Override
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
