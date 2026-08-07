package com.ihomy.dto;

import lombok.Data;

/**
 * 个人资料表单:昵称/头像/生日/性别,可部分更新。
 */
@Data
public class ProfileDTO {
    private String nickname;
    private String avatar;
    private java.time.LocalDate birthday;
    private Integer gender;
}
