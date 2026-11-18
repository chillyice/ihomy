package com.ihomy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ihomy.entity.FamilyHealthSample;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

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
}
