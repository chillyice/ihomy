package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ihomy.common.BizException;
import com.ihomy.common.ResultCode;
import com.ihomy.entity.BaiduCredential;
import com.ihomy.entity.StorageDevice;
import com.ihomy.entity.SysUser;
import com.ihomy.mapper.BaiduCredentialMapper;
import com.ihomy.mapper.StorageDeviceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 存储设备管理服务(家庭级):默认系统设备(本地磁盘)恒在列表首位,其余为自定义设备。
 */
@Service
@RequiredArgsConstructor
public class StorageService {

    private final StorageDeviceMapper storageDeviceMapper;
    private final BaiduCredentialMapper baiduCredentialMapper;
    private final ParameterService parameterService;
    private final StringRedisTemplate redis;
    private final ObjectMapper json = new ObjectMapper();

    @Value("${file.upload-dir}")
    private String uploadDir;

    /* ---------- 设备 CRUD ---------- */

    public List<Map<String, Object>> listDevices(Long familyId) {
        List<Map<String, Object>> result = new ArrayList<>();
        result.add(systemDevice());
        storageDeviceMapper.selectList(new LambdaQueryWrapper<StorageDevice>()
                        .eq(StorageDevice::getFamilyId, familyId).orderByAsc(StorageDevice::getId))
                .forEach(d -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", d.getId());
                    m.put("name", d.getName());
                    m.put("deviceType", d.getDeviceType());
                    m.put("rootPath", d.getRootPath());
                    m.put("status", d.getStatus());
                    result.add(m);
                });
        return result;
    }

    private Map<String, Object> systemDevice() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", 0L);
        m.put("name", "系统(本地磁盘)");
        m.put("deviceType", "SYSTEM");
        m.put("rootPath", uploadDir);
        m.put("status", "ACTIVE");
        return m;
    }

    /** 取设备;id=0 或 null 返回 null(即系统默认设备) */
    public StorageDevice getDevice(Long familyId, Long deviceId) {
        if (deviceId == null || deviceId == 0L) return null;
        StorageDevice d = storageDeviceMapper.selectById(deviceId);
        if (d == null || !d.getFamilyId().equals(familyId)) {
            throw new BizException(ResultCode.NOT_FOUND, "存储设备不存在");
        }
        return d;
    }

    /** 按主键取设备(签名中转端点无用户上下文,签名本身即凭证) */
    public StorageDevice getDeviceById(Long deviceId) {
        if (deviceId == null || deviceId == 0L) return null;
        return storageDeviceMapper.selectById(deviceId);
    }

    public StorageDevice addDevice(Long familyId, String name, String deviceType, String rootPath, SysUser user) {
        if (name == null || name.isBlank()) {
            throw new BizException(ResultCode.BAD_REQUEST, "设备名称不能为空");
        }
        boolean baidu = "BAIDU".equals(deviceType);
        if (baidu) {
            rootPath = "/"; // 百度网盘走 API 无本地根目录,占位待适配器解释;凭证存 sys_baidu_credential
        } else if (rootPath == null || rootPath.isBlank()) {
            throw new BizException(ResultCode.BAD_REQUEST, "设备名称与路径不能为空");
        } else {
            Path root = Paths.get(rootPath).toAbsolutePath().normalize();
            if (!Files.isDirectory(root)) {
                throw new BizException(ResultCode.BAD_REQUEST, "路径不存在或不是目录: " + root);
            }
            rootPath = root.toString();
        }
        StorageDevice d = new StorageDevice();
        d.setFamilyId(familyId);
        d.setName(name.trim());
        d.setDeviceType(deviceType == null || deviceType.isBlank() ? "NAS" : deviceType);
        d.setRootPath(rootPath);
        d.setStatus("ACTIVE");
        d.setCreatedBy(user == null ? null : user.getId());
        storageDeviceMapper.insert(d);
        return d;
    }

    public void updateDevice(Long familyId, Long deviceId, String name, String deviceType, String rootPath) {
        StorageDevice d = getDevice(familyId, deviceId);
        if (name != null && !name.isBlank()) d.setName(name.trim());
        if (deviceType != null && !deviceType.isBlank()) d.setDeviceType(deviceType);
        boolean baidu = "BAIDU".equals(d.getDeviceType());
        if (baidu) {
            d.setRootPath("/");
        } else if (rootPath != null && !rootPath.isBlank()) {
            Path root = Paths.get(rootPath).toAbsolutePath().normalize();
            if (!Files.isDirectory(root)) {
                throw new BizException(ResultCode.BAD_REQUEST, "路径不存在或不是目录: " + root);
            }
            d.setRootPath(root.toString());
        }
        storageDeviceMapper.updateById(d);
    }

    public void deleteDevice(Long familyId, Long deviceId) {
        storageDeviceMapper.deleteById(getDevice(familyId, deviceId).getId());
    }

    /* ---------- 文件浏览/读取(防路径遍历) ---------- */

    private Path deviceRoot(StorageDevice device) {
        return Paths.get(device == null ? uploadDir : device.getRootPath()).toAbsolutePath().normalize();
    }

    private Path resolveSafe(Path root, String rel) {
        String r = rel == null ? "" : rel.trim().replace('\\', '/');
        if (r.startsWith("/")) r = r.substring(1);
        Path target = root.resolve(r).normalize();
        if (!target.startsWith(root)) {
            throw new BizException(ResultCode.BAD_REQUEST, "非法路径");
        }
        return target;
    }

    public List<Map<String, Object>> browse(StorageDevice device, String path) {
        if ("BAIDU".equals(device.getDeviceType())) return baiduBrowse(device, path);
        Path root = deviceRoot(device);
        Path dir = resolveSafe(root, path);
        if (!Files.isDirectory(dir)) {
            throw new BizException(ResultCode.NOT_FOUND, "目录不存在");
        }
        List<Map<String, Object>> items = new ArrayList<>();
        try (var stream = Files.newDirectoryStream(dir)) {
            for (Path p : stream) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("name", p.getFileName().toString());
                m.put("isDir", Files.isDirectory(p));
                m.put("size", Files.isDirectory(p) ? null : Files.size(p));
                m.put("modified", Files.getLastModifiedTime(p).toMillis());
                items.add(m);
            }
        } catch (Exception e) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "读取目录失败: " + e.getMessage());
        }
        items.sort((a, b) -> {
            boolean da = (Boolean) a.get("isDir"), db = (Boolean) b.get("isDir");
            if (da != db) return da ? -1 : 1;
            return ((String) a.get("name")).compareToIgnoreCase((String) b.get("name"));
        });
        return items;
    }

    public byte[] readFileBytes(StorageDevice device, String path) {
        Path root = deviceRoot(device);
        Path f = resolveSafe(root, path);
        if (!Files.isRegularFile(f)) {
            throw new BizException(ResultCode.NOT_FOUND, "文件不存在");
        }
        try {
            return Files.readAllBytes(f);
        } catch (Exception e) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "读取文件失败");
        }
    }

    public String downloadName(StorageDevice device, String path) {
        return resolveSafe(deviceRoot(device), path).getFileName().toString();
    }

    /** 百度网盘文件流(dlink 服务器中转,不落盘不缓冲) */
    public record BaiduFileStream(InputStream in, long length) {}

    /* ---------- 一键同步(旧拷贝式已退役,统一走 AlbumMapService 映射式) ---------- */

    /* ---------- 百度网盘接入凭证 ---------- */

    /** 凭证视图:SecretKey/SignKey/token 不回传,仅返回是否已配置/已授权 */
    public Map<String, Object> getBaiduCredential(Long familyId) {
        BaiduCredential c = baiduCredentialMapper.selectOne(new LambdaQueryWrapper<BaiduCredential>()
                .eq(BaiduCredential::getFamilyId, familyId));
        Map<String, Object> m = new LinkedHashMap<>();
        if (c == null) return m;
        m.put("appId", c.getAppId());
        m.put("appKey", c.getAppKey());
        m.put("secretKeySet", c.getSecretKey() != null && !c.getSecretKey().isBlank());
        m.put("signKeySet", c.getSignKey() != null && !c.getSignKey().isBlank());
        m.put("authorized", c.getAccessToken() != null && !c.getAccessToken().isBlank());
        m.put("tokenExpiresAt", c.getTokenExpiresAt());
        m.put("updatedAt", c.getUpdatedAt());
        return m;
    }

    /** 保存凭证:SecretKey/SignKey ENC 加密入库,留空保留原值;appId/appKey 必填 */
    public void saveBaiduCredential(Long familyId, String appId, String appKey, String secretKey, String signKey) {
        if (appId == null || appId.isBlank() || appKey == null || appKey.isBlank()) {
            throw new BizException(ResultCode.BAD_REQUEST, "AppID 和 AppKey 不能为空");
        }
        BaiduCredential c = baiduCredentialMapper.selectOne(new LambdaQueryWrapper<BaiduCredential>()
                .eq(BaiduCredential::getFamilyId, familyId));
        if (c == null) {
            c = new BaiduCredential();
            c.setFamilyId(familyId);
            c.setAppId(appId.trim());
            c.setAppKey(appKey.trim());
            if (secretKey != null && !secretKey.isBlank()) c.setSecretKey(parameterService.encrypt(secretKey.trim()));
            if (signKey != null && !signKey.isBlank()) c.setSignKey(parameterService.encrypt(signKey.trim()));
            baiduCredentialMapper.insert(c);
        } else {
            c.setAppId(appId.trim());
            c.setAppKey(appKey.trim());
            if (secretKey != null && !secretKey.isBlank()) c.setSecretKey(parameterService.encrypt(secretKey.trim()));
            if (signKey != null && !signKey.isBlank()) c.setSignKey(parameterService.encrypt(signKey.trim()));
            baiduCredentialMapper.updateById(c);
        }
    }

    /* ---------- 百度网盘 OAuth 授权(授权码模式) ---------- */

    private static final String BAIDU_STATE_PREFIX = "ihomy:baidu:state:";
    private static final Duration BAIDU_STATE_TTL = Duration.ofMinutes(10);

    /** 生成授权跳转 URL:state 存 Redis 绑定家庭(10 分钟有效,防 CSRF/令牌替换攻击) */
    public String getBaiduAuthUrl(Long familyId, String redirectUri) {
        BaiduCredential c = requireBaiduCredential(familyId);
        String state = java.util.UUID.randomUUID().toString().replace("-", "");
        redis.opsForValue().set(BAIDU_STATE_PREFIX + state, String.valueOf(familyId), BAIDU_STATE_TTL);
        return "https://openapi.baidu.com/oauth/2.0/authorize?response_type=code"
                + "&client_id=" + URLEncoder.encode(c.getAppKey(), StandardCharsets.UTF_8)
                + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
                + "&scope=basic,netdisk&state=" + state;
    }

    /** 授权回调:校验 state → 授权码换 access_token/refresh_token → 加密存储 */
    public void baiduAuthCallback(Long familyId, String code, String state, String redirectUri) {
        BaiduCredential c = requireBaiduCredential(familyId);
        // state 一次性校验:必须存在且与本家庭一致
        String key = BAIDU_STATE_PREFIX + state;
        String bound = redis.opsForValue().get(key);
        if (state == null || state.isBlank() || bound == null || !bound.equals(String.valueOf(familyId))) {
            throw new BizException(ResultCode.BAD_REQUEST, "授权状态已过期或不合法,请重新发起授权");
        }
        redis.delete(key);
        if (code == null || code.isBlank()) {
            throw new BizException(ResultCode.BAD_REQUEST, "缺少授权码");
        }

        // 百度 OAuth2.0 授权码换 token(GET + query)
        String urlStr = "https://openapi.baidu.com/oauth/2.0/token?grant_type=authorization_code"
                + "&code=" + URLEncoder.encode(code, StandardCharsets.UTF_8)
                + "&client_id=" + URLEncoder.encode(c.getAppKey(), StandardCharsets.UTF_8)
                + "&client_secret=" + URLEncoder.encode(parameterService.decrypt(c.getSecretKey()), StandardCharsets.UTF_8)
                + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8);
        JsonNode resp = httpGetJson(urlStr);
        if (resp == null || resp.has("error")) {
            String detail = resp == null ? "网络错误或响应非 JSON" : resp.path("error_description").asText(resp.path("error").asText("unknown"));
            throw new BizException(ResultCode.BAD_REQUEST, "百度授权失败: " + detail);
        }
        String accessToken = resp.path("access_token").asText(null);
        if (accessToken == null || accessToken.isBlank()) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "百度授权响应缺少 access_token");
        }
        c.setAccessToken(parameterService.encrypt(accessToken));
        if (resp.hasNonNull("refresh_token")) {
            c.setRefreshToken(parameterService.encrypt(resp.get("refresh_token").asText()));
        }
        c.setTokenExpiresAt(LocalDateTime.now().plusSeconds(resp.path("expires_in").asLong(2592000L)));
        baiduCredentialMapper.updateById(c);
    }

    /** 凭证必须已配置(含 SecretKey,换 token 要用) */
    private BaiduCredential requireBaiduCredential(Long familyId) {
        BaiduCredential c = baiduCredentialMapper.selectOne(new LambdaQueryWrapper<BaiduCredential>()
                .eq(BaiduCredential::getFamilyId, familyId));
        if (c == null || c.getSecretKey() == null || c.getSecretKey().isBlank()) {
            throw new BizException(ResultCode.BAD_REQUEST, "请先在存储设置中配置百度网盘凭证(AppID/AppKey/SecretKey)");
        }
        return c;
    }

    /** 简易 HTTP GET 取 JSON(百度 OAuth 端点返回未压缩 JSON) */
    private JsonNode httpGetJson(String urlStr) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            int status = conn.getResponseCode();
            InputStream is = (status >= 200 && status < 300) ? conn.getInputStream() : conn.getErrorStream();
            String body = is == null ? "" : new String(is.readAllBytes(), StandardCharsets.UTF_8);
            conn.disconnect();
            return json.readTree(body);
        } catch (Exception e) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "请求百度授权接口失败: " + e.getMessage());
        }
    }

    /* ---------- 百度网盘文件访问(xpan API + dlink 服务器中转) ---------- */

    /** 浏览百度网盘目录:xpan file list(目录优先,按名称排序,与本地浏览一致) */
    public List<Map<String, Object>> baiduBrowse(StorageDevice device, String path) {
        BaiduCredential c = requireBaiduCredential(device.getFamilyId());
        JsonNode resp = baiduListDir(c, baiduNormalizePath(path, true));
        List<Map<String, Object>> items = new ArrayList<>();
        for (JsonNode n : resp.path("list")) {
            boolean isDir = n.path("isdir").asInt() == 1;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", n.path("server_filename").asText(""));
            m.put("isDir", isDir);
            m.put("size", isDir ? null : n.path("size").asLong());
            m.put("modified", n.path("server_mtime").asLong() * 1000);
            m.put("fsId", isDir ? null : n.path("fs_id").asLong());
            items.add(m);
        }
        items.sort((a, b) -> {
            boolean da = (Boolean) a.get("isDir"), db = (Boolean) b.get("isDir");
            if (da != db) return da ? -1 : 1;
            return ((String) a.get("name")).compareToIgnoreCase((String) b.get("name"));
        });
        return items;
    }

    /** 打开百度网盘文件:dlink 必须由服务器带 UA=pan.baidu.com 中转(浏览器直链 403),流式不缓冲 */
    public BaiduFileStream baiduOpen(StorageDevice device, String path) {
        return baiduOpen(device, path, null);
    }

    /** fsId 已知时跳过"列父目录找 fs_id"(影子照片直存 fs_id,省一次列表调用) */
    public BaiduFileStream baiduOpen(StorageDevice device, String path, Long fsId) {
        String p = baiduNormalizePath(path, false);
        String dir = p.substring(0, p.lastIndexOf('/'));
        String name = p.substring(p.lastIndexOf('/') + 1);
        BaiduCredential c = requireBaiduCredential(device.getFamilyId());

        // 1) fs_id 直达;未知则列父目录按文件名定位
        final long fid;
        if (fsId != null && fsId > 0) {
            fid = fsId;
        } else {
            JsonNode listResp = baiduListDir(c, dir.isEmpty() ? "/" : dir);
            long found = 0;
            for (JsonNode n : listResp.path("list")) {
                if (n.path("isdir").asInt() == 0 && name.equals(n.path("server_filename").asText())) {
                    found = n.path("fs_id").asLong();
                    break;
                }
            }
            if (found == 0) throw new BizException(ResultCode.NOT_FOUND, "文件不存在: " + name);
            fid = found;
        }

        // 2) filemetas 拿 dlink
        JsonNode metas = baiduGet(c, token -> "https://pan.baidu.com/rest/2.0/xpan/multimedia?method=filemetas&dlink=1"
                + "&access_token=" + URLEncoder.encode(token, StandardCharsets.UTF_8)
                + "&fsids=" + URLEncoder.encode("[" + fid + "]", StandardCharsets.UTF_8));
        String dlink = metas.path("list").path(0).path("dlink").asText(null);
        if (dlink == null || dlink.isBlank()) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "百度网盘未返回下载链接(dlink)");
        }

        // 3) 打开 dlink 流(手动跟随 302 到 CDN,重放 UA 防丢失)
        String token = baiduAccessToken(c);
        try {
            HttpURLConnection conn = baiduDlinkConnect(dlink + (dlink.contains("?") ? "&" : "?")
                    + "access_token=" + URLEncoder.encode(token, StandardCharsets.UTF_8));
            int status = conn.getResponseCode();
            if (status == 301 || status == 302) {
                String loc = conn.getHeaderField("Location");
                long length = conn.getContentLengthLong();
                conn.disconnect();
                if (loc == null) throw new BizException(ResultCode.INTERNAL_ERROR, "百度网盘下载重定向缺少 Location");
                conn = baiduDlinkConnect(loc);
                if (length <= 0) length = conn.getContentLengthLong();
                status = conn.getResponseCode();
            }
            if (status != 200 && status != 206) {
                conn.disconnect();
                throw new BizException(ResultCode.INTERNAL_ERROR, "百度网盘文件读取失败: HTTP " + status);
            }
            return new BaiduFileStream(conn.getInputStream(), conn.getContentLengthLong());
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "下载百度网盘文件失败: " + e.getMessage());
        }
    }

    private HttpURLConnection baiduDlinkConnect(String urlStr) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestProperty("User-Agent", "pan.baidu.com"); // 百度强制要求,否则 403
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(60000);
        conn.setInstanceFollowRedirects(false);
        return conn;
    }

    /** xpan 目录列表;ponytail: limit=1000 不分页,家庭网盘单目录超千文件再补分页 */
    private JsonNode baiduListDir(BaiduCredential c, String dir) {
        return baiduGet(c, token -> "https://pan.baidu.com/rest/2.0/xpan/file?method=list&order=name&start=0&limit=1000"
                + "&access_token=" + URLEncoder.encode(token, StandardCharsets.UTF_8)
                + "&dir=" + URLEncoder.encode(dir, StandardCharsets.UTF_8));
    }

    /** xpan GET:errno 111/-6(token 过期/失效)时刷新后重试一次,其余非 0 抛业务异常 */
    private JsonNode baiduGet(BaiduCredential c, java.util.function.UnaryOperator<String> urlWithToken) {
        JsonNode resp = httpGetJson(urlWithToken.apply(baiduAccessToken(c)));
        long errno = resp == null ? -1 : resp.path("errno").asLong(0);
        if (errno == 111 || errno == -6) {
            resp = httpGetJson(urlWithToken.apply(refreshBaiduToken(c)));
            errno = resp == null ? -1 : resp.path("errno").asLong(0);
        }
        if (resp == null || errno != 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "百度网盘接口失败: errno=" + errno
                    + (resp == null ? "" : " " + resp.path("errmsg").asText("")));
        }
        return resp;
    }

    /** 取 access_token:距过期不足 5 分钟先刷新(避免调用必失败再重试) */
    private String baiduAccessToken(BaiduCredential c) {
        if (c.getAccessToken() == null || c.getAccessToken().isBlank()) {
            throw new BizException(ResultCode.BAD_REQUEST, "百度网盘尚未授权,请先在存储设置中发起授权");
        }
        if (c.getTokenExpiresAt() == null || c.getTokenExpiresAt().isBefore(LocalDateTime.now().plusMinutes(5))) {
            return refreshBaiduToken(c);
        }
        return parameterService.decrypt(c.getAccessToken());
    }

    /** refresh_token 换新 access_token(百度会同时下发新 refresh_token,旧作废,必须落库) */
    private String refreshBaiduToken(BaiduCredential c) {
        if (c.getRefreshToken() == null || c.getRefreshToken().isBlank()) {
            throw new BizException(ResultCode.BAD_REQUEST, "百度网盘授权已失效,请重新授权");
        }
        JsonNode resp = httpGetJson("https://openapi.baidu.com/oauth/2.0/token?grant_type=refresh_token"
                + "&refresh_token=" + URLEncoder.encode(parameterService.decrypt(c.getRefreshToken()), StandardCharsets.UTF_8)
                + "&client_id=" + URLEncoder.encode(c.getAppKey(), StandardCharsets.UTF_8)
                + "&client_secret=" + URLEncoder.encode(parameterService.decrypt(c.getSecretKey()), StandardCharsets.UTF_8));
        if (resp == null || resp.has("error")) {
            throw new BizException(ResultCode.BAD_REQUEST, "刷新百度授权失败,请重新授权: "
                    + (resp == null ? "网络错误" : resp.path("error_description").asText(resp.path("error").asText(""))));
        }
        String at = resp.path("access_token").asText(null);
        if (at == null || at.isBlank()) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "刷新百度授权响应缺少 access_token");
        }
        c.setAccessToken(parameterService.encrypt(at));
        if (resp.hasNonNull("refresh_token")) {
            c.setRefreshToken(parameterService.encrypt(resp.get("refresh_token").asText()));
        }
        c.setTokenExpiresAt(LocalDateTime.now().plusSeconds(resp.path("expires_in").asLong(2592000L)));
        baiduCredentialMapper.updateById(c);
        return at;
    }

    /** 百度网盘路径规范化:统一正斜杠、以 / 开头、禁 ..;dir=true 时目录缺省根目录 */
    private String baiduNormalizePath(String path, boolean dir) {
        String p = path == null ? "" : path.trim().replace('\\', '/');
        if (p.contains("..")) throw new BizException(ResultCode.BAD_REQUEST, "非法路径");
        if (!p.startsWith("/")) p = "/" + p;
        if (p.endsWith("/")) p = p.substring(0, p.length() - 1);
        if (p.isEmpty()) p = "/";
        if (!dir && p.equals("/")) throw new BizException(ResultCode.BAD_REQUEST, "缺少文件名");
        return p;
    }
}