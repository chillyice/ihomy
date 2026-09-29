package com.ihomy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ihomy.entity.SysRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole> {

    String selectRoleCodeByUserAndFamily(@Param("userId") Long userId, @Param("familyId") Long familyId);

    List<String> selectAuthCodesByUserAndFamily(@Param("userId") Long userId, @Param("familyId") Long familyId);

    /** 一次取用户在所有家庭的角色码(键为 familyId,值为 roleCode),供家庭列表免 N+1 */
    List<Map<String, Object>> selectRoleCodesByUser(@Param("userId") Long userId);

    /** 查用户是否有系统级 OPS 角色(family_id=NULL) */
    @org.apache.ibatis.annotations.Select("SELECT COUNT(*) FROM sys_user_role ur JOIN sys_role r ON ur.role_id=r.id WHERE ur.user_id=#{userId} AND r.role_code='OPS' AND ur.family_id IS NULL")
    int countOpsRole(@Param("userId") Long userId);
}
