package com.ihomy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ihomy.entity.BookRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 记账明细映射。
 */
@Mapper
public interface BookRecordMapper extends BaseMapper<BookRecord> {
}