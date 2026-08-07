package com.ihomy.dto;

import lombok.Data;

import java.util.List;

/**
 * 首页模块配置表单:批量更新各模块的位置/排序/启停。
 */
@Data
public class HomeModuleDTO {
    private List<HomeModuleItem> modules;

    @Data
    public static class HomeModuleItem {
        private Long id;
        private String code;
        private String title;
        private String icon;
        private String path;
        private String position;
        private Integer sortOrder;
        private Integer enabled;
    }
}
