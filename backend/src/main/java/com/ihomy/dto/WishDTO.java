package com.ihomy.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 愿望单表单:status 0待实现 1已实现 2放弃。局部更新允许留空。
 * sourceType/sourceId/sourceTitle 为来源三列(可空);sourceType 非空时必须是字典 wish_source 取值。
 */
@Data
public class WishDTO {
    @Size(max = 100, message = "愿望名称最多 100 字")
    private String title;
    @Size(max = 500, message = "理由最多 500 字")
    private String reason;
    @Size(max = 50, message = "分类最多 50 字")
    private String category;
    private String status;
    @Size(max = 20, message = "来源类型最多 20 字")
    private String sourceType;
    @Size(max = 64, message = "来源记录最多 64 字")
    private String sourceId;
    @Size(max = 100, message = "来源标题最多 100 字")
    private String sourceTitle;
}
