package com.ihomy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ihomy.entity.Reminder;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提醒事项映射:触发检测在 ReminderService 定时任务中。
 */
@Mapper
public interface ReminderMapper extends BaseMapper<Reminder> {
}