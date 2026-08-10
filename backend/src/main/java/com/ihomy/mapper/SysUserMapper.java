package com.ihomy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ihomy.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    /** 按注册邮箱查询用户(登录账号即邮箱,大小写不敏感) */
    SysUser selectByEmail(@Param("email") String email);

    List<Map<String, Object>> selectMembersByFamily(@Param("familyId") Long familyId);
}
