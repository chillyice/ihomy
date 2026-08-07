package com.ihomy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ihomy.entity.SysRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole> {

    String selectRoleCodeByUserAndFamily(@Param("userId") Long userId, @Param("familyId") Long familyId);

    List<String> selectAuthCodesByUserAndFamily(@Param("userId") Long userId, @Param("familyId") Long familyId);
}
