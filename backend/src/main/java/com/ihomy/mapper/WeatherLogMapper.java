package com.ihomy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ihomy.entity.WeatherLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface WeatherLogMapper extends BaseMapper<WeatherLog> {

    @Select("SELECT DATE_FORMAT(created_at, #{fmt}) AS time_bucket, COUNT(*) AS total, " +
            "SUM(CASE WHEN status = 'FAIL' THEN 1 ELSE 0 END) AS failed " +
            "FROM sys_weather_log WHERE created_at >= #{start} AND created_at < #{end} " +
            "GROUP BY time_bucket ORDER BY time_bucket")
    List<Map<String, Object>> selectTimeline(@Param("start") LocalDateTime start,
                                              @Param("end") LocalDateTime end,
                                              @Param("fmt") String fmt);
}
