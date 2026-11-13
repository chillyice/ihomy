package com.ihomy.common;

/**
 * 家庭封面的地址归一与旧文件判定(纯函数)。
 * 落库口径:封面为空必须是 SQL NULL,不是空串——访客取封面文件的放行规则按 cover_image = path 精确匹配,
 * 空串会让「没有封面」变成「有一个指向本目录的空路径」;旧客户端「移除」提交的也是空串,须在这里兜住。
 */
public final class CoverImageUrl {

    private CoverImageUrl() {
    }

    /** 表单值 → 落库值:空白(含空串)归一为 null;其余去首尾空白 */
    public static String normalize(String incoming) {
        if (incoming == null) return null;
        String v = incoming.trim();
        return v.isEmpty() ? null : v;
    }

    /**
     * 换图/清除后该删的旧文件 URL;未变化或本来就没有返回 null。
     * 只做「要不要删」的判定,是否真是本站文件由 FileService.deleteByUrl 按 /files/ 前缀把关
     * (随前端包部署的 /demo/*.svg 之类不会被删)。
     */
    public static String obsoleteFile(String oldUrl, String newUrl) {
        if (oldUrl == null || oldUrl.isBlank()) return null;
        return oldUrl.equals(newUrl) ? null : oldUrl;
    }
}
