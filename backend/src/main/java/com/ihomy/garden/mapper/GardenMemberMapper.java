package com.ihomy.garden.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ihomy.garden.entity.GardenMember;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;

/**
 * 花园成员档案 Mapper:绑定关系唯一(sys_user_id 只在这里),建档幂等(架构分册 §30.3)。
 */
@Mapper
public interface GardenMemberMapper extends BaseMapper<GardenMember> {

    /** 成员建档(唯一键去重;并发建档后以重查结果为准) */
    int insertIgnore(@Param("familyId") Long familyId, @Param("playerUid") String playerUid,
                     @Param("sysUserId") Long sysUserId);

    /** 动手即记当日活跃(协作加成判定源,§16.4 要点 2) */
    int touchActive(@Param("familyId") Long familyId, @Param("playerUid") String playerUid,
                    @Param("date") LocalDate date);
}
