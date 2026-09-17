package com.ihomy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ihomy.entity.AiLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface AiLogMapper extends BaseMapper<AiLog> {

    /** 按时间桶聚合调用量/失败量(features 为空=全部功能);SQL 见 AiLogMapper.xml */
    List<Map<String, Object>> selectTimeline(@Param("familyId") Long familyId,
                                             @Param("start") LocalDateTime start,
                                             @Param("end") LocalDateTime end,
                                             @Param("fmt") String fmt,
                                             @Param("features") List<String> features);

    /** 功能分布(饼图):按 feature_code 统计调用量 */
    List<Map<String, Object>> selectTypeDistribution(@Param("familyId") Long familyId,
                                                     @Param("start") LocalDateTime start,
                                                     @Param("end") LocalDateTime end);

    /** 汇总(全量):按 feature_code 聚合总调用/失败量,服务层据此求合计 */
    List<Map<String, Object>> selectSummary(@Param("familyId") Long familyId);
}
