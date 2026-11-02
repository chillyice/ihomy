package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 游戏站点账号实体(game_site_account):成员为外部网页游戏保存的登录信息,一人一游戏一行。
 * 密码只存 AES-GCM 密文(ENC(Base64)),盐值走 sys_parameter(aes-salt);行随成员删除意愿物理删除。
 */
@Data
@TableName("game_site_account")
public class GameSiteAccount {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long gameId;
    private Long userId;
    private String siteUsername;
    private String sitePassword;
    private String remark;
    private LocalDateTime createdAt;
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updatedAt;
}
