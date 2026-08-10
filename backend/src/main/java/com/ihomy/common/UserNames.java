package com.ihomy.common;

import com.ihomy.entity.SysUser;
import org.springframework.util.StringUtils;

/**
 * 用户展示名工具:统一"昵称回退账号名"逻辑,避免各 service 重复实现。
 */
public final class UserNames {

    private UserNames() {
    }

    /** 用户展示名:有昵称用昵称,否则回退账号名(邮箱);用户不存在返回 null */
    public static String of(SysUser u) {
        if (u == null) return null;
        return StringUtils.hasText(u.getNickname()) ? u.getNickname() : u.getUsername();
    }
}
