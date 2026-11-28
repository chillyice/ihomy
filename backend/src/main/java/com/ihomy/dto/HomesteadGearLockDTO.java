package com.ihomy.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 小院慢生活:锁定 / 解锁一件装备(防误分解与误重铸)。 */
@Data
public class HomesteadGearLockDTO {
    @NotNull(message = "装备不能为空")
    private Long gearId;

    @NotNull(message = "锁定状态不能为空")
    private Boolean locked;

    public boolean lockedOrFalse() {
        return Boolean.TRUE.equals(locked);
    }
}
