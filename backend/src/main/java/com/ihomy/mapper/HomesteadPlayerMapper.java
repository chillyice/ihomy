package com.ihomy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ihomy.entity.HomesteadPlayer;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 小院慢生活玩家档映射。
 * 结算必须在事务内先 {@link #selectByUserForUpdate} 锁行再读写,保证同一账号多标签页/多设备不会重复入账;
 * 单实例部署下这就是唯一并发闸门(多实例时靠数据库行锁,行为不变,只是吞吐受阻)。
 */
@Mapper
public interface HomesteadPlayerMapper extends BaseMapper<HomesteadPlayer> {

    /** 锁行读取(事务内使用);不存在返回 null */
    HomesteadPlayer selectByUserForUpdate(@Param("userId") Long userId);

    /** 建号:已存在则什么都不做(多标签页同时首访不报错) */
    int insertPlayerIfAbsent(HomesteadPlayer player);

    /** 结算/写操作后整行回写(允许 action_code 置空,故走 XML 而不是 MP 的 null 跳过策略) */
    int updateProgress(HomesteadPlayer player);

    /** 家庭榜候选:只含当前仍是本家庭成员的行(退家庭/被移除自然消失) */
    List<Map<String, Object>> selectFamilyPlayers(@Param("familyId") Long familyId);
}
