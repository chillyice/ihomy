package com.ihomy.dto;

import lombok.Data;

/**
 * 成员播放档案(成员自己的媒体服务器账号)。
 * 保存时 password 留空表示保留原密码;保存后会试连一次并回报是否可用。
 */
@Data
public class MediaUserDTO {
    private String username;
    private String password;
}
