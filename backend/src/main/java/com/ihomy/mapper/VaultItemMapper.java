package com.ihomy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ihomy.entity.VaultItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * 家庭保险箱条目映射。
 */
@Mapper
public interface VaultItemMapper extends BaseMapper<VaultItem> {
}
