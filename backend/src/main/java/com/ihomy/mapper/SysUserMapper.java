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

    /** 家庭全部成员账号(按 sys_user_role 绑定,含第二家庭成员) */
    List<SysUser> selectUsersByFamily(@Param("familyId") Long familyId);

    /** 家庭活跃成员数(与成员列表同一口径) */
    long countActiveMembers(@Param("familyId") Long familyId);
}
