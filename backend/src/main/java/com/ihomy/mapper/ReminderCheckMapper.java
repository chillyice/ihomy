package com.ihomy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ihomy.entity.ReminderCheck;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 循环提醒按日完成记录映射:按提醒删除完成行走自定义 XML(表无逻辑删列,物理删)。
 */
@Mapper
public interface ReminderCheckMapper extends BaseMapper<ReminderCheck> {

    /** 删除一条提醒的全部完成记录(提醒被删时连带清理) */
    int deleteByReminderId(@Param("reminderId") Long reminderId);

    /** 批量删除多条提醒的完成记录(计划删除等连带场景) */
    int deleteByReminderIds(@Param("reminderIds") List<Long> reminderIds);
}
