package com.ihomy.service;

import com.ihomy.dto.LoginDTO;
import com.ihomy.dto.RegisterDTO;
import com.ihomy.security.LoginUser;

import java.util.Map;

public interface AuthService {
    Map<String, Object> login(LoginDTO dto);

    Map<String, Object> register(RegisterDTO dto);

    void logout(String token);

    Map<String, Object> refresh(String refreshToken);

    LoginUser currentUser();
}
