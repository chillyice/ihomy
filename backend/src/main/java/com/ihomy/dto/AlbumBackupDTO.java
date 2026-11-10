package com.ihomy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 「取或建」自动备份相册的表单。
 * key 是设备端的稳定标识(android:&lt;设备标识&gt;),name 是当前界面语言下的相册名(由客户端传,服务端不认这个名字)。
 */
@Data
public class AlbumBackupDTO {

    @NotBlank(message = "备份标识不能为空")
    @Pattern(regexp = "android:[A-Za-z0-9_-]{1,56}", message = "备份标识格式不正确")
    private String key;

    @NotBlank(message = "相册名不能为空")
    @Size(max = 100, message = "相册名最长 100 个字符")
    private String name;
}
