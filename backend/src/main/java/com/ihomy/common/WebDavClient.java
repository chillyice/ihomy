package com.ihomy.common;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * 轻量 WebDAV 客户端(仅 PROPFIND 浏览 + GET 流式,零重依赖):
 * 供 NEXTCLOUD/WEBDAV 存储设备接入(与百度网盘平级,见需求设计说明书 §9 P2)。
 *
 * PROPFIND 小响应走 ThirdPartyHttp.request(自动 thirdparty 日志 + URL/头脱敏);
 * 大文件 GET 流式下载不走 ThirdPartyHttp(不能整包读成字符串),手动 HttpURLConnection
 * + 手动 Loggers.thirdParty("webdav") 日志(同 StorageService.baiduOpen 惯例)。
 *
 * 请求路径统一百分号编码(UTF-8,空格用 %20),响应 href 统一解码('+' 保护,不转空格)。
 * 404 → NOT_FOUND(映射失败分类为 MISSING)、401/403 → 认证失败、连接异常 → 无法连接。
 */
public final class WebDavClient {

    /** 目录项(name/isDir/size/modified,与 StorageService.browse 返回结构对齐;fsId 恒无) */
    public record DavItem(String name, boolean dir, Long size, Long modified) {}

    /** 文件流:status 支持 200/206(Range 透传),contentRange 为上游原样回传 */
    public record WebDavStream(int status, long length, String contentRange, InputStream in) {}

    private static final String DAV_NS = "DAV:";
    private static final String PROPFIND_BODY =
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
            + "<D:propfind xmlns:D=\"DAV:\"><D:prop><D:resourcetype/><D:getcontentlength/>"
            + "<D:getlastmodified/><D:getetag/></D:prop></D:propfind>";

    private WebDavClient() {
    }

    /** Basic 认证头 */
    public static String basicAuth(String user, String pass) {
        String token = user + ":" + pass;
        return "Basic " + Base64.getEncoder().encodeToString(token.getBytes(StandardCharsets.UTF_8));
    }

