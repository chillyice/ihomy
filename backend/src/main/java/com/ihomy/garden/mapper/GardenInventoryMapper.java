package com.ihomy.garden.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ihomy.garden.entity.GardenInventory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 家庭仓库 Mapper:同码堆叠 upsert、条件扣减(防超扣)、qty=0 删行(FR-13)。
 */
@Mapper
public interface GardenInventoryMapper extends BaseMapper<GardenInventory> {

    /** 同码累加入库(收获 / 拆信发种);返回 2 表示已有行被累加 */
    int upsertAdd(@Param("familyId") Long familyId, @Param("itemCode") String itemCode,
                  @Param("itemType") String itemType, @Param("qty") int qty);

    /** 条件扣减(播种消耗种子包):qty 不足返回 0 行 */
    int deduct(@Param("familyId") Long familyId, @Param("itemCode") String itemCode, @Param("qty") int qty);

    /** 清理 0 数量行 */
    int deleteIfZero(@Param("familyId") Long familyId, @Param("itemCode") String itemCode);

    /** 仓库占用(SUM(qty),容量现算不落库) */
    int sumQty(@Param("familyId") Long familyId);
}
