package com.ihomy.service.access;

import com.ihomy.entity.Family;
import com.ihomy.mapper.FamilyMapper;
import com.ihomy.security.LoginUser;
import com.ihomy.security.SecurityHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 把当前请求解析成 {@link ViewerScope}:登录用户=当前家庭;游客=默认演示家庭。
 * 游客一律落在演示家庭,避免旧实现「游客跨库取 PUBLIC」把别家公开内容混进来。
 */
@Component
@RequiredArgsConstructor
public class ViewerScopeResolver {

    private final SecurityHelper securityHelper;
    private final FamilyMapper familyMapper;

    /** 当前请求的可见性上下文(登录态优先;游客取默认演示家庭,取不到时 familyId=null 只读 PUBLIC) */
    public ViewerScope resolve() {
        LoginUser user = securityHelper.current();
        if (user != null) {
            return ViewerScope.of(user.getUserId(), user.getFamilyId(), "OWNER".equals(user.getRole()));
        }
        Family demo = familyMapper.selectDefault();
        return ViewerScope.guest(demo == null ? null : demo.getId());
    }

    /** 指定家庭的公开视图(公开动态流等已由调用方校验过家庭公开性) */
    public ViewerScope publicView(Long familyId) {
        return ViewerScope.publicView(familyId);
    }
}
