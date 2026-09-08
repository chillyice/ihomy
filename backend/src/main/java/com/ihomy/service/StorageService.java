package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ihomy.common.BizException;
import com.ihomy.common.ResultCode;
import com.ihomy.common.ThirdPartyHttp;
import com.ihomy.common.WebDavClient;
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
    private final SignedUrlService signedUrlService;
    private final StringRedisTemplate redis;
    private final ObjectMapper json = new ObjectMapper();

    @Value("${file.upload-dir}")
    private String uploadDir;

    /* ---------- 设备 CRUD ---------- */

    /** WebDAV 系设备类型(NEXTCLOUD/WEBDAV):路径以 / 开头,凭证存 root_path(serverUrl|username|ENC 密码) */
    public static boolean isWebDavType(String deviceType) {
        return "WEBDAV".equals(deviceType) || "NEXTCLOUD".equals(deviceType);
    }

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
                    // WebDAV 设备 root_path 含凭证,只回 serverUrl|username(密码段剥掉,同百度密钥不回传)
                    m.put("rootPath", isWebDavType(d.getDeviceType()) ? maskWebDavRootPath(d.getRootPath()) : d.getRootPath());
                    m.put("status", d.getStatus());
                    result.add(m);
                });
        return result;
    }

    /** WebDAV root_path 脱敏:剥掉最后一段 ENC 密码,回 serverUrl|username */
    private String maskWebDavRootPath(String rootPath) {
        int p2 = rootPath == null ? -1 : rootPath.lastIndexOf('|');
        return p2 > 0 ? rootPath.substring(0, p2) : rootPath;
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

    public StorageDevice addDevice(Long familyId, String name, String deviceType, String rootPath,
                                   String username, String password, SysUser user) {
        if (name == null || name.isBlank()) {
            throw new BizException(ResultCode.BAD_REQUEST, "设备名称不能为空");
        }
        boolean baidu = "BAIDU".equals(deviceType);
        if (baidu) {
            rootPath = "/"; // 百度网盘走 API 无本地根目录,占位待适配器解释;凭证存 sys_baidu_credential
        } else if (isWebDavType(deviceType)) {
            rootPath = buildWebDavRootPath(deviceType, rootPath, username, password, null);
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

    public void updateDevice(Long familyId, Long deviceId, String name, String deviceType,
                             String rootPath, String username, String password) {
        StorageDevice d = getDevice(familyId, deviceId);
        if (name != null && !name.isBlank()) d.setName(name.trim());
        if (deviceType != null && !deviceType.isBlank()) d.setDeviceType(deviceType);
        boolean baidu = "BAIDU".equals(d.getDeviceType());
        if (baidu) {
            d.setRootPath("/");
        } else if (isWebDavType(d.getDeviceType())) {
            // 编辑密码留空沿用旧值;地址/账号变更会重新测连
            d.setRootPath(buildWebDavRootPath(d.getDeviceType(), rootPath, username, password, d.getRootPath()));
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
        List<Map<String, Object>> items;
        if ("BAIDU".equals(device.getDeviceType())) {
            items = baiduBrowse(device, path);
        } else if (isWebDavType(device.getDeviceType())) {
            items = webdavBrowse(device, path);
        } else {
            items = localBrowse(device, path);
        }
        // 文件项附签名 URL(免登录,前端 img/iframe/下载全走它;目录不需要)
        for (Map<String, Object> item : items) {
            if (!Boolean.TRUE.equals(item.get("isDir")) && device.getId() != null && device.getId() > 0) {
                String full = browsePath(device.getDeviceType(), path, String.valueOf(item.get("name")));
                item.put("signedUrl", signedUrlService.sign(device.getId(), full, null));
            }
        }
        return items;
    }

    /** 浏览项完整路径(签名用):百度/WebDAV 以 / 开头,本地设备相对路径拼接 */
    private String browsePath(String deviceType, String parent, String name) {
        String p = parent == null ? "" : parent;
        if ("BAIDU".equals(deviceType) || isWebDavType(deviceType)) {
            String base = p.isEmpty() || "/".equals(p) ? "" : p;
            return base + "/" + name;
        }
        return p.isEmpty() ? name : p + "/" + name;
    }

    /** 本地设备列目录(目录优先,按名称排序) */
    private List<Map<String, Object>> localBrowse(StorageDevice device, String path) {
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

    /** 本地设备文件定位(防路径遍历):供流式返回用,GB 级视频不能 readFileBytes 全量入堆 */
    public java.nio.file.Path resolveLocalFile(StorageDevice device, String path) {
        Path f = resolveSafe(deviceRoot(device), path);
        if (!Files.isRegularFile(f)) {
            throw new BizException(ResultCode.NOT_FOUND, "文件不存在");
        }
        return f;
    }

    public String downloadName(StorageDevice device, String path) {
        return resolveSafe(deviceRoot(device), path).getFileName().toString();
    }

    /* ---------- 文件管理写操作(新建/重命名/删除;系统设备只读) ---------- */

    /** 写操作仅限自定义设备:系统设备(本地上传根目录)存业务内容,只读 */
    private void requireWritable(StorageDevice device) {
        if (device == null || device.getId() == null || device.getId() == 0L) {
            throw new BizException(ResultCode.BAD_REQUEST, "系统设备不支持文件管理操作");
        }
    }

    /** 新建目录(本地/WebDAV/百度三分支) */
    public void mkdir(StorageDevice device, String path) {
        requireWritable(device);
        if (path == null || path.isBlank()) {
            throw new BizException(ResultCode.BAD_REQUEST, "目录路径不能为空");
        }
        if ("BAIDU".equals(device.getDeviceType())) {
            baiduMkdir(device, baiduNormalizePath(path, true));
        } else if (isWebDavType(device.getDeviceType())) {
            WebDavCreds c = parseWebDavCreds(device);
            WebDavClient.mkcol(c.serverUrl(), webdavNormalizePath(path, true), c.username(), c.password());
        } else {
            Path dir = resolveSafe(deviceRoot(device), path);
            if (Files.exists(dir)) {
                throw new BizException(ResultCode.BAD_REQUEST, "目录已存在");
            }
            try {
                Files.createDirectory(dir);
            } catch (Exception e) {
                throw new BizException(ResultCode.BAD_REQUEST, "新建目录失败: " + e.getMessage());
            }
        }
    }

    /** 重命名(同目录内改名;新名称禁路径分隔符与 ..) */
    public void rename(StorageDevice device, String path, String newName) {
        requireWritable(device);
        if (newName == null || newName.isBlank()) {
            throw new BizException(ResultCode.BAD_REQUEST, "新名称不能为空");
        }
        String name = newName.trim();
        if (name.contains("/") || name.contains("\\") || name.contains("..")) {
            throw new BizException(ResultCode.BAD_REQUEST, "名称不能包含路径分隔符或 ..");
        }
        if ("BAIDU".equals(device.getDeviceType())) {
            baiduRename(device, baiduNormalizePath(path, false), name);
        } else if (isWebDavType(device.getDeviceType())) {
            WebDavCreds c = parseWebDavCreds(device);
            String p = webdavNormalizePath(path, false);
            String parent = p.substring(0, p.lastIndexOf('/'));
            WebDavClient.move(c.serverUrl(), p, parent + "/" + name, c.username(), c.password());
        } else {
            Path src = resolveSafe(deviceRoot(device), path);
            if (!Files.exists(src)) {
                throw new BizException(ResultCode.NOT_FOUND, "文件或目录不存在");
            }
            try {
                Files.move(src, src.getParent().resolve(name));
            } catch (Exception e) {
                throw new BizException(ResultCode.BAD_REQUEST, "重命名失败: " + e.getMessage());
            }
        }
    }

    /** 批量删除(百度一次调用进其回收站可恢复;本地/WebDAV 逐个,目录含内容递归,永久删除) */
    public int deleteEntries(StorageDevice device, List<String> paths) {
        requireWritable(device);
        if (paths == null || paths.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "请选择要删除的文件或目录");
        }
        if ("BAIDU".equals(device.getDeviceType())) {
            baiduDelete(device, paths);
            return paths.size();
        }
        WebDavCreds c = isWebDavType(device.getDeviceType()) ? parseWebDavCreds(device) : null;
        int deleted = 0;
        for (String path : paths) {
            if (c != null) {
                WebDavClient.delete(c.serverUrl(), webdavNormalizePath(path, true), c.username(), c.password());
            } else {
                Path target = resolveSafe(deviceRoot(device), path);
                if (!Files.exists(target)) {
                    throw new BizException(ResultCode.NOT_FOUND, "文件或目录不存在: " + path);
                }
                deleteLocalRecursive(target);
            }
            deleted++;
        }
        return deleted;
    }

    /** 本地递归删除(目录先删子项);target 已经 resolveSafe 校验 */
    private void deleteLocalRecursive(Path target) throws BizException {
        try {
            if (Files.isDirectory(target)) {
                try (var walk = Files.walk(target)) {
                    walk.sorted(java.util.Comparator.reverseOrder())
                            .forEach(p -> {
                                try {
                                    Files.deleteIfExists(p);
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                }
                            });
                }
            } else {
                Files.deleteIfExists(target);
            }
        } catch (Exception e) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "删除失败: " + e.getMessage());
        }
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

    /** 简易 HTTP GET 取 JSON(百度 OAuth/xpan 端点;出站日志走 ThirdPartyHttp→thirdparty 文件) */
    private JsonNode httpGetJson(String urlStr) {
        try {
            return json.readTree(ThirdPartyHttp.get("baidu", urlStr, null, 10000).body());
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

    /**
     * 百度 API 并发限流:网格页一次打几十个中转请求,不加限制会同时打出几十个
     * filemetas+dlink 调用,触发百度限频且占满连接。只限建连阶段,数据传输不占槽。
     * ponytail: 固定 4 并发,撞限频(errno 2)再按错误率自适应
     */
    private final java.util.concurrent.Semaphore baiduSlots = new java.util.concurrent.Semaphore(4);

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
        try {
            baiduSlots.acquire();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new BizException(ResultCode.INTERNAL_ERROR, "请求被中断");
        }
        try {
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

            // 3) 打开 dlink 流(手动跟随 302 到 CDN,重放 UA 防丢失);流关闭时释放限流槽
            //    大文件流式下载不走 ThirdPartyHttp(不能整包读),手动打 thirdparty 日志
            String token = baiduAccessToken(c);
            org.slf4j.Logger tp = com.ihomy.common.Loggers.thirdParty("baidu");
            long t0 = System.currentTimeMillis();
            tp.info(">>> STREAM dlink fsId={} path={}", fid, p);
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
                tp.error("!!! STREAM dlink failed fsId={} status={} costMs={}",
                        fid, status, System.currentTimeMillis() - t0);
                throw new BizException(ResultCode.INTERNAL_ERROR, "百度网盘文件读取失败: HTTP " + status);
            }
            tp.info("<<< STREAM dlink fsId={} status={} len={} costMs={}",
                    fid, status, conn.getContentLengthLong(), System.currentTimeMillis() - t0);
            InputStream raw = conn.getInputStream();
            return new BaiduFileStream(new java.io.FilterInputStream(raw) {
                @Override
                public void close() throws java.io.IOException {
                    try {
                        super.close();
                    } finally {
                        baiduSlots.release();
                    }
                }
            }, conn.getContentLengthLong());
        } catch (BizException e) {
            baiduSlots.release();
            throw e;
        } catch (Exception e) {
            baiduSlots.release();
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

    /* ---------- WebDAV/Nextcloud 设备接入(NEXTCLOUD/WEBDAV,凭证存 root_path) ---------- */

    /** WebDAV 凭证(解密后) */
    public record WebDavCreds(String serverUrl, String username, String password) {}

    /**
     * root_path 凭证解析:格式 serverUrl|username|ENC(password),从最后一段往前取
     * (密码是 Base64 密文不含 |,username/serverUrl 位移安全)。
     */
    private WebDavCreds parseWebDavCreds(StorageDevice device) {
        String rootPath = device.getRootPath();
        int p1 = rootPath.indexOf('|');
        int p2 = rootPath.lastIndexOf('|');
        if (p1 <= 0 || p2 <= p1) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "WebDAV 设备凭证缺失,请编辑设备重新配置");
        }
        return new WebDavCreds(rootPath.substring(0, p1),
                rootPath.substring(p1 + 1, p2),
                parameterService.decrypt(rootPath.substring(p2 + 1)));
    }

    /** 组装 root_path(serverUrl|username|ENC 密码)并 PROPFIND 测连,不通过不入库;编辑密码留空沿用旧值 */
    private String buildWebDavRootPath(String deviceType, String serverUrl, String username,
                                       String password, String oldRootPath) {
        if (serverUrl == null || serverUrl.isBlank() || username == null || username.isBlank()) {
            throw new BizException(ResultCode.BAD_REQUEST, "服务器地址与账号不能为空");
        }
        String url = normalizeDavUrl(deviceType, serverUrl, username.trim());
        String encPwd;
        boolean oldLooksWebDav = oldRootPath != null && oldRootPath.indexOf('|') > 0;
        if (password != null && !password.isBlank()) {
            encPwd = parameterService.encrypt(password.trim());
        } else if (oldLooksWebDav) {
            encPwd = parameterService.decrypt(oldRootPath.substring(oldRootPath.lastIndexOf('|') + 1));
        } else {
            throw new BizException(ResultCode.BAD_REQUEST, "应用密码不能为空");
        }
        WebDavCreds c = new WebDavCreds(url, username.trim(), parameterService.decrypt(encPwd));
        WebDavClient.propfind(url, "/", c.username(), c.password(), 0); // 测连:401/404/连接失败直接抛给前端
        return url + "|" + c.username() + "|" + encPwd;
    }

    /** DAV 地址规范化:去尾斜杠;NEXTCLOUD 填站点根地址时自动拼 /remote.php/dav/files/{用户名} */
    private String normalizeDavUrl(String deviceType, String serverUrl, String username) {
        String u = serverUrl.trim();
        if (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        if (!u.startsWith("http://") && !u.startsWith("https://")) {
            throw new BizException(ResultCode.BAD_REQUEST, "服务器地址必须以 http(s):// 开头");
        }
        if ("NEXTCLOUD".equals(deviceType) && !u.contains("/remote.php/dav")) {
            u = u + "/remote.php/dav/files/" + username;
        }
        return u;
    }

    /** 浏览 WebDAV/Nextcloud 目录:PROPFIND Depth=1(目录优先按名称排序,与本地浏览一致;fsId 恒 null) */
    public List<Map<String, Object>> webdavBrowse(StorageDevice device, String path) {
        WebDavCreds c = parseWebDavCreds(device);
        String p = webdavNormalizePath(path, true);
        List<Map<String, Object>> items = new ArrayList<>();
        for (WebDavClient.DavItem it : WebDavClient.propfind(c.serverUrl(), p, c.username(), c.password(), 1)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", it.name());
            m.put("isDir", it.dir());
            m.put("size", it.dir() ? null : it.size());
            m.put("modified", it.modified());
            m.put("fsId", null);
            items.add(m);
        }
        items.sort((a, b) -> {
            boolean da = (Boolean) a.get("isDir"), db = (Boolean) b.get("isDir");
            if (da != db) return da ? -1 : 1;
            return ((String) a.get("name")).compareToIgnoreCase((String) b.get("name"));
        });
        return items;
    }

    /** 打开 WebDAV/Nextcloud 文件流:Range 原样透传(200/206),流式不缓冲 */
    public WebDavClient.WebDavStream webdavOpen(StorageDevice device, String path, String range) {
        WebDavCreds c = parseWebDavCreds(device);
        String p = webdavNormalizePath(path, false);
        return WebDavClient.open(c.serverUrl(), p, c.username(), c.password(), range);
    }

    /** WebDAV 路径规范化:与 baiduNormalizePath 同规则(正斜杠、/ 开头、禁 ..) */
    private String webdavNormalizePath(String path, boolean dir) {
        String p = path == null ? "" : path.trim().replace('\\', '/');
        if (p.contains("..")) throw new BizException(ResultCode.BAD_REQUEST, "非法路径");
        if (!p.startsWith("/")) p = "/" + p;
        if (p.endsWith("/")) p = p.substring(0, p.length() - 1);
        if (p.isEmpty()) p = "/";
        if (!dir && p.equals("/")) throw new BizException(ResultCode.BAD_REQUEST, "缺少文件名");
        return p;
    }

    /* ---------- 百度网盘写操作(POST 表单;项目首个 POST 到百度的先例) ---------- */

    /** xpan POST 表单:errno 111/-6(token 过期/失效)刷新后重试一次,其余非 0 抛业务异常 */
    private JsonNode baiduPostForm(BaiduCredential c, java.util.function.UnaryOperator<String> urlWithToken, byte[] formBody) {
        JsonNode resp = postJson(urlWithToken.apply(baiduAccessToken(c)), formBody);
        long errno = resp == null ? -1 : resp.path("errno").asLong(0);
        if (errno == 111 || errno == -6) {
            resp = postJson(urlWithToken.apply(refreshBaiduToken(c)), formBody);
            errno = resp == null ? -1 : resp.path("errno").asLong(0);
        }
        if (resp == null || errno != 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "百度网盘接口失败: errno=" + errno
                    + (resp == null ? "" : " " + resp.path("errmsg").asText("")));
        }
        return resp;
    }

    /** POST 表单取 JSON(百度 xpan 写端点;出站日志走 ThirdPartyHttp→thirdparty 文件) */
    private JsonNode postJson(String urlStr, byte[] formBody) {
        try {
            return json.readTree(ThirdPartyHttp.request("baidu", "POST", urlStr,
                    Map.of("Content-Type", "application/x-www-form-urlencoded"), formBody, 10000).body());
        } catch (Exception e) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "请求百度网盘接口失败: " + e.getMessage());
        }
    }

    /** 百度新建文件夹:xpan create(isdir=1) */
    private void baiduMkdir(StorageDevice device, String path) {
        BaiduCredential c = requireBaiduCredential(device.getFamilyId());
        String form = "path=" + URLEncoder.encode(path, StandardCharsets.UTF_8) + "&isdir=1&size=0";
        baiduPostForm(c, token -> "https://pan.baidu.com/rest/2.0/xpan/file?method=create"
                + "&access_token=" + URLEncoder.encode(token, StandardCharsets.UTF_8),
                form.getBytes(StandardCharsets.UTF_8));
    }

    /** 百度重命名:filemanager opera=rename(filelist=[{"path","newname"}]) */
    private void baiduRename(StorageDevice device, String path, String newName) {
        BaiduCredential c = requireBaiduCredential(device.getFamilyId());
        try {
            String fileList = json.writeValueAsString(List.of(Map.of("path", path, "newname", newName)));
            String form = "filelist=" + URLEncoder.encode(fileList, StandardCharsets.UTF_8);
            baiduPostForm(c, token -> "https://pan.baidu.com/rest/2.0/xpan/file?method=filemanager&opera=rename"
                    + "&access_token=" + URLEncoder.encode(token, StandardCharsets.UTF_8),
                    form.getBytes(StandardCharsets.UTF_8));
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "百度重命名失败: " + e.getMessage());
        }
    }

    /** 百度删除:filemanager opera=delete(filelist=[路径]);进百度网盘回收站,可恢复 */
    private void baiduDelete(StorageDevice device, List<String> paths) {
        BaiduCredential c = requireBaiduCredential(device.getFamilyId());
        try {
            List<String> normalized = new ArrayList<>();
            for (String p : paths) {
                normalized.add(baiduNormalizePath(p, true));
            }
            String form = "filelist=" + URLEncoder.encode(json.writeValueAsString(normalized), StandardCharsets.UTF_8);
            baiduPostForm(c, token -> "https://pan.baidu.com/rest/2.0/xpan/file?method=filemanager&opera=delete"
                    + "&access_token=" + URLEncoder.encode(token, StandardCharsets.UTF_8),
                    form.getBytes(StandardCharsets.UTF_8));
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "百度删除失败: " + e.getMessage());
        }
    }
}