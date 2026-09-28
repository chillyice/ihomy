package com.ihomy.dto;

import lombok.Data;

/**
 * 保险箱条目表单。password 为明文入参(仅用于写入时加密),留空表示不改密码。
 */
@Data
public class VaultItemDTO {
    private String name;
    private String category;
    private String username;
    /** 明文密码:新增/修改时提交;为空表示保留原密码不改 */
    private String password;
    private String url;
    private String tags;
    private String note;
    /** 可见范围:PRIVATE 仅自己 / FAMILY 家庭可见 */
    private String visibility;
}
