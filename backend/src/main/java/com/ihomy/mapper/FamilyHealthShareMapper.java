package com.ihomy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ihomy.entity.FamilyHealthShare;
import org.apache.ibatis.annotations.Mapper;

/**
 * 健康报表分享:全部按主键/令牌做单表操作,BaseMapper 足够,不写自定义 SQL。
 */
@Mapper
public interface FamilyHealthShareMapper extends BaseMapper<FamilyHealthShare> {
}
