package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 成员播放档案实体(sys_media_user_config):成员在媒体服务器上用「自己的账号」看片,
 * 各自的继续观看/看过互不干扰;没配的成员沿用家庭账号({@link MediaServer})。
 * password 存 ENC 密文,出接口只回 hasPassword。
 */
@Data
@TableName("sys_media_user_config")
public class MediaUserConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long familyId;
    private Long userId;
    private String username;
    private String password;
    private LocalDateTime createdAt;
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updatedAt;
}
