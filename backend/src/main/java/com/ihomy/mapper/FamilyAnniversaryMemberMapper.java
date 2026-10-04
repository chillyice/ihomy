package com.ihomy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ihomy.entity.FamilyAnniversaryMember;
import org.apache.ibatis.annotations.Mapper;

/**
 * 纪念日-成员关联表 Mapper(仅 BaseMapper,查询条件用 Wrapper 组合)。
 */
@Mapper
public interface FamilyAnniversaryMemberMapper extends BaseMapper<FamilyAnniversaryMember> {
}
