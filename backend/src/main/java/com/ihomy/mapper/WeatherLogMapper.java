package com.ihomy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ihomy.entity.WeatherLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface WeatherLogMapper extends BaseMapper<WeatherLog> {

    /** 按时间桶聚合调用量/失败量(apiTypes 为空=全部类型);SQL 见 WeatherLogMapper.xml */
    List<Map<String, Object>> selectTimeline(@Param("start") LocalDateTime start,
                                              @Param("end") LocalDateTime end,
                                              @Param("fmt") String fmt,
                                              @Param("apiTypes") List<String> apiTypes);

    /** API 类型分布(饼图):按类型统计调用量 */
    List<Map<String, Object>> selectTypeDistribution(@Param("start") LocalDateTime start,
                                                      @Param("end") LocalDateTime end);
}
