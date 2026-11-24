package com.ihomy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ihomy.entity.SysBackupJob;
import com.ihomy.entity.SysBackupPolicy;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SysBackupPolicyMapper extends BaseMapper<SysBackupPolicy> {

    /** 每条策略最近一次任务(策略列表展示执行情况,避免逐条查询) */
    List<SysBackupJob> selectLatestJobs(@Param("policyIds") List<Long> policyIds);
}
