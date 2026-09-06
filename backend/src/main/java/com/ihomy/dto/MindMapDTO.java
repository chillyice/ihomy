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

    /** 列表缩略图 data URL(null 不修改) */
    private String thumbUrl;

    /** 乐观锁基线:客户端最后一次看到的 updated_at(ISO 格式)。非空时若库中已更新则返回 409,防多端互相覆盖 */
    private String baseUpdatedAt;
}
