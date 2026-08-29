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
    private final StorageSyncRunner syncRunner;
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

    /* ---------- 一键同步 ---------- */

    public Long startSync(StorageDevice device, boolean includeEmpty, SysUser user) {
        Long taskId = System.nanoTime();
        syncRunner.run(device, includeEmpty, user, taskId);
        return taskId;
    }

    public Map<String, Object> syncProgress(Long taskId) {
        return syncRunner.progress(taskId);
    }

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
}