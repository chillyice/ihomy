package com.ihomy.service;

import cn.hutool.core.util.EscapeUtil;
import cn.hutool.crypto.SecureUtil;
import com.ihomy.common.BizException;
import com.ihomy.common.HttpUrlUtil;
import com.ihomy.common.ResultCode;
import com.ihomy.common.ThirdPartyHttp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 站外链接预览(unfurl):抓取 og:title/og:description/og:image/og:site_name,<title> 兜底。
 * 四道防线(计划 §4.4):协议白名单 + 私网 IP 拒绝(逐跳)+ 超时与响应体封顶 + 每用户限流;
 * 任何一步失败都静默降级为纯链接(preview=null),消息本身照发不受影响。
 * 成功结果 Redis 缓存 24h;失败不缓存(瞬时故障下次可重试,限流兜底)。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatUnfurlService {

    private final StringRedisTemplate redisTemplate;
    private final tools.jackson.databind.ObjectMapper objectMapper;

    private static final String CACHE_KEY = "ihomy:chat:unfurl:";
    private static final String RATE_KEY = "ihomy:chat:unfurl-rl:";
    private static final Duration CACHE_TTL = Duration.ofHours(24);
    private static final int RATE_MAX_PER_MIN = 10;
    private static final int TIMEOUT_MS = 3000;
    private static final int MAX_BODY_BYTES = 1024 * 1024;
    private static final int MAX_HOPS = 2;
    /** 部分站点对非浏览器 UA 拒答,用常规浏览器 UA 抓取 */
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36";

    private static final int TITLE_MAX = 200;
    private static final int DESC_MAX = 500;
    private static final int IMAGE_MAX = 1000;
    private static final int SITE_MAX = 100;

    /** og:meta 两种属性顺序都兼容;content 取第一个引号段(og 值不含引号,够用) */
    private static final Pattern OG_PROP_FIRST = Pattern.compile(
            "<meta[^>]*?(?:property|name)=[\"']og:(\\w+)[\"'][^>]*?content=[\"'](.*?)[\"'][^>]*>",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern OG_CONTENT_FIRST = Pattern.compile(
            "<meta[^>]*?content=[\"'](.*?)[\"'][^>]*?(?:property|name)=[\"']og:(\\w+)[\"'][^>]*>",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TITLE_TAG = Pattern.compile(
            "<title[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /**
     * 抓取链接预览。返回 {url: 规范化地址, preview: {title?,description?,image?,siteName?} | null}。
     * 限流超限抛 BizException(TOO_MANY_REQUESTS);其余失败一律降级返回 null preview。
     */
    public Map<String, Object> unfurl(Long userId, String rawUrl) {
        // 每用户固定窗口限流(口径同 AuthGuardService.checkRate,这里按用户计数)
        Long n = redisTemplate.opsForValue().increment(RATE_KEY + userId);
        if (n != null && n == 1L) {
            redisTemplate.expire(RATE_KEY + userId, Duration.ofMinutes(1));
        }
        if (n != null && n > RATE_MAX_PER_MIN) {
            throw new BizException(ResultCode.TOO_MANY_REQUESTS, "操作过于频繁,请稍后再试");
        }
        String url = HttpUrlUtil.normalize(rawUrl, "链接格式不正确", "这个链接不能抓取预览");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("url", url);
        result.put("preview", fetchPreview(url));
        return result;
    }

    /** 抓取 + 解析 + 缓存;失败返回 null(纯链接降级) */
    private Map<String, Object> fetchPreview(String url) {
        String md5 = SecureUtil.md5(url);
        try {
            String cached = redisTemplate.opsForValue().get(CACHE_KEY + md5);
            if (cached != null) {
                Map<?, ?> node = objectMapper.readValue(cached, Map.class);
                Map<String, Object> preview = new LinkedHashMap<>();
                node.forEach((k, v) -> preview.put(String.valueOf(k), v));
                return preview.isEmpty() ? null : preview;
            }
        } catch (Exception e) {
            log.warn("unfurl 缓存读取失败 url={}", url, e);
        }
        Map<String, Object> preview = null;
        try {
            String html = fetchHtml(url);
            if (html != null) {
                preview = parse(html, url);
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.info("unfurl 抓取失败,降级纯链接 url={}", url, e);
        }
        if (preview != null) {
            try {
                redisTemplate.opsForValue().set(CACHE_KEY + md5, objectMapper.writeValueAsString(preview), CACHE_TTL);
            } catch (Exception e) {
                log.warn("unfurl 缓存写入失败 url={}", url, e);
            }
        }
        return preview;
    }

    /** 抓取 HTML:仅 text/html,≤2 跳重定向逐跳过 SSRF 检查;不满足条件返回 null */
    private String fetchHtml(String url) throws Exception {
        String current = url;
        for (int hop = 0; hop <= MAX_HOPS; hop++) {
            if (isPrivateTarget(current)) {
                return null;
            }
            ThirdPartyHttp.Resp resp = ThirdPartyHttp.getCapped("chat-unfurl", current,
                    Map.of("User-Agent", USER_AGENT, "Accept", "text/html"), TIMEOUT_MS, MAX_BODY_BYTES);
            if (resp.status() >= 300 && resp.status() < 400 && resp.location() != null) {
                current = resolveRedirect(current, resp.location());
                continue;
            }
            if (!resp.ok()) {
                return null;
            }
            String ctype = resp.contentType() == null ? "" : resp.contentType().toLowerCase();
            if (!ctype.startsWith("text/html")) {
                return null;
            }
            return resp.body();
        }
        return null;
    }

    /** 相对/绝对 Location 都解析成绝对地址;非法目标抛业务异常 → 上层降级 */
    private String resolveRedirect(String base, String location) {
        String abs = URI.create(base).resolve(location.trim()).toString();
        return HttpUrlUtil.normalize(abs, "链接格式不正确", "这个链接不能抓取预览");
    }

    /** DNS 解析后落私网/回环/链路本地/云元数据段则拒绝(与 HttpUrlUtil.isBlockedHost 互补) */
    private boolean isPrivateTarget(String url) {
        try {
            String host = URI.create(url).getHost();
            if (host == null) {
                return true;
            }
            for (InetAddress addr : InetAddress.getAllByName(host)) {
                if (HttpUrlUtil.isPrivateAddress(addr)) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    /** 从 HTML 抽 og: 四字段 + <title> 兜底;字段全缺返回 null */
    private Map<String, Object> parse(String html, String baseUrl) {
        Map<String, String> og = new LinkedHashMap<>();
        collectOg(OG_PROP_FIRST.matcher(html), og, true);
        collectOg(OG_CONTENT_FIRST.matcher(html), og, false);
        Map<String, Object> preview = new LinkedHashMap<>();
        String title = clean(og.get("title"));
        if (title == null) {
            Matcher m = TITLE_TAG.matcher(html);
            if (m.find()) {
                title = clean(m.group(1));
            }
        }
        if (notOver(title, TITLE_MAX)) {
            preview.put("title", title);
        }
        String desc = clean(og.get("description"));
        if (notOver(desc, DESC_MAX)) {
            preview.put("description", desc);
        }
        String siteName = clean(og.get("site_name"));
        if (notOver(siteName, SITE_MAX)) {
            preview.put("siteName", siteName);
        }
        String image = clean(og.get("image"));
        if (image != null && (image.startsWith("http://") || image.startsWith("https://") || image.startsWith("//"))) {
            image = resolveImage(image, baseUrl);
            if (notOver(image, IMAGE_MAX)) {
                preview.put("image", image);
            }
        }
        return preview.isEmpty() ? null : preview;
    }

    private void collectOg(Matcher m, Map<String, String> og, boolean propFirst) {
        while (m.find() && og.size() < 8) {
            String key = propFirst ? m.group(1) : m.group(2);
            String value = propFirst ? m.group(2) : m.group(1);
            if (!og.containsKey(key)) {
                og.put(key.toLowerCase(), value);
            }
        }
    }

    /** 相对图片地址按页面 URL 解析为绝对地址;非 http(s) 返回 null */
    private String resolveImage(String raw, String baseUrl) {
        try {
            String abs = raw.startsWith("//") ? "https:" + raw : raw;
            if (abs.startsWith("http://") || abs.startsWith("https://")) {
                return abs;
            }
            String resolved = URI.create(baseUrl).resolve(abs).toString();
            return resolved.startsWith("http://") || resolved.startsWith("https://") ? resolved : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** 去空白 + HTML 反转义;空串归 null */
    private String clean(String v) {
        if (v == null) {
            return null;
        }
        String s = EscapeUtil.unescapeHtml4(v).replaceAll("\\s+", " ").trim();
        return s.isEmpty() ? null : s;
    }

    private boolean notOver(String v, int max) {
        return v != null && v.length() <= max;
    }
}
