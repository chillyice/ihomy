package com.ihomy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ihomy.entity.FamilyHealthSample;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 健康样本映射:重复上报按唯一键 upsert 覆盖(厂商修正历史也走同一条路),批量写走 XML。
 */
@Mapper
public interface FamilyHealthSampleMapper extends BaseMapper<FamilyHealthSample> {

    int upsertBatch(@Param("list") List<FamilyHealthSample> list);

    /** 清除某成员某来源的全部样本(换设备/误配) */
    int deleteByProvider(@Param("userId") Long userId, @Param("provider") String provider);

    /** 清除某成员某指标的全部样本(关闭敏感指标时一并清除) */
    int deleteByMetric(@Param("userId") Long userId, @Param("metric") String metric);

    /**
     * 逐点指标按天聚合(长区间趋势图):每天平均 + 当天最小/最大。
     * 小时级逐点样本一年几千行,原样返回会被 2000 行上限截断,长区间先聚合再回。
     */
    List<Map<String, Object>> selectDailyPoints(@Param("userId") Long userId,
                                                @Param("metric") String metric,
                                                @Param("from") LocalDate from,
                                                @Param("toExclusive") LocalDate toExclusive);
}
