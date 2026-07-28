package com.family.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.family.dto.BlogDTO;
import com.family.entity.Blog;

public interface BlogService {
    IPage<Blog> page(int current, int size, Long familyId, String keyword);

    Blog getDetail(Long id);

    Blog create(Long authorId, Long familyId, BlogDTO dto);

    Blog update(Long id, Long currentUserId, BlogDTO dto);

    void delete(Long id, Long currentUserId, boolean isOwner);
}
