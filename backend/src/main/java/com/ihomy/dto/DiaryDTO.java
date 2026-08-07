package com.ihomy.dto;

import lombok.Data;

/**
 * 日记表单:内容/心情/天气,可见性缺省=家庭可见(3)。
 */
@Data
public class DiaryDTO {
    private String content;
    private String mood;
    private String weather;
    private Integer visibility;
}
