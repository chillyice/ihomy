package com.ihomy.dto;

import lombok.Data;

import java.util.List;

/**
 * 从家庭成员导入家谱:userIds 为空表示导入全部尚未关联的成员。
 */
@Data
public class TreeImportDTO {
    private List<Long> userIds;
}
