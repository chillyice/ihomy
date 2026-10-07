package com.ihomy.dto;

import lombok.Data;

/**
 * 日记表单入参。
 * visibility 为历史整数口径(0 仅自己/1 家庭成员/2 分组/4 公开),由 DictConst.visibility 转字典词,缺省与未知值归家庭可见;
 * images/doodle 是前端序列化好的字符串(按原样落库);date 允许传 yyyy-MM-dd 补记往期日记,为空则取当前时间。
 */
@Data
public class DiaryDTO {
    private String content;
    private String mood;
    private String weather;
    private String visibility;
    private String images;
    private String doodle;
    private String date;
}
