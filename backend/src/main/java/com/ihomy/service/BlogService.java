package com.ihomy.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ihomy.dto.BlogDTO;
import com.ihomy.entity.Blog;

/**
 * 博客服务接口:分页/详情/增删改。
 */
public interface BlogService {
    IPage<Blog> page(int current, int size, Long familyId, Long currentUserId, boolean isOwner, String keyword);

    Blog getDetail(Long id);

    Blog create(Long authorId, Long familyId, BlogDTO dto);

    Blog update(Long id, Long currentUserId, BlogDTO dto);

    void delete(Long id, Long currentUserId, boolean isOwner);
}
