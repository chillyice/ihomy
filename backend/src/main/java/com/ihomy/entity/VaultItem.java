package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 家庭保险箱条目(family_vault_item):家庭共享的账号密码保管箱。
 *
 * passwordEnc 存 ENC(Base64(iv+密文+tag)) 密文(盐值 sys_parameter.aes-salt),
 * 仅在「揭示密码」接口解密返回,列表接口只回固定掩码。
 * visibility 复用 PRIVATE(仅创建人可见)/FAMILY(家庭可见) 口径。
 */
@Data
@TableName("family_vault_item")
public class VaultItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long familyId;

    /** 条目名称 */
    private String name;

    /** 分类:SITE/APP/BANK/SOCIAL/DEVICE/WIFI/OTHER */
    private String category;

    /** 账号/用户名(可空,如无线网络只存密码) */
    private String username;

    /** 密码密文 ENC(...) */
    private String passwordEnc;

    /** 登录地址 */
    private String url;

    /** 标签(逗号分隔) */
    private String tags;

    /** 备注 */
    private String note;

    /** 创建人ID */
    private Long ownerId;

    /** 可见范围:PRIVATE 仅创建人 / FAMILY 家庭可见 */
    private String visibility;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createdAt;

    @TableField(updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updatedAt;
}
