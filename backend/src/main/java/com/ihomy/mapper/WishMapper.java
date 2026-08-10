package com.ihomy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ihomy.entity.Wish;
import org.apache.ibatis.annotations.Mapper;

/**
 * 愿望单映射。
 */
@Mapper
public interface WishMapper extends BaseMapper<Wish> {
}