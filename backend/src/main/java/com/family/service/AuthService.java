package com.family.service;

import com.family.dto.LoginDTO;
import com.family.dto.RegisterDTO;
import com.family.security.LoginUser;

import java.util.Map;

public interface AuthService {
    Map<String, Object> login(LoginDTO dto);

    Map<String, Object> register(RegisterDTO dto);

    void logout(String token);

    Map<String, Object> refresh(String refreshToken);

    LoginUser currentUser();
}
