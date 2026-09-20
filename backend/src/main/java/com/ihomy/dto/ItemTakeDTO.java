package com.ihomy.dto;

import lombok.Data;

/**
 * 取出食材参数:amount 取出数量(正数,库存不足时后端拒绝)。
 */
@Data
public class ItemTakeDTO {
    private java.math.BigDecimal amount;
}