    /** PROPFIND 测连(depth=0 仅自身)/列目录(depth=1 返回子项,跳过自集合);path 以 / 开头 */
    public static List<DavItem> propfind(String baseUrl, String path, String user, String pass, int depth) {
        ThirdPartyHttp.Resp resp;
        try {
            resp = ThirdPartyHttp.request("webdav", "PROPFIND", joinUrl(baseUrl, path), Map.of(
                    "Authorization", basicAuth(user, pass),
                    "Depth", String.valueOf(depth),
                    "Content-Type", "application/xml"), PROPFIND_BODY.getBytes(StandardCharsets.UTF_8), 15000);
        } catch (IOException e) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "无法连接 WebDAV 服务器: " + e.getMessage());
        }
        if (resp.status() == 404) {
            throw new BizException(ResultCode.NOT_FOUND, "目录不存在");
        }
        if (resp.status() == 401 || resp.status() == 403) {
            throw new BizException(ResultCode.BAD_REQUEST, "WebDAV 认证失败,请检查账号与应用密码");
        }
        if (!resp.ok()) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "WebDAV 服务器返回 HTTP " + resp.status());
        }
        return parseMultiStatus(resp.body(), baseUrl, path);
    }

    /** 打开文件流(range 原样透传,支持 206 拖进度条);调用方负责关闭流 */
    public static WebDavStream open(String baseUrl, String path, String user, String pass, String range) {
        org.slf4j.Logger tp = Loggers.thirdParty("webdav");
        long t0 = System.currentTimeMillis();
        tp.info(">>> STREAM GET path={}", path);
        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(joinUrl(baseUrl, path)).toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(60000);
            conn.setRequestProperty("Authorization", basicAuth(user, pass));
            if (range != null && !range.isBlank()) {
                conn.setRequestProperty("Range", range);
            }
            int status = conn.getResponseCode();
            if (status != 200 && status != 206) {
                conn.disconnect();
                tp.error("!!! STREAM GET path={} status={} costMs={}", path, status, System.currentTimeMillis() - t0);
                if (status == 404) {
                    throw new BizException(ResultCode.NOT_FOUND, "文件不存在");
                }
                if (status == 401 || status == 403) {
                    throw new BizException(ResultCode.BAD_REQUEST, "WebDAV 认证失败,请检查账号与应用密码");
                }
                throw new BizException(ResultCode.INTERNAL_ERROR, "WebDAV 文件读取失败: HTTP " + status);
            }
            tp.info("<<< STREAM GET path={} status={} len={} costMs={}", path, status,
                    conn.getContentLengthLong(), System.currentTimeMillis() - t0);
            return new WebDavStream(status, conn.getContentLengthLong(),
                    conn.getHeaderField("Content-Range"), conn.getInputStream());
        } catch (BizException e) {
            throw e;
        } catch (IOException e) {
            tp.error("!!! STREAM GET path={} failed costMs={}", path, System.currentTimeMillis() - t0, e);
            throw new BizException(ResultCode.INTERNAL_ERROR, "读取 WebDAV 文件失败: " + e.getMessage());
        }
    }

    /** 解析 207 Multi-Status:相对路径 = 解码 href − baseUrl 路径前缀;跳过自集合条目 */
    private static List<DavItem> parseMultiStatus(String xml, String baseUrl, String requestPath) {
        List<DavItem> items = new ArrayList<>();
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setFeature("http://xml.org/sax/features/external-general-entities", false);
            f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            f.setXIncludeAware(false);
            f.setExpandEntityReferences(false);
            Document doc = f.newDocumentBuilder()
                    .parse(new org.xml.sax.InputSource(new java.io.StringReader(xml)));
            String basePath = decode(hrefPath(baseUrl));
            String self = trimSlash(requestPath);
            NodeList responses = doc.getElementsByTagNameNS(DAV_NS, "response");
            for (int i = 0; i < responses.getLength(); i++) {
                Element r = (Element) responses.item(i);
                String href = text(firstNS(r, "href"));
                if (href == null || href.isBlank()) {
                    continue;
                }
                String decoded = decode(hrefPath(href));
                String rel = trimSlash(decoded.startsWith(basePath) ? decoded.substring(basePath.length()) : decoded);
                if (rel.isEmpty() || rel.equals(self)) {
                    continue; // 自集合条目
                }
                boolean dir = isCollection(r) || decoded.endsWith("/");
                String name = rel.substring(rel.lastIndexOf('/') + 1);
                Long size = null;
                String lenText = text(firstNS(r, "getcontentlength"));
                if (lenText != null && !lenText.isBlank()) {
                    try {
                        size = Long.parseLong(lenText.trim());
                    } catch (NumberFormatException ignored) {
                    }
                }
                Long modified = parseHttpDate(text(firstNS(r, "getlastmodified")));
                items.add(new DavItem(name, dir, dir ? null : size, modified));
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "解析 WebDAV 目录响应失败: " + e.getMessage());
        }
        return items;
    }

    /** href 可能是服务器根相对路径或绝对 URI,统一取 URL path 部分 */
    private static String hrefPath(String href) {
        if (href.startsWith("http://") || href.startsWith("https://")) {
            try {
                return URI.create(href).getRawPath();
            } catch (IllegalArgumentException e) {
                return href;
            }
        }
        return href;
    }

    /** 百分号解码:先把字面 '+' 转义为 %2B,避免 URLDecoder 把它转成空格 */
    private static String decode(String raw) {
        try {
            return URLDecoder.decode(raw.replace("+", "%2B"), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return raw;
        }
    }

    /** 请求 URL 的 path 段百分号编码(UTF-8,空格 %20);段内已编码的 % 不重复编码 */
    static String encodePath(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        String[] segs = path.split("/", -1);
        for (int i = 0; i < segs.length; i++) {
            String seg = segs[i];
            if (i > 0) {
                sb.append('/');
            }
            if (seg.isEmpty()) {
                continue;
            }
            sb.append(seg.contains("%")
                    ? seg // 调用方已编码过
                    : java.net.URLEncoder.encode(seg, StandardCharsets.UTF_8).replace("+", "%20"));
        }
        return sb.toString();
    }

    private static String joinUrl(String baseUrl, String path) {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return base + encodePath(path.startsWith("/") ? path : "/" + path);
    }

    private static boolean isCollection(Element response) {
        Node rt = firstNS(response, "resourcetype");
        if (rt instanceof Element el) {
            return el.getElementsByTagNameNS(DAV_NS, "collection").getLength() > 0;
        }
        return false;
    }

    private static Long parseHttpDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli();
        } catch (Exception e) {
            return null;
        }
    }

    private static Node firstNS(Element parent, String localName) {
        NodeList list = parent.getElementsByTagNameNS(DAV_NS, localName);
        return list.getLength() > 0 ? list.item(0) : null;
    }

    private static String text(Node node) {
        return node == null ? null : node.getTextContent();
    }

    private static String trimSlash(String p) {
        String s = p == null ? "" : p;
        while (s.endsWith("/") && s.length() > 1) {
            s = s.substring(0, s.length() - 1);
        }
        if (s.equals("/")) {
            s = "";
        }
        return s;
    }
}
