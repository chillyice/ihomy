package com.ihomy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ihomy.entity.UserLabel;
import org.apache.ibatis.annotations.Mapper;

/** 身份标签 Mapper(V3.9):按 user_id+family_id 唯一(每家庭一条) */
@Mapper
public interface UserLabelMapper extends BaseMapper<UserLabel> {
}