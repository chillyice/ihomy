package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ihomy.common.BizException;
import com.ihomy.common.DictConst;
import com.ihomy.common.Loggers;
import com.ihomy.common.ResultCode;
import com.ihomy.common.ThirdPartyHttp;
import com.ihomy.dto.MediaProgressDTO;
import com.ihomy.dto.MediaServerDTO;
import com.ihomy.entity.MediaServer;
import com.ihomy.mapper.MediaServerMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 放映厅媒体引擎对接(Infuse 模式:刮削/转码/TV 客户端用媒体服务器,界面与家庭层 ihomy 自建)。
 *
 * 拓扑:家庭 NAS 上跑媒体服务器,ihomy 后端只调它的 REST API(小 JSON);
 * 视频播放地址由客户端直连(带播放令牌),不经本服务转发视频流量。
 * 每家庭一条配置({@link MediaServer}),账号密码存 ENC 密文,会话令牌缓存 Redis 24 小时。
 *
 * 端点均按 Jellyfin 10.9 实测校准(列表 /Items、续看 /UserItems/Resume、
 * 看过 /UserPlayedItems、观看进度 /UserItems/{id}/UserData;Emby 同名兼容)。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JellyfinService {

    /** 媒体服务器要求的客户端标识(设备标识固定为后端自身,与浏览器令牌互不影响) */
    private static final String CLIENT = "ihomy";
    private static final String DEVICE = "ihomy-server";
    private static final String DEVICE_ID = "ihomy-server";
    private static final String CLIENT_VERSION = "1.0";

    private static final String TOKEN_KEY = "ihomy:jellyfin:token:";
    private static final String STATUS_KEY = "ihomy:jellyfin:status:";
    private static final Duration TOKEN_TTL = Duration.ofHours(24);
    /** 状态探测结果的短缓存:放映厅页与设置页都会拉,避免每次开页都打一串外网调用 */
    private static final Duration STATUS_TTL = Duration.ofSeconds(60);

    private static final int API_TIMEOUT_MS = 8000;
    private static final int TEST_TIMEOUT_MS = 6000;
    private static final int IMAGE_TIMEOUT_MS = 8000;

    private static final int ITEM_LIMIT = 1000;
    private static final int RESUME_LIMIT = 12;
    private static final int IMAGE_MAX_WIDTH = 2000;
    private static final long TICKS_PER_SECOND = 10_000_000L;
    private static final long TICKS_PER_MINUTE = TICKS_PER_SECOND * 60;

    /** 列表/卡片所需字段(实测:多余字段名不会报错,缺字段值为 null) */
    private static final String CARD_FIELDS = "Genres,Studios,ProductionLocations,PremiereDate,CommunityRating,"
            + "ChildCount,RunTimeTicks,ImageTags,Overview,People,UserData";
    private static final String EPISODE_FIELDS = "Overview,RunTimeTicks,ImageTags,UserData";
    /**
     * 继续观看要比卡片多要 SeriesInfo:实测媒体服务器的续看列表默认不返回剧名与季集号
     * (分集类型下 SeriesName/IndexNumber/ParentIndexNumber 全缺),不带上就只能显示成「S01E01」。
     */
    private static final String RESUME_FIELDS = CARD_FIELDS + ",SeriesInfo";

    /** 浏览器 <video> 可直接播的编码;不在其中只作提示,不做转码回退(留后续迭代) */
    private static final Set<String> BROWSER_VIDEO = Set.of("h264", "vp8", "vp9", "av1");
    private static final Set<String> BROWSER_AUDIO = Set.of("aac", "mp3", "opus", "vorbis", "flac");

    private static final Set<String> IMAGE_TYPES =
            Set.of("Primary", "Backdrop", "Thumb", "Logo", "Banner", "Art", "Disc");

    /** 条目 id 只允许媒体服务器的 GUID 形态(拼进上游 URL 前校验,防路径穿越) */
    private static final Pattern ITEM_ID = Pattern.compile(
            "^[0-9a-fA-F]{32}$|^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private final MediaServerMapper mediaServerMapper;
    private final ParameterService parameterService;
    private final SignedUrlService signedUrlService;
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    /** 已完成认证的会话:配置 + 令牌 + 服务器上的用户 id */
    private record Ctx(MediaServer cfg, String token, String userId) {
    }

    /** 图片响应(二进制通道不适用 ThirdPartyHttp 的字符串封装) */
    public record Image(byte[] data, String contentType) {
    }

    // ==================== 配置 ====================

    /** 配置视图:密码只回 hasPassword,不回明文 */
    public Map<String, Object> getConfig(Long familyId) {
        Map<String, Object> m = new LinkedHashMap<>();
        MediaServer row = find(familyId);
        m.put("configured", row != null);
        m.put("serverType", row == null ? DictConst.MEDIA_JELLYFIN : row.getServerType());
        m.put("serverUrl", row == null ? null : row.getServerUrl());
        m.put("publicUrl", row == null ? null : row.getPublicUrl());
        m.put("username", row == null ? null : row.getUsername());
        m.put("hasPassword", row != null && row.getPassword() != null && !row.getPassword().isBlank());
        m.put("enabled", row == null || row.getEnabled() == null || row.getEnabled() == 1);
        m.put("lastConnectedAt", row == null ? null : row.getLastConnectedAt());
        return m;
    }

    /**
     * 保存配置并试连一次。密码留空保留原值;试连失败不拦保存(服务器可能只是暂时不可达),
     * 由返回值告知前端「已保存但连不上」。
     */
    public Map<String, Object> saveConfig(Long familyId, Long userId, MediaServerDTO dto) {
        String serverUrl = normalizeUrl(dto.getServerUrl(), "请填写媒体服务器地址");
        String username = dto.getUsername() == null ? "" : dto.getUsername().trim();
        if (username.isEmpty()) throw new BizException(ResultCode.BAD_REQUEST, "请填写账号");
        String publicUrl = dto.getPublicUrl() == null || dto.getPublicUrl().isBlank()
                ? null : normalizeUrl(dto.getPublicUrl(), "播放地址不正确");

        MediaServer row = find(familyId);
        boolean isNew = row == null;
        String password;
        if (isNew) {
            row = new MediaServer();
            row.setFamilyId(familyId);
            row.setCreatedBy(userId);
            password = parameterService.encrypt(requirePassword(dto));
        } else {
            password = row.getPassword();
        }
        if (dto.getPassword() != null && !dto.getPassword().isBlank()) {
            password = parameterService.encrypt(dto.getPassword().trim());
        }
        int enabled = dto.getEnabled() == null || dto.getEnabled() ? 1 : 0;

        if (isNew) {
            row.setServerType(DictConst.mediaServerType(dto.getServerType()));
            row.setServerUrl(serverUrl);
            row.setPublicUrl(publicUrl);
            row.setUsername(username);
            row.setPassword(password);
            row.setEnabled(enabled);
            mediaServerMapper.insert(row);
        } else {
            // 只 SET 业务字段:LambdaUpdateWrapper 不回写实体里的旧 updated_at(否则会抑制 ON UPDATE CURRENT_TIMESTAMP)
            LambdaUpdateWrapper<MediaServer> uw = new LambdaUpdateWrapper<>();
            uw.eq(MediaServer::getFamilyId, familyId)
              .set(MediaServer::getServerType, DictConst.mediaServerType(dto.getServerType()))
              .set(MediaServer::getServerUrl, serverUrl)
              .set(MediaServer::getPublicUrl, publicUrl)
              .set(MediaServer::getUsername, username)
              .set(MediaServer::getPassword, password)
              .set(MediaServer::getEnabled, enabled);
            mediaServerMapper.update(null, uw);
        }

        evictSession(familyId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("saved", true);
        m.put("connected", false);
        MediaServer saved = find(familyId);
        try {
            Ctx c = login(saved);
            markConnected(saved);
            m.put("connected", true);
            m.put("serverName", text(info(c), "ServerName"));
            m.put("version", text(info(c), "Version"));
        } catch (RuntimeException e) {
            log.warn("媒体引擎保存后试连失败, familyId={}, reason={}", familyId, e.getMessage());
        }
        return m;
    }

    /** 删除配置(停用引擎,不动物媒体服务器上的任何数据) */
    public void removeConfig(Long familyId) {
        mediaServerMapper.delete(new LambdaQueryWrapper<MediaServer>().eq(MediaServer::getFamilyId, familyId));
        evictSession(familyId);
    }

    /** 连通测试:未填的项回退到已保存配置,便于「先测再存」 */
    public Map<String, Object> testConnection(Long familyId, MediaServerDTO dto) {
        MediaServer row = find(familyId);
        MediaServer probe = new MediaServer();
        String url = dto.getServerUrl() == null || dto.getServerUrl().isBlank()
                ? (row == null ? null : row.getServerUrl())
                : dto.getServerUrl().trim();
        String username = dto.getUsername() == null || dto.getUsername().isBlank()
                ? (row == null ? null : row.getUsername())
                : dto.getUsername().trim();
        String password = dto.getPassword() == null || dto.getPassword().isBlank()
                ? (row == null ? null : row.getPassword())
                : parameterService.encrypt(dto.getPassword().trim());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", false);
        if (url == null || username == null || password == null) {
            m.put("message", "请先填写服务器地址、账号和密码");
            return m;
        }
        try {
            probe.setServerUrl(normalizeUrl(url, "服务器地址不正确"));
        } catch (BizException e) {
            m.put("message", e.getMessage());
            return m;
        }
        probe.setServerType(DictConst.mediaServerType(row == null ? null : row.getServerType()));
        probe.setUsername(username);
        probe.setPassword(password);
        probe.setFamilyId(familyId);
        try {
            Ctx c = login(probe);
            m.put("ok", true);
            m.put("serverName", text(info(c), "ServerName"));
            m.put("version", text(info(c), "Version"));
        } catch (BizException e) {
            m.put("message", e.getMessage());
        }
        return m;
    }

    /** 引擎状态:放映厅页与设置页共用(未配置时 configured=false,连通失败时 connected=false) */
    public Map<String, Object> status(Long familyId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("configured", false);
        m.put("connected", false);
        MediaServer row = find(familyId);
        if (row == null) return m;
        m.put("configured", true);
        m.put("enabled", ready(row));
        m.put("lastConnectedAt", row.getLastConnectedAt());
        if (!ready(row)) return m;

        String cached = redis.opsForValue().get(STATUS_KEY + familyId);
        JsonNode probe = null;
        if (cached != null) {
            try {
                probe = mapper.readTree(cached);
            } catch (Exception e) {
                probe = null;
            }
        }
        if (probe == null) {
            try {
                Ctx c = ctxWith(row);
                var node = mapper.createObjectNode();
                node.set("info", info(c));
                node.set("counts", apiJson(familyId, c, "GET", "/Items/Counts?userId={uid}", null));
                node.set("libraries", apiJson(familyId, c, "GET", "/Library/VirtualFolders", null));
                redis.opsForValue().set(STATUS_KEY + familyId, node.toString(), STATUS_TTL);
                markConnected(row);
                probe = node;
            } catch (RuntimeException e) {
                log.warn("媒体引擎状态探测失败, familyId={}, reason={}", familyId, e.getMessage());
                return m;
            }
        }
        m.put("connected", true);
        m.put("serverName", text(probe.path("info"), "ServerName"));
        m.put("version", text(probe.path("info"), "Version"));
        m.put("serverType", row.getServerType());
        m.put("libraries", libraries(probe.path("libraries")));
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("movies", intOrNull(probe.path("counts").path("MovieCount")));
        counts.put("series", intOrNull(probe.path("counts").path("SeriesCount")));
        counts.put("episodes", intOrNull(probe.path("counts").path("EpisodeCount")));
        m.put("counts", counts);
        return m;
    }

    // ==================== 内容 ====================

    /** 作品列表(海报墙):电影 + 剧集,按加入时间倒序;筛选在前端做(家庭库数据量小) */
    public List<Map<String, Object>> works(Long familyId) {
        if (familyId == null) return List.of();
        MediaServer row = find(familyId);
        if (!ready(row)) return List.of();
        JsonNode node = apiJson(familyId, "GET", "/Items?userId={uid}&recursive=true"
                + "&includeItemTypes=Movie,Series&fields=" + CARD_FIELDS
                + "&sortBy=DateCreated&sortOrder=Descending&limit=" + ITEM_LIMIT + "&imageTypeLimit=1");
        List<Map<String, Object>> list = cards(node.path("Items"));
        for (Map<String, Object> m : list) attachImage(familyId, m, 400, "Primary");
        return list;
    }

    /** 详情:电影回元数据;剧集另带分季与分集 */
    public Map<String, Object> workDetail(Long familyId, String itemId) {
        String id = requireItemId(itemId);
        JsonNode node = apiJson(familyId, "GET", "/Items/" + id + "?userId={uid}&fields=" + CARD_FIELDS);
        Map<String, Object> m = card(node);
        JsonNode people = node.path("People");
        List<String> directors = new ArrayList<>();
        List<Map<String, Object>> actors = new ArrayList<>();
        if (people.isArray()) {
            for (JsonNode p : people) {
                String type = text(p, "Type");
                if ("Director".equals(type)) {
                    directors.add(text(p, "Name"));
                } else if ("Actor".equals(type)) {
                    Map<String, Object> a = new LinkedHashMap<>();
                    a.put("name", text(p, "Name"));
                    a.put("role", text(p, "Role"));
                    actors.add(a);
                }
            }
        }
        m.put("directors", directors);
        m.put("actors", actors);
        m.put("officialRating", text(node, "OfficialRating"));
        m.put("backdropTag", first(node.path("BackdropImageTags")));
        attachImage(familyId, m, 600, "Primary");
        if (m.get("backdropTag") != null) {
            m.put("backdropUrl", signedUrlService.signMediaImage(familyId, id, "Backdrop", 1280));
        }
        if ("Series".equals(text(node, "Type"))) {
            m.put("seasons", seasons(familyId, id));
            m.put("episodes", episodes(familyId, id));
        }
        return m;
    }

    /**
     * 播放地址(现取,不缓存):static=true 直出原文件,地址带播放令牌,客户端直连媒体服务器。
     * 扩展名必须带上(实测:不带扩展名时 Content-Type 报 video/quicktime,浏览器可能拒播)。
     */
    public Map<String, Object> playUrl(Long familyId, String itemId) {
        String id = requireItemId(itemId);
        Ctx c = ctx(familyId);
        JsonNode pb = apiJson(familyId, c, "GET", "/Items/" + id + "/PlaybackInfo?userId={uid}", null);
        JsonNode source = firstNode(pb.path("MediaSources"));
        String videoCodec = null;
        String audioCodec = null;
        if (source != null && source.path("MediaStreams").isArray()) {
            for (JsonNode s : source.get("MediaStreams")) {
                if ("Video".equals(text(s, "Type")) && videoCodec == null) videoCodec = lower(text(s, "Codec"));
                if ("Audio".equals(text(s, "Type")) && audioCodec == null) audioCodec = lower(text(s, "Codec"));
            }
        }
        boolean playable = (videoCodec == null || BROWSER_VIDEO.contains(videoCodec))
                && (audioCodec == null || BROWSER_AUDIO.contains(audioCodec));
        String ext = streamExtension(source == null ? null : text(source, "Container"));
        String base = clientBase(c.cfg());
        String url = base + "/Videos/" + id + "/stream" + (ext.isEmpty() ? "" : "." + ext)
                + "?static=true&api_key=" + urlEncode(c.token())
                + "&mediaSourceId=" + urlEncode(id) + "&deviceId=" + urlEncode(DEVICE_ID);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("url", url);
        m.put("playable", playable);
        m.put("videoCodec", videoCodec);
        m.put("audioCodec", audioCodec);
        return m;
    }

    /** 标记看过/取消看过(剧集条目会连带其分集) */
    public void markPlayed(Long familyId, String itemId, boolean played) {
        String id = requireItemId(itemId);
        apiJson(familyId, played ? "POST" : "DELETE", "/UserPlayedItems/" + id);
    }

    /** 播放进度上报:只更新位置时不要带上 played,否则会把已看过的片子翻回未看 */
    public void reportProgress(Long familyId, String itemId, MediaProgressDTO dto) {
        String id = requireItemId(itemId);
        Map<String, Object> body = new LinkedHashMap<>();
        if (dto.getPositionTicks() != null) body.put("PlaybackPositionTicks", Math.max(0L, dto.getPositionTicks()));
        if (dto.getPlayed() != null) body.put("Played", dto.getPlayed());
        if (body.isEmpty()) return;
        apiJson(familyId, "POST", "/UserItems/" + id + "/UserData", body);
    }

    /** 继续观看(媒体服务器记录的进度:ihomy 网页播放与电视端 App 共用同一份) */
    public List<Map<String, Object>> resume(Long familyId) {
        if (familyId == null) return List.of();
        if (!ready(find(familyId))) return List.of();
        JsonNode node = apiJson(familyId, "GET", "/UserItems/Resume?userId={uid}&mediaTypes=Video"
                + "&limit=" + RESUME_LIMIT + "&fields=" + RESUME_FIELDS);
        List<Map<String, Object>> list = cards(node.path("Items"));
        // 「继续观看」只收没看完的:媒体服务器会把已标记看过、但位置有残留的条目也放进来
        list.removeIf(c -> Boolean.TRUE.equals(c.get("played")));
        for (Map<String, Object> m : list) attachImage(familyId, m, 400, "Primary");
        return list;
    }

    /**
     * 海报/剧照中转:签名 URL 免 JWT 供 <img> 直接引用,令牌不出后端。
     * 二进制响应不能走 ThirdPartyHttp(它按字符串封装),按流式响应的既有惯例手动打三方日志。
     */
    public Image image(Long familyId, String itemId, String type, Integer maxWidth) {
        MediaServer row = find(familyId);
        if (!ready(row)) throw new BizException(ResultCode.NOT_FOUND, "图片不存在");
        String id = requireItemId(itemId);
        String imageType = IMAGE_TYPES.contains(type) ? type : "Primary";
        int width = maxWidth == null || maxWidth <= 0 ? 400 : Math.min(maxWidth, IMAGE_MAX_WIDTH);
        Ctx c = ctxWith(row);
        String url = base(row.getServerUrl()) + "/Items/" + id + "/Images/" + imageType
                + "?maxWidth=" + width + "&quality=90&api_key=" + urlEncode(c.token());
        var tlog = Loggers.thirdParty("jellyfin");
        long start = System.currentTimeMillis();
        tlog.info(">>> GET /Items/{}/Images/{} maxWidth={}", id, imageType, width);
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(IMAGE_TIMEOUT_MS);
            conn.setReadTimeout(IMAGE_TIMEOUT_MS);
            conn.setRequestProperty("Authorization", mediaBrowserHeader(c.token()));
            int status = conn.getResponseCode();
            if (status < 200 || status >= 300) {
                tlog.warn("<<< GET Images status={} costMs={}", status, System.currentTimeMillis() - start);
                closeQuietly(conn);
                throw new BizException(ResultCode.NOT_FOUND, "图片不存在");
            }
            byte[] data;
            try (InputStream in = conn.getInputStream()) {
                data = readAll(in);
            }
            String contentType = conn.getContentType();
            tlog.info("<<< GET Images status={} bytes={} costMs={}", status, data.length,
                    System.currentTimeMillis() - start);
            return new Image(data, imageContentType(contentType));
        } catch (IOException e) {
            tlog.error("!!! GET Images failed costMs={}", System.currentTimeMillis() - start, e);
            throw new BizException(ResultCode.INTERNAL_ERROR, "图片读取失败");
        } finally {
            closeQuietly(conn);
        }
    }

    // ==================== 上游调用 ====================

    private JsonNode info(Ctx c) {
        return apiJson(c.cfg().getFamilyId(), c, "GET", "/System/Info", null);
    }

    private List<Map<String, Object>> seasons(Long familyId, String seriesId) {
        JsonNode node = apiJson(familyId, "GET", "/Shows/" + seriesId + "/Seasons?userId={uid}&fields=UserData");
        List<Map<String, Object>> list = new ArrayList<>();
        for (JsonNode s : node.path("Items")) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", text(s, "Id"));
            m.put("name", text(s, "Name"));
            m.put("seasonNumber", intOrNull(s.path("IndexNumber")));
            m.put("unplayedCount", intOrNull(s.path("UserData").path("UnplayedItemCount")));
            m.put("imageTag", text(s.path("ImageTags"), "Primary"));
            list.add(m);
        }
        return list;
    }

    private List<Map<String, Object>> episodes(Long familyId, String seriesId) {
        JsonNode node = apiJson(familyId, "GET", "/Shows/" + seriesId + "/Episodes?userId={uid}&fields=" + EPISODE_FIELDS);
        List<Map<String, Object>> list = new ArrayList<>();
        for (JsonNode e : node.path("Items")) {
            Map<String, Object> m = card(e);
            m.put("seasonName", text(e, "SeasonName"));
            m.put("seriesImageTag", text(e, "SeriesPrimaryImageTag"));
            attachImage(familyId, m, 300, "Primary");
            if (m.get("imageUrl") == null && m.get("seriesImageTag") != null) {
                // 分集自身没有剧照时退回所属剧集的海报
                String seriesItemId = text(e, "SeriesId");
                if (seriesItemId != null) {
                    m.put("imageUrl", signedUrlService.signMediaImage(familyId, seriesItemId, "Primary", 300));
                }
            }
            list.add(m);
        }
        return list;
    }

    /** 给卡片补海报签名地址(无图不留字段,前端按有无渲染占位) */
    private void attachImage(Long familyId, Map<String, Object> card, int width, String type) {
        if (card.get("imageTag") == null) return;
        String id = (String) card.get("id");
        if (id == null) return;
        card.put(type.equals("Primary") ? "imageUrl" : type.toLowerCase(Locale.ROOT) + "Url",
                signedUrlService.signMediaImage(familyId, id, type, width));
    }

    private List<Map<String, Object>> libraries(JsonNode arr) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (arr == null || !arr.isArray()) return list;
        for (JsonNode l : arr) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", text(l, "Name"));
            m.put("type", text(l, "CollectionType"));
            m.put("locations", strList(l.path("Locations")));
            list.add(m);
        }
        return list;
    }

    /** 条目 → 卡片(电影/剧集/分集通用) */
    private List<Map<String, Object>> cards(JsonNode arr) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (arr == null || !arr.isArray()) return list;
        for (JsonNode it : arr) list.add(card(it));
        return list;
    }

    private Map<String, Object> card(JsonNode it) {
        JsonNode ud = it.path("UserData");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", text(it, "Id"));
        m.put("name", text(it, "Name"));
        m.put("originalTitle", text(it, "OriginalTitle"));
        m.put("type", text(it, "Type"));
        m.put("year", intOrNull(it.path("ProductionYear")));
        m.put("premiereDate", date10(text(it, "PremiereDate")));
        m.put("rating", ud1(it.path("CommunityRating")));
        m.put("genres", strList(it.path("Genres")));
        m.put("studios", names(it.path("Studios")));
        m.put("countries", strList(it.path("ProductionLocations")));
        m.put("overview", text(it, "Overview"));
        m.put("runtimeMinutes", minutes(it.path("RunTimeTicks")));
        m.put("seasonCount", intOrNull(it.path("ChildCount")));
        m.put("seriesId", text(it, "SeriesId"));
        m.put("seriesName", text(it, "SeriesName"));
        m.put("episodeNumber", intOrNull(it.path("IndexNumber")));
        m.put("seasonNumber", intOrNull(it.path("ParentIndexNumber")));
        m.put("imageTag", text(it.path("ImageTags"), "Primary"));
        m.put("played", ud.path("Played").asBoolean(false));
        m.put("unplayedCount", intOrNull(ud.path("UnplayedItemCount")));
        m.put("positionTicks", ud.path("PlaybackPositionTicks").asLong(0));
        m.put("playedPercentage", ud1(ud.path("PlayedPercentage")));
        m.put("lastPlayedAt", text(ud, "LastPlayedDate"));
        return m;
    }

    /** 带会话的调用(返回节点) */
    private JsonNode apiJson(Long familyId, String method, String path) {
        return apiJson(familyId, method, path, null);
    }

    private JsonNode apiJson(Long familyId, String method, String path, Object body) {
        return apiJson(familyId, ctx(familyId), method, path, body);
    }

    private JsonNode apiJson(Long familyId, Ctx ctx, String method, String path, Object body) {
        ThirdPartyHttp.Resp r = raw(ctx, method, path, body);
        if (r.status() == 401) {
            // 会话失效(服务器重启/令牌被撤销):清缓存重新认证一次,仍失败则交由调用方兜底
            evictSession(familyId);
            Ctx retry = ctx(familyId);
            r = raw(retry, method, path, body);
        }
        if (!r.ok()) {
            log.warn("媒体引擎接口返回异常, familyId={}, method={}, status={}", familyId, method, r.status());
            throw new BizException(ResultCode.INTERNAL_ERROR, "媒体服务器返回异常,请稍后再试");
        }
        return parse(r.body());
    }

    private ThirdPartyHttp.Resp raw(Ctx ctx, String method, String path, Object body) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Accept", "application/json");
        headers.put("Authorization", mediaBrowserHeader(ctx.token()));
        byte[] payload = null;
        if (body != null) {
            headers.put("Content-Type", "application/json");
            payload = jsonBytes(body);
        }
        String url = base(ctx.cfg().getServerUrl()) + uidPath(ctx, path);
        try {
            return ThirdPartyHttp.request("jellyfin", method, url, headers, payload, API_TIMEOUT_MS);
        } catch (IOException e) {
            log.warn("媒体引擎请求失败, familyId={}, method={}, path={}, reason={}",
                    ctx.cfg().getFamilyId(), method, beforeQuery(path), e.getMessage());
            throw new BizException(ResultCode.INTERNAL_ERROR, "无法连接媒体服务器,请检查网络与配置");
        }
    }

    /** 路径里的 {uid} 换成服务器上的用户 id(调用方从不传入用户 id,避免越权与格式错误) */
    private String uidPath(Ctx ctx, String path) {
        return path.replace("{uid}", urlEncode(ctx.userId()));
    }

    private Ctx ctx(Long familyId) {
        return ctxWith(requireConfig(familyId));
    }

    /** 取会话:优先用 Redis 缓存的令牌,无缓存则登录并缓存 24 小时 */
    private Ctx ctxWith(MediaServer row) {
        String key = TOKEN_KEY + row.getFamilyId();
        String cached = redis.opsForValue().get(key);
        if (cached != null) {
            try {
                JsonNode n = mapper.readTree(cached);
                String token = text(n, "token");
                String userId = text(n, "userId");
                if (token != null && userId != null) return new Ctx(row, token, userId);
            } catch (Exception e) {
                log.warn("媒体引擎会话缓存解析失败, familyId={}, reason={}", row.getFamilyId(), e.getMessage());
            }
            redis.delete(key);
        }
        Ctx fresh = login(row);
        try {
            var node = mapper.createObjectNode();
            node.put("token", fresh.token());
            node.put("userId", fresh.userId());
            redis.opsForValue().set(key, node.toString(), TOKEN_TTL);
        } catch (RuntimeException e) {
            log.warn("媒体引擎会话写入缓存失败, familyId={}, reason={}", row.getFamilyId(), e.getMessage());
        }
        return fresh;
    }

    /** 登录换令牌(测试连接与正式会话共用) */
    private Ctx login(MediaServer cfg) {
        String url = base(cfg.getServerUrl()) + "/Users/AuthenticateByName";
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Accept", "application/json");
        headers.put("Content-Type", "application/json");
        headers.put("Authorization", mediaBrowserHeader(null));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("Username", cfg.getUsername());
        body.put("Pw", parameterService.decrypt(cfg.getPassword()));
        ThirdPartyHttp.Resp r;
        try {
            r = ThirdPartyHttp.request("jellyfin", "POST", url, headers, jsonBytes(body), TEST_TIMEOUT_MS);
        } catch (IOException e) {
            log.warn("媒体引擎登录请求失败, familyId={}, reason={}", cfg.getFamilyId(), e.getMessage());
            throw new BizException(ResultCode.INTERNAL_ERROR, "无法连接媒体服务器,请检查地址与网络");
        }
        if (r.status() == 401 || r.status() == 403) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "账号或密码不正确");
        }
        if (!r.ok()) {
            log.warn("媒体引擎登录失败, familyId={}, status={}", cfg.getFamilyId(), r.status());
            throw new BizException(ResultCode.INTERNAL_ERROR, "媒体服务器拒绝登录,请检查账号与地址");
        }
        JsonNode n = parse(r.body());
        String token = text(n, "AccessToken");
        String userId = text(n.path("User"), "Id");
        if (token == null || userId == null) {
            log.warn("媒体引擎登录响应缺少令牌, familyId={}, body={}", cfg.getFamilyId(), truncate(r.body()));
            throw new BizException(ResultCode.INTERNAL_ERROR, "媒体服务器返回内容不正确");
        }
        return new Ctx(cfg, token, userId);
    }

    private void evictSession(Long familyId) {
        redis.delete(TOKEN_KEY + familyId);
        redis.delete(STATUS_KEY + familyId);
    }

    /** 记录最近一次连接成功时间(探测命中的那一分钟才写一次) */
    private void markConnected(MediaServer row) {
        try {
            LambdaUpdateWrapper<MediaServer> uw = new LambdaUpdateWrapper<>();
            uw.eq(MediaServer::getFamilyId, row.getFamilyId()).set(MediaServer::getLastConnectedAt, LocalDateTime.now());
            mediaServerMapper.update(null, uw);
        } catch (RuntimeException e) {
            log.warn("媒体引擎最近连接时间写入失败, familyId={}, reason={}", row.getFamilyId(), e.getMessage());
        }
    }

    private MediaServer find(Long familyId) {
        if (familyId == null) return null;
        return mediaServerMapper.selectOne(new LambdaQueryWrapper<MediaServer>()
                .eq(MediaServer::getFamilyId, familyId).last("limit 1"));
    }

    private MediaServer requireConfig(Long familyId) {
        MediaServer row = find(familyId);
        if (row == null) throw new BizException(ResultCode.BAD_REQUEST, "尚未配置放映厅引擎");
        if (!ready(row)) throw new BizException(ResultCode.BAD_REQUEST, "放映厅引擎已停用");
        return row;
    }

    private boolean ready(MediaServer row) {
        return row != null && (row.getEnabled() == null || row.getEnabled() == 1);
    }

    private String requirePassword(MediaServerDTO dto) {
        if (dto.getPassword() == null || dto.getPassword().isBlank()) {
            throw new BizException(ResultCode.BAD_REQUEST, "请填写密码");
        }
        return dto.getPassword().trim();
    }

    /** 校验条目 id 形态(拼上游 URL 前的唯一防线,防路径穿越) */
    private String requireItemId(String itemId) {
        if (itemId == null || !ITEM_ID.matcher(itemId).matches()) {
            throw new BizException(ResultCode.BAD_REQUEST, "参数不正确");
        }
        return itemId;
    }

    /** 地址规整:补协议、去尾斜杠;只允许 http/https */
    private String normalizeUrl(String raw, String errorMessage) {
        if (raw == null || raw.isBlank()) throw new BizException(ResultCode.BAD_REQUEST, errorMessage);
        String v = raw.trim();
        String lower = v.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            // 带协议但不是 http(s) 的直接判非法,否则会补成 http://ftp://… 这种怪地址
            if (lower.contains("://")) throw new BizException(ResultCode.BAD_REQUEST, errorMessage);
            v = "http://" + v;
        }
        String bare = v.endsWith("/") ? v.substring(0, v.length() - 1) : v;
        try {
            URI uri = URI.create(bare);
            if (uri.getHost() == null || uri.getScheme() == null) throw new IllegalArgumentException("no host");
            if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
                throw new IllegalArgumentException("bad scheme");
            }
        } catch (IllegalArgumentException e) {
            throw new BizException(ResultCode.BAD_REQUEST, errorMessage);
        }
        return bare;
    }

    private String base(String serverUrl) {
        return serverUrl.endsWith("/") ? serverUrl.substring(0, serverUrl.length() - 1) : serverUrl;
    }

    /** 客户端播放/取图地址:配了直连地址就用它(后端走隧道、客户端走内网/公网) */
    private String clientBase(MediaServer cfg) {
        String url = cfg.getPublicUrl() == null || cfg.getPublicUrl().isBlank() ? cfg.getServerUrl() : cfg.getPublicUrl();
        return base(url);
    }

    /** 容器名(可能形如 "mov,mp4,m4a,...")→ 流地址扩展名,取不到则不带扩展名 */
    private String streamExtension(String container) {
        if (container == null) return "";
        for (String token : container.toLowerCase(Locale.ROOT).split(",")) {
            switch (token.trim()) {
                case "mp4", "m4v", "mov", "m4a", "3gp", "3g2", "mj2" -> {
                    return "mp4";
                }
                case "mkv", "matroska" -> {
                    return "mkv";
                }
                case "webm" -> {
                    return "webm";
                }
                case "avi" -> {
                    return "avi";
                }
                case "ts", "m2ts", "mpegts" -> {
                    return "ts";
                }
                case "flv" -> {
                    return "flv";
                }
                case "wmv", "asf" -> {
                    return "wmv";
                }
                case "ogv", "ogg" -> {
                    return "ogv";
                }
                default -> {
                    // 继续看下一个容器标识(容器串是逗号列表)
                }
            }
        }
        return "";
    }

    private String mediaBrowserHeader(String token) {
        StringBuilder sb = new StringBuilder("MediaBrowser Client=\"").append(CLIENT)
                .append("\", Device=\"").append(DEVICE)
                .append("\", DeviceId=\"").append(DEVICE_ID)
                .append("\", Version=\"").append(CLIENT_VERSION).append('"');
        if (token != null) sb.append(", Token=\"").append(token).append('"');
        return sb.toString();
    }

    private byte[] jsonBytes(Object body) {
        try {
            return mapper.writeValueAsBytes(body);
        } catch (IOException e) {
            throw new IllegalStateException("JSON 序列化失败", e);
        }
    }

    private JsonNode parse(String body) {
        if (body == null || body.isBlank()) return mapper.createObjectNode();
        try {
            return mapper.readTree(body);
        } catch (IOException e) {
            log.warn("媒体引擎响应解析失败, reason={}, body={}", e.getMessage(), truncate(body));
            throw new BizException(ResultCode.INTERNAL_ERROR, "媒体服务器返回内容不正确");
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node == null ? null : node.get(field);
        if (v == null || v.isNull() || v.isMissingNode()) return null;
        String s = v.asText();
        return s == null || s.isEmpty() ? null : s;
    }

    private static Integer intOrNull(JsonNode v) {
        return v == null || !v.isNumber() ? null : v.asInt();
    }

    private static Double ud1(JsonNode v) {
        return v == null || !v.isNumber() ? null : v.asDouble();
    }

    private static Integer minutes(JsonNode ticks) {
        if (ticks == null || !ticks.isNumber() || ticks.asLong() <= 0) return null;
        return (int) Math.round(ticks.asLong() / (double) TICKS_PER_MINUTE);
    }

    private static String date10(String iso) {
        return iso == null || iso.length() < 10 ? null : iso.substring(0, 10);
    }

    private static String first(JsonNode arr) {
        return arr != null && arr.isArray() && !arr.isEmpty() ? arr.get(0).asText() : null;
    }

    private static JsonNode firstNode(JsonNode arr) {
        return arr != null && arr.isArray() && !arr.isEmpty() ? arr.get(0) : null;
    }

    private static List<String> strList(JsonNode arr) {
        List<String> list = new ArrayList<>();
        if (arr != null && arr.isArray()) {
            for (JsonNode n : arr) list.add(n.asText());
        }
        return list;
    }

    /** Studios 是对象数组,取 Name */
    private static List<String> names(JsonNode arr) {
        List<String> list = new ArrayList<>();
        if (arr != null && arr.isArray()) {
            for (JsonNode n : arr) {
                String name = text(n, "Name");
                if (name != null) list.add(name);
            }
        }
        return list;
    }

    private static String lower(String s) {
        return s == null ? null : s.toLowerCase(Locale.ROOT);
    }

    /** 上游 Content-Type 只信图片类,其余一律按 image/jpeg(该端点免登录,不透传任意类型) */
    private static String imageContentType(String raw) {
        if (raw != null) {
            String base = raw.split(";")[0].trim().toLowerCase(Locale.ROOT);
            if (base.startsWith("image/")) return base;
        }
        return "image/jpeg";
    }

    private static String urlEncode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String beforeQuery(String url) {
        int q = url.indexOf('?');
        return q < 0 ? url : url.substring(0, q);
    }

    private static String truncate(String s) {
        return s == null || s.length() <= 512 ? s : s.substring(0, 512) + "...";
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(16 * 1024);
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return out.toByteArray();
    }

    private static void closeQuietly(HttpURLConnection conn) {
        if (conn != null) conn.disconnect();
    }
}
