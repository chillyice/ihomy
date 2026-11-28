package com.ihomy.garden.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ihomy.garden.entity.GardenWallet;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 个人园币钱包 Mapper:所有扣减都是条件 UPDATE + 0 行即失败(FR-23、架构分册 §17.1)。
 */
@Mapper
public interface GardenWalletMapper extends BaseMapper<GardenWallet> {

    /** 建档(余额 0),唯一键去重 */
    int insertIgnore(@Param("familyId") Long familyId, @Param("playerUid") String playerUid);

    /** 条件扣款:余额不足返回 0 行(调用方查余额补差额文案) */
    int deduct(@Param("familyId") Long familyId, @Param("playerUid") String playerUid, @Param("amount") int amount);

    /** 入账(拆信 / 收获) */
    int add(@Param("familyId") Long familyId, @Param("playerUid") String playerUid, @Param("amount") int amount);
}
