package com.ihomy.dto;

import lombok.Data;

/**
 * 脑图创建/更新入参。更新时 title/data 为 null 表示不修改该字段。
 */
@Data
public class MindMapDTO {

    private String title;

    /** 脑图数据 JSON 字符串 */
    private String data;
}
