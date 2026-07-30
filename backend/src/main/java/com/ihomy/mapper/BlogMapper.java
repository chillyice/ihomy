package com.ihomy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ihomy.entity.Blog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface BlogMapper extends BaseMapper<Blog> {

    @Update("UPDATE blog SET view_count = view_count + 1 WHERE id = #{id}")
    int incrViewCount(Long id);
}
