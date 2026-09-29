package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 放映厅媒体引擎配置实体(sys_media_server):每家庭一条,一家庭一个媒体服务器。
 * ihomy 只调它的 REST API;password 存 ENC 密文,出接口只回 hasPassword。
 */
@Data
@TableName("sys_media_server")
public class MediaServer {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long familyId;
    private String serverType;
    private String serverUrl;
    private String publicUrl;
    private String username;
    private String password;
    private Integer enabled;
    private LocalDateTime lastConnectedAt;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
