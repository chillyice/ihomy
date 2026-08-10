package com.ihomy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ihomy.entity.Checkin;
import org.apache.ibatis.annotations.Mapper;

/**
 * 签到表映射:每日一次(UNIQUE user_id+checkin_date),常规查询走 BaseMapper。
 */
@Mapper
public interface CheckinMapper extends BaseMapper<Checkin> {
}
