package com.ihomy.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * 家谱成员表单:新增/编辑共用,空字段跳过更新。
 */
@Data
public class TreeMemberDTO {
    private String name;
    private Integer gender;
    private LocalDate birthDate;
    private String photo;
    private Long fatherId;
    private Long motherId;
    private Long spouseId;
    private Integer generation;
    private String note;
}