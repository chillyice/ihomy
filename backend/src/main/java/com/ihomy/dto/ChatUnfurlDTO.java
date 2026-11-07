package com.ihomy.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 聊天站外链接预览请求:要抓取的 URL。
 */
@Data
public class ChatUnfurlDTO {
    @Size(max = 2000, message = "链接过长")
    private String url;
}
