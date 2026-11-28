package com.ihomy.garden.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ihomy.garden.entity.GardenListing;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 市集挂单 Mapper:条件 UPDATE 落地(§15.2 纪律),过期回仓走条件 UPDATE 防并发。
 */
@Mapper
public interface GardenListingMapper extends BaseMapper<GardenListing> {

    /** 过期 ACTIVE 挂单(清理任务分批调用;货回家庭仓由调用方在事务内 upsertAdd) */
    List<GardenListing> selectExpired(@Param("familyId") Long familyId, @Param("now") LocalDateTime now);

    int markExpired(@Param("id") Long id);

    /** 购买锁定:仅 ACTIVE 且未过期且非自己的挂单可买;0 行 = 已售/已下架/已过期 */
    int markSold(@Param("id") Long id, @Param("familyId") Long familyId,
                 @Param("buyerUid") String buyerUid, @Param("now") LocalDateTime now);

    int markCancelled(@Param("id") Long id, @Param("familyId") Long familyId,
                      @Param("sellerUid") String sellerUid);
}
