package com.ihomy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ihomy.entity.BookAccount;
import org.apache.ibatis.annotations.Mapper;

/**
 * 记账账户映射。
 */
@Mapper
public interface BookAccountMapper extends BaseMapper<BookAccount> {
}
