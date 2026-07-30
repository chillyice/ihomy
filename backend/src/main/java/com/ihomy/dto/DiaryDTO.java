package com.ihomy.dto;

import lombok.Data;

@Data
public class DiaryDTO {
    private String content;
    private String mood;
    private String weather;
    private Integer visibility;
}
