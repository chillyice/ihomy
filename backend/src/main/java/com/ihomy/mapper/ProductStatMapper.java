package com.ihomy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ihomy.entity.ProductStat;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Mapper
public interface ProductStatMapper extends BaseMapper<ProductStat> {

    /** 原子累加当天计数(无行则插入);SQL 见 ProductStatMapper.xml */
    int increment(@Param("product") String product, @Param("views") int views, @Param("downloads") int downloads);

    /** 按时间桶聚合:fmt 取 %Y-%m-%d(按天)或 %Y-%m(按月),start 为空=全部历史 */
    List<Map<String, Object>> selectBuckets(@Param("start") LocalDate start,
                                            @Param("end") LocalDate end,
                                            @Param("fmt") String fmt);

    /** 最早有数据的一天(用于「全部」区间决定按天还是按月分桶) */
    LocalDate selectMinDate();
}
