package com.ihomy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ihomy.entity.Task;
import org.apache.ibatis.annotations.Mapper;

/**
 * 悬赏任务映射:状态流转在 TaskService 层校验。
 */
@Mapper
public interface TaskMapper extends BaseMapper<Task> {
}