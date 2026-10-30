package com.ihomy.service.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ihomy.entity.Item;
import com.ihomy.mapper.ItemMapper;
import com.ihomy.service.ItemExpiryService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 食材到期只读来源:与站内过期预警同一口径(只取 INGREDIENT、有入库时间与保质期),
 * 到期日由 {@link ItemExpiryService#expiryDate} 算,这里不另写第二套到期算法;
 * 一项食材在到期日当天出一个只读项,点击跳食材页。家庭内无独立权限码,登录即可见。
 */
@Component
@Order(30)
@RequiredArgsConstructor
public class ItemExpiryScheduleSource implements ScheduleSourceProvider {

    private final ItemMapper itemMapper;
    private final ItemExpiryService itemExpiryService;

    @Override
    public String source() {
        return "ITEM_EXPIRY";
    }

    @Override
    public boolean available(Long familyId) {
        return familyId != null;
    }

    @Override
    public void append(Long familyId, LocalDate from, LocalDate to, List<Map<String, Object>> items) {
        List<Item> ingredients = itemMapper.selectList(new LambdaQueryWrapper<Item>()
                .eq(Item::getFamilyId, familyId)
                .eq(Item::getType, "INGREDIENT")
                .isNotNull(Item::getStoredAt)
                .isNotNull(Item::getShelfLife)
                .gt(Item::getShelfLife, 0));
        for (Item it : ingredients) {
            LocalDate expiry = itemExpiryService.expiryDate(it);
            if (expiry == null || expiry.isBefore(from) || expiry.isAfter(to)) continue;
            items.add(ScheduleSourceProvider.readOnlyItem(
                    source(), it.getId(), it.getName(), expiry, "/kitchen/ingredients"));
        }
    }
}
