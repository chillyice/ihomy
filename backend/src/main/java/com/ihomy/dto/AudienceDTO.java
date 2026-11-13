package com.ihomy.dto;

import lombok.Data;

import java.util.List;

/**
 * 内容分享范围:选中哪些圈就发给圈内其他家庭;空列表 = 只给自家人(全部收回)。
 * 新前端可直接传目标家庭(targetFamilyIds),老客户端传圈(circleIds),服务端统一展开成逐家庭行。
 */
@Data
public class AudienceDTO {
    private List<Long> circleIds;
    /** 直接选中的接收家庭(必须在本家庭所在活跃圈里) */
    private List<Long> targetFamilyIds;
}
