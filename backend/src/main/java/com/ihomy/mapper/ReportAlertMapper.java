package com.ihomy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ihomy.entity.ReportAlert;
import org.apache.ibatis.annotations.Mapper;

/**
 * 同类异常聚合预警(report_alert):查询/标记/超期清理都用 MyBatis-Plus 条件构造器,
 * 无自定义 SQL(故无同名 XML)。该表无 deleted 列,delete 即物理删(仅超期清理使用)。
 */
@Mapper
public interface ReportAlertMapper extends BaseMapper<ReportAlert> {
}
