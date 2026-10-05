package com.ihomy.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 保险箱条目表单。password 为明文入参(仅用于写入时加密),留空表示不改密码。
 *
 * 长度上限按 family_vault_item 列宽前置拦截(超限原先落库抛异常走兜底 500);
 * password 明文上限 128 字符,保证加密后的 ENC(Base64(iv+密文+tag)) 不超 password_enc VARCHAR(1024)。
 */
@Data
public class VaultItemDTO {
    @Size(max = 100, message = "条目名称过长")
    private String name;
    @Size(max = 30, message = "分类过长")
    private String category;
    @Size(max = 200, message = "账号过长")
    private String username;
    /** 明文密码:新增/修改时提交;为空表示保留原密码不改 */
    @Size(max = 128, message = "密码过长")
    private String password;
    @Size(max = 500, message = "登录地址过长")
    private String url;
    @Size(max = 200, message = "标签过长")
    private String tags;
    @Size(max = 1000, message = "备注过长")
    private String note;
    /** 可见范围:PRIVATE 仅自己 / FAMILY 家庭可见 */
    @Size(max = 20, message = "可见范围过长")
    private String visibility;
}
