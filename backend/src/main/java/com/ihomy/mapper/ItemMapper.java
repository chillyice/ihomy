package com.ihomy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ihomy.entity.Item;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

public interface ItemMapper extends BaseMapper<Item> {

    /**
     * 家庭物品列表:JOIN 房间/家具,支持关键词与层级过滤。
     * 关键词匹配:物品名/别名/位置/家具名/房间名(3期 AI 拆解出的名称与别名走同一条查询)。
     */
    List<Map<String, Object>> selectItemByFamily(@Param("familyId") Long familyId,
                                                 @Param("keyword") String keyword,
                                                 @Param("roomId") Long roomId,
                                                 @Param("furnitureId") Long furnitureId,
                                                 @Param("type") String type);

    /**
     * 取出食材:原子扣减库存(quantity = quantity - amount)。
     * 仅当 quantity 非空且 >= amount 时命中,返回受影响行数(0 = 库存不足)。
     */
    int takeAmount(@Param("id") Long id, @Param("familyId") Long familyId, @Param("amount") java.math.BigDecimal amount);
}