package com.ihomy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ihomy.entity.KadaSnippet;
import org.apache.ibatis.annotations.Mapper;

/**
 * 咔哒卡带社区发布(sys_kada_snippet):列表 / 计数用 MyBatis-Plus 条件构造器,
 * 无自定义 SQL(故无同名 XML)。审核通过后写文件与索引由 KadaSnippetService 负责。
 */
@Mapper
public interface KadaSnippetMapper extends BaseMapper<KadaSnippet> {
}
