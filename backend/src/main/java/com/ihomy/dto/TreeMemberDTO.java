package com.ihomy.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * 家谱成员表单:新增/编辑共用,空字段跳过更新。
 * userId 可选,用于把家谱人物关联到家庭成员账号(须为同家庭成员)。
 */
@Data
public class TreeMemberDTO {
    private String name;
    private Integer gender;
    private LocalDate birthDate;
    private String photo;
    /** 关联的家庭成员账号ID(sys_user.id),空=不关联 */
    private Long userId;
    private Long fatherId;
    private Long motherId;
    private Long spouseId;
    private Integer generation;
    private String note;
}