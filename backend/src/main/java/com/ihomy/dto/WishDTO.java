package com.ihomy.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 愿望单表单:status 0待实现 1已实现 2放弃。局部更新允许留空。
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
}
