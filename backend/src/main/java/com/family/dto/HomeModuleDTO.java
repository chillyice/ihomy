package com.family.dto;

import lombok.Data;

import java.util.List;

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
