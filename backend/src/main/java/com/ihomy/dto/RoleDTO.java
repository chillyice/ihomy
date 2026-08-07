package com.ihomy.dto;

import lombok.Data;

/**
 * 角色表单:roleCode(如 OWNER/MEMBER)。
 */
@Data
public class RoleDTO {
    private String roleCode;
}
