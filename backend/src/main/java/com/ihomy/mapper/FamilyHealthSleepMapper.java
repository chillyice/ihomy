package com.ihomy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ihomy.entity.FamilyHealthSleep;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 睡眠会话映射:派生数据重算即整窗物理删(表无逻辑删列),删除与重叠查询走自定义 XML。
 */
@Mapper
public interface FamilyHealthSleepMapper extends BaseMapper<FamilyHealthSleep> {

    /** 查出与 [from,to) 重叠的会话 id(重算前先删这些行及其分期) */
    List<Long> selectOverlappingIds(@Param("userId") Long userId,
                                    @Param("sourceApp") String sourceApp,
                                    @Param("from") LocalDateTime from,
                                    @Param("to") LocalDateTime to);

    /** 物理删除指定会话(派生数据,重算即替换) */
    int deleteByIds(@Param("ids") List<Long> ids);

    /** 清除某成员某来源的全部睡眠会话(换设备/误配) */
    int deleteByProvider(@Param("userId") Long userId, @Param("provider") String provider);
}
