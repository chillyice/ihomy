package com.ihomy.security;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginUser {
    private Long userId;
    private String username;
    private String role;
    private Long familyId;
}
