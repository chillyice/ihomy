package com.ihomy.service;

import com.ihomy.dto.LoginDTO;
import com.ihomy.dto.RegisterDTO;
import com.ihomy.security.LoginUser;

import java.util.List;
import java.util.Map;

/**
 * 认证服务接口:登录/注册/登出/刷新令牌,以及多家庭列表与切换。
 */
public interface AuthService {
    Map<String, Object> login(LoginDTO dto);

    Map<String, Object> register(RegisterDTO dto);

    void logout(String token);

    Map<String, Object> refresh(String refreshToken);

    LoginUser currentUser();

    List<Map<String, Object>> listFamilies(Long userId);

    /** setDefault=true 时同时将目标家庭登记为用户默认家庭 */
    Map<String, Object> switchFamily(Long userId, Long familyId, Boolean setDefault);

    void joinFamily(Long userId, String inviteCode);
}
