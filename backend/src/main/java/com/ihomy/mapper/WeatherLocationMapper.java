package com.ihomy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ihomy.entity.WeatherLocation;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WeatherLocationMapper extends BaseMapper<WeatherLocation> {
}
