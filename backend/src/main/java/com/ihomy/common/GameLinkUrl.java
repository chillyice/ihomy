package com.ihomy.common;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * 外部网页游戏地址(LINK 类型)校验与规范化。
 * 网址最终会以新标签页打开,必须收敛为 http(s) 绝对地址,堵住 javascript:/data: 等危险 scheme。
 */
public final class GameLinkUrl {

    private GameLinkUrl() {
    }

    /** 长度上限(与 game_info.link_url 列宽一致) */
    public static final int MAX_LENGTH = 500;

    /**
     * 校验并规范化网址:
     *  - 没写 scheme 时自动补 https://;
     *  - 只接受 http/https,且必须有主机名;
     *  - 合法返回原样字符串(不重写大小写,保留路径),非法抛 BizException。
     */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BizException(ResultCode.BAD_REQUEST, "请填写游戏网址");
        }
        String url = raw.trim();
        if (url.length() > MAX_LENGTH) {
            throw new BizException(ResultCode.BAD_REQUEST, "网址过长(上限 " + MAX_LENGTH + " 字符)");
        }
        if (!url.matches("(?i)^[a-z][a-z0-9+.-]*://.*")) {
            url = "https://" + url;
        }
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new BizException(ResultCode.BAD_REQUEST, "网址格式不正确，请检查后重试");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new BizException(ResultCode.BAD_REQUEST, "只支持 http(s):// 开头的网址");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new BizException(ResultCode.BAD_REQUEST, "网址缺少域名，请检查后重试");
        }
        return url;
    }
}
