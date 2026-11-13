package com.ihomy.service.access;

/**
 * 请求期可见性上下文:由 {@link ViewerScopeResolver} 每请求构造一次,Controller 显式传给 Service。
 * 不用 ThreadLocal,便于单测与并发请求互不干扰。
 *
 * @param userId   登录用户 id;null = 游客
 * @param familyId 可见性判定的基准家庭:登录=当前家庭;游客=默认演示家庭;公开视图=指定的公开家庭
 * @param owner    是否为该家庭的家长(OWNER)
 */
public record ViewerScope(Long userId, Long familyId, boolean owner) {

    /** 登录用户(或已定位家庭的主体) */
    public static ViewerScope of(Long userId, Long familyId, boolean owner) {
        return new ViewerScope(userId, familyId, owner);
    }

    /** 游客:只读默认演示家庭的公开内容(修掉「游客跨库取 PUBLIC」的旧缺陷) */
    public static ViewerScope guest(Long demoFamilyId) {
        return new ViewerScope(null, demoFamilyId, false);
    }

    /** 公开视图:限定某家庭的公开内容,与登录态无关(公开动态流 / 公开家庭首页) */
    public static ViewerScope publicView(Long familyId) {
        return new ViewerScope(null, familyId, false);
    }

    public boolean guest() {
        return userId == null;
    }

    public boolean hasFamily() {
        return familyId != null;
    }
}
