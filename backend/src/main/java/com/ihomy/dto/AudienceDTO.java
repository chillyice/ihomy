package com.ihomy.dto;

import lombok.Data;

import java.util.List;

/**
 * 内容分享范围:选中哪些圈就发给圈内其他家庭;空列表 = 只给自家人(全部收回)。
 * 圈在写入时展开为逐个接收家庭,不存目标家庭成员名单。
 */
@Data
public class AudienceDTO {
    private List<Long> circleIds;
}
