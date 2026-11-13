package com.ihomy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ihomy.entity.SysUserPref;
import org.apache.ibatis.annotations.Param;

/** 用户界面偏好Mapper:写入要「没有就插、有就覆盖」,自定义 SQL 在 resources/mapper/SysUserPrefMapper.xml */
public interface SysUserPrefMapper extends BaseMapper<SysUserPref> {

    /** 按 (user_id, pref_key) upsert:同一个人两台设备先后保存都落到同一行(唯一键命中原位覆盖) */
    int upsert(@Param("userId") Long userId, @Param("prefKey") String prefKey, @Param("prefValue") String prefValue);
}
