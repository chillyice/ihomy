package com.ihomy.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ihomy.common.AiConst;
import com.ihomy.common.BizException;
import com.ihomy.common.ResultCode;
import com.ihomy.common.ThirdPartyHttp;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 百度短语音识别客户端(V9.50):非 OpenAI 协议,独立适配。
 * 鉴权:API Key(client_id) + Secret Key(client_secret) 经 OAuth 换 access_token(有效期 30 天,进程内缓存);
 * 识别:POST {base-url}(vop.baidu.com/server_api),JSON 体 format/rate/channel/cuid/token/dev_pid/speech(base64)/len。
 * 出站一律走 ThirdPartyHttp(thirdparty 日志 + URL/头脱敏);60 秒以内短语音。
 */
@Service
public class BaiduAsrClient {

    private final ObjectMapper mapper = new ObjectMapper();

    /** access_token 缓存:key=apiKey|secretKey(仅内存,不落盘),value=token 与过期时间(秒级提前 5 分钟失效) */
    private record Token(String token, long expireAtMillis) {
    }

    private final ConcurrentHashMap<String, Token> tokenCache = new ConcurrentHashMap<>();

    /** 常用错误码 → 中文提示(其余回退 err_msg) */
    private static final Map<Integer, String> ERRORS = Map.ofEntries(
            Map.entry(3300, "输入参数不正确"),
            Map.entry(3301, "音频质量过差"),
            Map.entry(3302, "鉴权失败(access_token 无效或过期)"),
            Map.entry(3303, "百度服务端问题"),
            Map.entry(3304, "用户请求超限(QPS)"),
            Map.entry(3305, "用户账户欠费"),
            Map.entry(3307, "百度服务端错误"),
            Map.entry(3308, "音频过长(须 60 秒以内)"),
            Map.entry(3309, "音频数据过大"),
            Map.entry(3310, "音频采样率不支持(仅 16000/8000)"),
            Map.entry(3311, "音频格式不支持(仅 pcm/wav/amr/m4a)"),
            Map.entry(3312, "speech/len 参数错误"));

    /**
     * 识别一段短语音,返回识别文本。
     *
     * @param baseUrl  百度识别接口地址(如 https://vop.baidu.com/server_api)
     * @param apiKey   百度 API Key(client_id)
     * @param secretKey 百度 Secret Key(client_secret)
     * @param devPid   识别模型(1537 普通话/1737 英语/1637 粤语/1837 四川话)
     * @param format   pcm/wav/amr/m4a
     * @param rate     采样率 16000/8000
     * @param cuid     用户唯一标识(≤60 字符)
     * @param audio    原始音频字节
     */
    public String recognize(String baseUrl, String apiKey, String secretKey, String devPid,
                            String format, int rate, String cuid, byte[] audio, int timeoutMs) {
        if (audio == null || audio.length == 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "请上传音频文件");
        }
        String token = accessToken(apiKey, secretKey, timeoutMs);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("format", format);
        body.put("rate", rate);
        body.put("channel", 1);
        body.put("cuid", cuid);
        body.put("token", token);
        body.put("dev_pid", Integer.parseInt(devPid == null || devPid.isBlank() ? AiConst.BAIDU_DEFAULT_DEV_PID : devPid.trim()));
        body.put("speech", Base64.getEncoder().encodeToString(audio));
        body.put("len", audio.length);
        Map<String, String> headers = Map.of("Content-Type", "application/json");
        try {
            ThirdPartyHttp.Resp resp = ThirdPartyHttp.request("baidu-asr", "POST", baseUrl, headers,
                    mapper.writeValueAsBytes(body), timeoutMs);
            if (!resp.ok()) {
                throw new BizException(ResultCode.INTERNAL_ERROR,
                        "百度语音识别返回异常(" + resp.status() + "):" + truncate(resp.body(), 200));
            }
            JsonNode root = mapper.readTree(resp.body());
            int errNo = root.path("err_no").asInt(-1);
            if (errNo != 0) {
                String msg = ERRORS.getOrDefault(errNo, root.path("err_msg").asText(""));
                // token 失效则清缓存,下次重取
                if (errNo == 3302 || errNo == 110 || errNo == 111) {
                    tokenCache.remove(cacheKey(apiKey, secretKey));
                }
                throw new BizException(ResultCode.INTERNAL_ERROR, "百度语音识别失败(" + errNo + "):" + msg);
            }
            JsonNode result = root.path("result");
            if (!result.isArray() || result.isEmpty()) {
                throw new BizException(ResultCode.INTERNAL_ERROR, "百度语音识别返回结果为空");
            }
            StringBuilder sb = new StringBuilder();
            for (JsonNode r : result) {
                sb.append(r.asText());
            }
            return sb.toString();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "百度语音识别调用失败:" + e.getMessage());
        }
    }

    /** 获取 access_token(缓存 30 天,提前 5 分钟失效;OAuth 失败抛 BizException) */
    private String accessToken(String apiKey, String secretKey, int timeoutMs) {
        String key = cacheKey(apiKey, secretKey);
        Token cached = tokenCache.get(key);
        if (cached != null && System.currentTimeMillis() < cached.expireAtMillis()) {
            return cached.token();
        }
        String url = AiConst.BAIDU_TOKEN_URL
                + "?grant_type=client_credentials"
                + "&client_id=" + encode(apiKey)
                + "&client_secret=" + encode(secretKey);
        try {
            ThirdPartyHttp.Resp resp = ThirdPartyHttp.request("baidu-asr", "POST", url,
                    Map.of("Content-Type", "application/x-www-form-urlencoded"), null, timeoutMs);
            if (!resp.ok()) {
                throw new BizException(ResultCode.INTERNAL_ERROR,
                        "百度鉴权失败(" + resp.status() + "):" + truncate(resp.body(), 200));
            }
            JsonNode root = mapper.readTree(resp.body());
            String token = root.path("access_token").asText("");
            if (token.isBlank()) {
                String err = root.path("error_description").asText(root.path("error").asText(""));
                throw new BizException(ResultCode.INTERNAL_ERROR, "百度鉴权失败:" + (err.isBlank() ? "未返回 access_token" : err));
            }
            long expiresIn = root.path("expires_in").asLong(2592000L);
            long expireAt = System.currentTimeMillis() + (expiresIn - 300) * 1000L;
            tokenCache.put(key, new Token(token, expireAt));
            return token;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "百度鉴权调用失败:" + e.getMessage());
        }
    }

    private String cacheKey(String apiKey, String secretKey) {
        return apiKey + "|" + secretKey;
    }

    private String encode(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
