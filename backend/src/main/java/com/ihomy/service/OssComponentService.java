package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.ihomy.common.AiConst;
import com.ihomy.common.BizException;
import com.ihomy.common.DictConst;
import com.ihomy.common.OssUpgradeGuide;
import com.ihomy.common.OssVersionUtil;
import com.ihomy.common.ResultCode;
import com.ihomy.common.ThirdPartyHttp;
import com.ihomy.entity.MediaServer;
import com.ihomy.entity.OssComponent;
import com.ihomy.entity.StorageDevice;
import com.ihomy.mapper.MediaServerMapper;
import com.ihomy.mapper.OssComponentMapper;
import com.ihomy.mapper.StorageDeviceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.IOException;
import java.io.StringReader;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 开源组件台账服务:登记 ihomy 集成的开源软件(直接依赖 + 独立服务),
 * 并发拉取 npm registry / Maven Central / GitHub Releases 检测最新版,
 * 版本归类纯规则(无 AI),升级仅提示 + 生成方案(不改源码)。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OssComponentService {

    private final OssComponentMapper mapper;
    private final StorageDeviceMapper storageDeviceMapper;
    private final MediaServerMapper mediaServerMapper;
    private final ParameterService parameterService;
    private final AiService aiService;
    private final ObjectMapper json = new ObjectMapper();

    private static final int TIMEOUT_MS = 5000;
    /** Maven Central 官方仓库根:按 group(点换路径)/artifact 取 maven-metadata.xml */
    private static final String MAVEN_METADATA_BASE = "https://repo1.maven.org/maven2/";
    /** 自动升级通道探测超时:比常规请求短,避免打开升级方案时被 GitHub 拖住 */
    private static final int PROBE_TIMEOUT_MS = 2500;
    /** 「仓库是否已接入 Renovate」结果缓存 5 分钟:同一仓库的台账共用,不必每次开弹窗都打 GitHub */
    private static final long DASHBOARD_CACHE_MS = 5 * 60 * 1000L;
    private static final int CHECK_POOL = 8;
    /** 触发 Renovate 的 GitHub 仓库(owner/repo),可被 sys_parameter oss.github.repo 覆盖 */
    private static final String DEFAULT_GITHUB_REPO = "chillyice/ihomy";
    private final AtomicBoolean checking = new AtomicBoolean(false);
    /** Dependency Dashboard 探测缓存(含 null 结果:仓库没装 Renovate 时也缓存,别反复超时等待) */
    private volatile String dashboardRepo;
    private volatile Dashboard dashboardCache;
    private volatile long dashboardCheckedAt;

    /** 台账列表:可升级项排前(MAJOR > MINOR > PATCH),其余按名称 */
    public List<OssComponent> list() {
        List<OssComponent> all = mapper.selectList(null);
        all.forEach(this::normalizeUpdateType);
        all.sort((a, b) -> {
            int ra = updatableRank(a);
            int rb = updatableRank(b);
            if (ra != rb) {
                return Integer.compare(ra, rb);
            }
            String na = a.getName() == null ? "" : a.getName();
            String nb = b.getName() == null ? "" : b.getName();
            return na.compareTo(nb);
        });
        return maskAll(all);
    }

    /** 汇总:总数 / 可升级数 / 最近检测时间(供导航角标) */
    public Map<String, Object> summary() {
        List<OssComponent> all = mapper.selectList(null);
        all.forEach(this::normalizeUpdateType);
        long updatable = all.stream().filter(this::isUpdatable).count();
        LocalDateTime last = all.stream()
                .map(OssComponent::getLastCheckedAt)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", all.size());
        m.put("updatable", updatable);
        m.put("lastCheckedAt", last);
        return m;
    }

    /** 立即检查:并发拉取最新版并写库;已在进行中则直接返回当前列表 */
    public List<OssComponent> checkNow() {
        if (!checking.compareAndSet(false, true)) {
            return list();
        }
        try {
            List<OssComponent> rows = mapper.selectList(new LambdaQueryWrapper<OssComponent>()
                    .eq(OssComponent::getStatus, DictConst.OSS_ACTIVE));
            if (!rows.isEmpty()) {
                ExecutorService pool = Executors.newFixedThreadPool(Math.min(CHECK_POOL, Math.max(1, rows.size())));
                try {
                    List<CompletableFuture<Void>> futures = rows.stream()
                            .map(c -> CompletableFuture.runAsync(() -> checkOne(c), pool))
                            .toList();
                    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                } finally {
                    pool.shutdown();
                }
            }
            return list();
        } finally {
            checking.set(false);
        }
    }

    /** 生成升级方案(按组件类型确定性输出可执行步骤,无 AI;同时回带上次 AI 评估供弹窗直接展示) */
    public Map<String, Object> buildUpgradePlan(Long id) {
        OssComponent c = mapper.selectById(id);
        if (c == null) {
            throw new BizException(ResultCode.NOT_FOUND, "组件不存在");
        }
        if (c.getLatestVersion() == null || c.getLatestVersion().isBlank()) {
            throw new BizException(ResultCode.BAD_REQUEST, "尚未检测到最新版本,请先执行检查");
        }
        normalizeUpdateType(c);
        if (!isUpdatable(c)) {
            throw new BizException(ResultCode.BAD_REQUEST, "当前已是最新版本,无需升级");
        }
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("id", c.getId());
        plan.put("name", c.getName());
        plan.put("componentType", c.getComponentType());
        plan.put("currentVersion", c.getCurrentVersion());
        plan.put("latestVersion", c.getLatestVersion());
        plan.put("updateType", c.getUpdateType());
        plan.put("repoUrl", c.getRepoUrl());
        plan.put("steps", OssUpgradeGuide.steps(c.getComponentType(), c.getPackageRef(),
                c.getLatestVersion(), c.getDeployType(), hasProbe(c), c.getUpdateType()));
        plan.put("autoUpgrade", autoUpgradeInfo(c));
        plan.put("lastAssess", readAssess(c.getAssessJson()));
        plan.put("assessedAt", c.getAssessedAt());
        return plan;
    }

    /**
     * 自动升级通道是否可用:仓库已接入 Renovate(Dependency Dashboard 里还有这个组件未勾选的条目)才显示「生成升级 PR」。
     * 仓库没接入时前端只给手工升级路径,不再让用户点了才吃一条 500。
     */
    private Map<String, Object> autoUpgradeInfo(OssComponent c) {
        boolean managed = DictConst.OSS_MANAGED_RENOVATE.equals(c.getManagedBy());
        String repo = githubRepo();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("managed", managed);
        m.put("repo", repo);
        m.put("available", managed && isUpdatable(c) && dashboardHasPending(dashboardFor(repo), c));
        return m;
    }

    /** 该组件的自动升级项是否还在 Dashboard 里等着勾选 */
    private boolean dashboardHasPending(Dashboard d, OssComponent c) {
        if (d == null || d.body() == null) {
            return false;
        }
        String match = componentDashboardToken(c);
        if (match.isBlank()) {
            return false;
        }
        for (String line : d.body().split("\n", -1)) {
            if (line.contains(match) && line.contains("[ ]")) {
                return true;
            }
        }
        return false;
    }

    /** 取仓库的 Dependency Dashboard(按仓库缓存 5 分钟,含「没有」这个结论) */
    private Dashboard dashboardFor(String repo) {
        long now = System.currentTimeMillis();
        if (repo.equals(dashboardRepo) && now - dashboardCheckedAt < DASHBOARD_CACHE_MS) {
            return dashboardCache;
        }
        Dashboard d = fetchDashboard(repo);
        dashboardCache = d;
        dashboardRepo = repo;
        dashboardCheckedAt = now;
        return d;
    }

    /** 去 issue 列表里找标题为 Dependency Dashboard 的那条;仓库没装 Renovate 或接口失败返回 null */
    private Dashboard fetchDashboard(String repo) {
        try {
            Map<String, String> headers = null;
            String token = githubToken();
            if (token != null) {
                headers = Map.of("Authorization", "Bearer " + token);
            }
            ThirdPartyHttp.Resp list = ThirdPartyHttp.get("github",
                    "https://api.github.com/repos/" + repo + "/issues?state=open&per_page=100",
                    headers, PROBE_TIMEOUT_MS);
            if (!list.ok()) {
                return null;
            }
            for (JsonNode issue : json.readTree(list.body())) {
                if ("Dependency Dashboard".equals(issue.path("title").asText())) {
                    return new Dashboard(issue.path("number").asLong(), issue.path("body").asText(""));
                }
            }
            return null;
        } catch (Exception e) {
            log.warn("fetch renovate dashboard failed, repo={}", repo, e);
            return null;
        }
    }

    /** Dependency Dashboard:issue 号 + 正文(勾选项所在) */
    private record Dashboard(Long number, String body) {
    }

    /** 读上次 AI 评估结果(JSON 解析失败或未评估返回 null,不阻断方案生成) */
    private Map<String, Object> readAssess(String assessJson) {
        if (assessJson == null || assessJson.isBlank()) {
            return null;
        }
        try {
            return json.readValue(assessJson, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            log.warn("parse oss assess json failed", e);
            return null;
        }
    }

    /** AI 升级评估:拉 release notes + 台账字段,LLM 出风险分级/迁移点/可行性,并回写台账 */
    public Map<String, Object> assess(Long id, Long familyId) {
        OssComponent c = mapper.selectById(id);
        if (c == null) {
            throw new BizException(ResultCode.NOT_FOUND, "组件不存在");
        }
        if (c.getLatestVersion() == null || c.getLatestVersion().isBlank()) {
            throw new BizException(ResultCode.BAD_REQUEST, "尚未检测到最新版本,请先执行检查");
        }
        String notes = fetchReleaseNotes(c);
        String userContent = "组件: " + nvl(c.getName()) + "\n"
                + "类型: " + nvl(c.getComponentType()) + "\n"
                + "当前版本: " + nvl(c.getCurrentVersion()) + "\n"
                + "目标版本: " + nvl(c.getLatestVersion()) + "\n"
                + "用途: " + nvl(c.getPurpose()) + "\n"
                + "许可证: " + nvl(c.getLicense()) + "\n"
                + "仓库: " + nvl(c.getRepoUrl()) + "\n"
                + (notes.isBlank() ? "" : "官方变更说明(release notes/changelog,截断):\n" + notes);
        String systemPrompt = "你是资深软件升级评审助手。请评估把一个开源组件从当前版本升级到目标版本的风险与影响。"
                + "仅基于给出的信息,不臆造不存在的变更。只输出 JSON,字段:"
                + " riskLevel(字符串,取值 LOW/MEDIUM/HIGH)、"
                + " feasible(布尔,是否建议升级)、"
                + " summary(字符串,一句话结论)、"
                + " breakingChanges(字符串数组,可能的破坏性变更或需关注的迁移点)、"
                + " migrationSteps(字符串数组,建议的迁移/验证步骤)。";
        JsonNode r;
        try {
            r = aiService.chatJson(familyId, AiConst.FEATURE_OSS_UPGRADE_EVAL, systemPrompt, userContent);
        } catch (BizException e) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "AI 评估不可用:" + e.getMessage() + "(需在「设置 → 家庭 AI 配置」里为「升级评估」绑定一个语言模型)");
        }
        Map<String, Object> assess = new LinkedHashMap<>();
        assess.put("componentId", id);
        assess.put("name", c.getName());
        assess.put("currentVersion", c.getCurrentVersion());
        assess.put("latestVersion", c.getLatestVersion());
        assess.put("riskLevel", r.path("riskLevel").asText("UNKNOWN").toUpperCase());
        assess.put("feasible", r.path("feasible").asBoolean(false));
        assess.put("summary", r.path("summary").asText(""));
        assess.put("breakingChanges", stringArray(r.path("breakingChanges")));
        assess.put("migrationSteps", stringArray(r.path("migrationSteps")));
        assess.put("disclaimer", "评估由 AI 生成,仅供参考,以官方 changelog 为准");
        try {
            mapper.update(null, new LambdaUpdateWrapper<OssComponent>()
                    .eq(OssComponent::getId, id)
                    .set(OssComponent::getAssessJson, json.writeValueAsString(assess))
                    .set(OssComponent::getAssessedAt, LocalDateTime.now()));
        } catch (Exception e) {
            log.warn("persist oss assess failed, id={}", id, e);
        }
        return assess;
    }

    /** 生成升级 PR 闸门:仅 RENOVATE 管理 + 可升级 + AI 评估可行时,勾选 Renovate Dependency Dashboard 触发 */
    public Map<String, Object> requestUpgrade(Long id) {
        OssComponent c = mapper.selectById(id);
        if (c == null) {
            throw new BizException(ResultCode.NOT_FOUND, "组件不存在");
        }
        if (!DictConst.OSS_MANAGED_RENOVATE.equals(c.getManagedBy())) {
            throw new BizException(ResultCode.BAD_REQUEST, "该组件非 Renovate 管理(SERVICE 请在其自身界面升级)");
        }
        if (!isUpdatable(c)) {
            throw new BizException(ResultCode.BAD_REQUEST, "该组件无可升级版本");
        }
        if (!isAssessFeasible(c.getAssessJson())) {
            throw new BizException(ResultCode.BAD_REQUEST, "请先 AI 评估且评估结论为可行,再生成升级 PR");
        }
        String repo = githubRepo();
        // 先探一次再动手:仓库没接入 Renovate / 该组件的条目不在 Dashboard 里时,给可执行的人工路径而不是 500
        if (!dashboardHasPending(dashboardFor(repo), c) || !approveRenovateDashboard(repo, c)) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "该仓库暂无待办的自动升级任务:请按「升级方案」里的步骤手工升级,或在仓库接入 Renovate 后再试");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("component", c.getName());
        out.put("repo", repo);
        out.put("message", "已标记升级,待 Renovate 下一轮生成 PR");
        return out;
    }

    public void edit(Long id, OssComponent body) {
        OssComponent old = mapper.selectById(id);
        if (old == null) {
            throw new BizException(ResultCode.NOT_FOUND, "组件不存在");
        }
        mapper.update(null, new LambdaUpdateWrapper<OssComponent>()
                .eq(OssComponent::getId, id)
                .set(OssComponent::getName, body.getName())
                .set(OssComponent::getComponentType, body.getComponentType())
                .set(OssComponent::getPackageRef, body.getPackageRef())
                .set(OssComponent::getCurrentVersion, body.getCurrentVersion())
                .set(OssComponent::getLicense, body.getLicense())
                .set(OssComponent::getRepoUrl, body.getRepoUrl())
                .set(OssComponent::getPurpose, body.getPurpose())
                .set(OssComponent::getIntegrationStatus, body.getIntegrationStatus())
                .set(OssComponent::getManagedBy, resolveManagedBy(body))
                .set(OssComponent::getDeployType,
                        DictConst.OSS_TYPE_SERVICE.equals(body.getComponentType()) ? body.getDeployType() : null)
                .set(OssComponent::getProbeType,
                        DictConst.OSS_TYPE_SERVICE.equals(body.getComponentType()) ? blankToNull(body.getProbeType()) : null)
                .set(OssComponent::getProbeUrl,
                        DictConst.OSS_TYPE_SERVICE.equals(body.getComponentType()) ? blankToNull(body.getProbeUrl()) : null)
                .set(OssComponent::getProbeToken, resolveProbeToken(body, old))
                .set(OssComponent::getRemark, body.getRemark()));
    }

    public void add(OssComponent body) {
        body.setId(null);
        body.setLatestVersion(null);
        body.setUpdateType(DictConst.OSS_UPDATE_NONE);
        body.setStatus(DictConst.OSS_ACTIVE);
        if (body.getIntegrationStatus() == null) {
            body.setIntegrationStatus(DictConst.OSS_INTEG_FULL);
        }
        if (body.getManagedBy() == null || body.getManagedBy().isBlank()) {
            body.setManagedBy(resolveManagedBy(body.getComponentType()));
        }
        if (!DictConst.OSS_TYPE_SERVICE.equals(body.getComponentType())) {
            body.setDeployType(null);
            body.setProbeType(null);
            body.setProbeUrl(null);
            body.setProbeToken(null);
        } else {
            body.setProbeUrl(blankToNull(body.getProbeUrl()));
            body.setProbeToken(encryptToken(body.getProbeToken()));
        }
        mapper.insert(body);
    }

    public void confirmUpgraded(Long id) {
        OssComponent c = mapper.selectById(id);
        if (c == null) {
            throw new BizException(ResultCode.NOT_FOUND, "组件不存在");
        }
        if (c.getLatestVersion() == null || c.getLatestVersion().isBlank()) {
            throw new BizException(ResultCode.BAD_REQUEST, "尚未检测到最新版本");
        }
        if (!isNewer(c.getLatestVersion(), c.getCurrentVersion())) {
            throw new BizException(ResultCode.BAD_REQUEST, "最新版不高于当前版本,无需确认升级");
        }
        mapper.update(null, new LambdaUpdateWrapper<OssComponent>()
                .eq(OssComponent::getId, id)
                .set(OssComponent::getCurrentVersion, c.getLatestVersion())
                .set(OssComponent::getUpdateType, DictConst.OSS_UPDATE_NONE));
    }

    public void ignore(Long id, boolean ignored) {
        mapper.update(null, new LambdaUpdateWrapper<OssComponent>()
                .eq(OssComponent::getId, id)
                .set(OssComponent::getStatus, ignored ? DictConst.OSS_IGNORED : DictConst.OSS_ACTIVE));
    }

    /** 每日 03:30 自动检测;sys_parameter oss.check.enabled=false 可关闭 */
    @Scheduled(cron = "0 30 3 * * *")
    public void scheduledCheck() {
        String enabled = parameterService.getString("oss.check.enabled");
        if (enabled != null && !"true".equalsIgnoreCase(enabled)) {
            return;
        }
        checkNow();
    }

    // ---------- 内部 ----------

    private String resolveManagedBy(String componentType) {
        return DictConst.OSS_TYPE_SERVICE.equals(componentType)
                ? DictConst.OSS_MANAGED_INTERNAL : DictConst.OSS_MANAGED_RENOVATE;
    }

    /** edit 时管理方:显式给出则保留,否则按类型推导 */
    private String resolveManagedBy(OssComponent body) {
        if (body.getManagedBy() != null && !body.getManagedBy().isBlank()) {
            return body.getManagedBy();
        }
        return resolveManagedBy(body.getComponentType());
    }

    /** 探测令牌:填了新的就加密存,留空沿用旧值(与存储设备密码同一惯例);非独立服务一律清空 */
    private String resolveProbeToken(OssComponent body, OssComponent old) {
        if (!DictConst.OSS_TYPE_SERVICE.equals(body.getComponentType())) {
            return null;
        }
        String t = body.getProbeToken();
        if (t != null && !t.isBlank()) {
            return parameterService.encrypt(t.trim());
        }
        return old == null ? null : old.getProbeToken();
    }

    private String encryptToken(String raw) {
        return raw == null || raw.isBlank() ? null : parameterService.encrypt(raw.trim());
    }

    private String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private List<OssComponent> maskAll(List<OssComponent> rows) {
        rows.forEach(this::mask);
        return rows;
    }

    private boolean isAssessFeasible(String assessJson) {
        if (assessJson == null || assessJson.isBlank()) {
            return false;
        }
        try {
            return json.readTree(assessJson).path("feasible").asBoolean(false);
        } catch (Exception e) {
            return false;
        }
    }

    private String fetchReleaseNotes(OssComponent c) {
        String repo = extractGithubRepo(c);
        if (repo == null) {
            return "";
        }
        try {
            Map<String, String> headers = null;
            String token = githubToken();
            if (token != null) {
                headers = Map.of("Authorization", "Bearer " + token);
            }
            ThirdPartyHttp.Resp r = ThirdPartyHttp.get("github",
                    "https://api.github.com/repos/" + repo + "/releases/latest", headers, TIMEOUT_MS);
            if (r.ok()) {
                String body = json.readTree(r.body()).path("body").asText("");
                if (body.isBlank()) {
                    return "";
                }
                return body.length() > 4000 ? body.substring(0, 4000) : body;
            }
        } catch (Exception e) {
            log.warn("fetch release notes failed, name={}", c.getName(), e);
        }
        return "";
    }

    /** SERVICE 的 packageRef 直接是 owner/repo;NPM/MAVEN 从 repoUrl 提取 github.com/owner/repo */
    private String extractGithubRepo(OssComponent c) {
        if (DictConst.OSS_TYPE_SERVICE.equals(c.getComponentType()) && c.getPackageRef() != null) {
            String ref = c.getPackageRef().trim();
            if (ref.matches("[\\w.-]+/[\\w.-]+")) {
                return ref;
            }
        }
        String url = c.getRepoUrl();
        if (url == null) {
            return null;
        }
        Matcher m = Pattern.compile("github\\.com/([\\w.-]+/[\\w.-]+)").matcher(url);
        if (!m.find()) {
            return null;
        }
        return m.group(1).replaceAll("\\.git$", "").replaceAll("/$", "");
    }

    private List<String> stringArray(JsonNode node) {
        List<String> out = new ArrayList<>();
        if (node != null && node.isArray()) {
            node.forEach(n -> {
                if (n.isTextual()) {
                    out.add(n.asText());
                }
            });
        }
        return out;
    }

    private String nvl(String s) {
        return s == null ? "" : s;
    }

    /** 触发 Renovate 用的 GitHub 仓库(owner/repo):sys_parameter oss.github.repo 覆盖,缺省仓库常量 */
    private String githubRepo() {
        String repo = parameterService.getString("oss.github.repo");
        if (repo != null && !repo.isBlank()) {
            return repo.trim();
        }
        return DEFAULT_GITHUB_REPO;
    }

    /** 在 Dependency Dashboard issue 里勾选该组件对应项,触发 Renovate 下一轮开 PR(尽力而为,格式随 Renovate 版本变化) */
    private boolean approveRenovateDashboard(String repo, OssComponent c) {
        try {
            Dashboard d = dashboardFor(repo);
            if (d == null || d.body() == null) {
                return false;
            }
            String match = componentDashboardToken(c);
            StringBuilder sb = new StringBuilder();
            boolean changed = false;
            for (String line : d.body().split("\n", -1)) {
                if (!changed && line.contains(match) && line.contains("[ ]")) {
                    sb.append(line.replaceFirst("\\[ \\]", "[x]")).append('\n');
                    changed = true;
                } else {
                    sb.append(line).append('\n');
                }
            }
            if (!changed) {
                return false;
            }
            Map<String, String> patchHeaders = new LinkedHashMap<>();
            String token = githubToken();
            if (token != null) {
                patchHeaders.put("Authorization", "Bearer " + token);
            }
            patchHeaders.put("Content-Type", "application/json");
            Map<String, Object> patch = Map.of("body", sb.toString());
            ThirdPartyHttp.Resp resp = ThirdPartyHttp.request("github", "PATCH",
                    "https://api.github.com/repos/" + repo + "/issues/" + d.number(),
                    patchHeaders, json.writeValueAsBytes(patch), TIMEOUT_MS);
            // Dashboard 正文已变:清掉缓存,下一次探测重新拉
            dashboardCheckedAt = 0;
            return resp.ok();
        } catch (Exception e) {
            log.warn("approve renovate dashboard failed, name={}", c.getName(), e);
            return false;
        }
    }

    /** 匹配 Dashboard 条目的令牌:NPM 用包名,MAVEN 用 artifactId(去掉 groupId) */
    private String componentDashboardToken(OssComponent c) {
        String ref = c.getPackageRef() == null ? "" : c.getPackageRef();
        if (DictConst.OSS_TYPE_MAVEN.equals(c.getComponentType())) {
            int idx = ref.lastIndexOf(':');
            return idx >= 0 ? ref.substring(idx + 1) : ref;
        }
        return ref;
    }

    private boolean isUpdatable(OssComponent c) {
        return DictConst.OSS_ACTIVE.equals(c.getStatus())
                && c.getUpdateType() != null
                && !DictConst.OSS_UPDATE_NONE.equals(c.getUpdateType())
                && isNewer(c.getLatestVersion(), c.getCurrentVersion());
    }

    /**
     * 列表/汇总统一按「当前版本 vs 最新版」重新判型:历史检测结果可能把低于当前版的版本标成可升级
     * (台账显示与操作入口共用这一份结论,不只看库里存的 update_type)。
     */
    private void normalizeUpdateType(OssComponent c) {
        if (c.getLatestVersion() != null && !c.getLatestVersion().isBlank()) {
            c.setUpdateType(OssVersionUtil.updateType(c.getCurrentVersion(), c.getLatestVersion()));
        }
    }

    /**
     * 只有 latest 严格高于 current 才算升级项:台账里可能残留「最新版低于当前版」的历史检测结果
     * (Maven Central 的 latestVersion 是最后发布而非最高版),否则「确认已升级」会把版本降回去。
     * 版本不可比较(如 SHA / 日期型标签)时沿用已存的 updateType 结论。
     */
    private boolean isNewer(String latest, String current) {
        Integer cmp = OssVersionUtil.compare(latest, current);
        return cmp == null || cmp > 0;
    }

    private int updatableRank(OssComponent c) {
        if (!isUpdatable(c)) {
            return 9;
        }
        return switch (c.getUpdateType()) {
            case DictConst.OSS_UPDATE_MAJOR -> 0;
            case DictConst.OSS_UPDATE_MINOR -> 1;
            default -> 2;
        };
    }

    private void checkOne(OssComponent c) {
        try {
            // 独立服务先探测当前运行版本(回写 current_version),再拿它跟最新版比
            if (hasProbe(c)) {
                probeAndPersist(c);
                OssComponent probed = mapper.selectById(c.getId());
                if (probed != null) {
                    c = probed;
                }
            }
            String latest = fetchLatest(c);
            LambdaUpdateWrapper<OssComponent> w = new LambdaUpdateWrapper<OssComponent>()
                    .eq(OssComponent::getId, c.getId())
                    .set(OssComponent::getLastCheckedAt, LocalDateTime.now());
            if (latest != null) {
                w.set(OssComponent::getLatestVersion, latest)
                        .set(OssComponent::getUpdateType, OssVersionUtil.updateType(c.getCurrentVersion(), latest));
            }
            // 拉取失败:仅刷新检测时间,保留上次结果
            mapper.update(null, w);
        } catch (Exception e) {
            log.warn("oss check failed, name={}, ref={}", c.getName(), c.getPackageRef(), e);
        }
    }

    private String fetchLatest(OssComponent c) throws IOException {
        String type = c.getComponentType() == null ? "" : c.getComponentType();
        if (DictConst.OSS_TYPE_NPM.equals(type)) {
            ThirdPartyHttp.Resp r = ThirdPartyHttp.get("npm",
                    "https://registry.npmjs.org/" + c.getPackageRef() + "/latest", null, TIMEOUT_MS);
            if (!r.ok() || r.body() == null || r.body().isBlank()) {
                return null;
            }
            String latest = json.readTree(r.body()).path("version").asText(null);
            // dist-tag latest 可能指向 -fix/-rc 发行:不作为升级目标,保留上次检测结果
            return OssVersionUtil.isPrerelease(latest) ? null : latest;
        }
        if (DictConst.OSS_TYPE_MAVEN.equals(type)) {
            return fetchMavenLatest(c.getPackageRef());
        }
        if (DictConst.OSS_TYPE_SERVICE.equals(type)) {
            return fetchGithubLatest(c.getPackageRef());
        }
        return null;
    }

    /**
     * Maven Central 官方元数据(repo1.maven.org):search.maven.org 的 solr 索引陈旧,会漏报新版本。
     * 选版先看 &lt;release&gt;(避开 3.5.18 与 3.5.18.1 的歧义),它是预发布时回退版本列表取最高稳定版。
     */
    private String fetchMavenLatest(String ref) throws IOException {
        String[] ga = ref.split(":", 2);
        if (ga.length < 2) {
            return null;
        }
        String url = MAVEN_METADATA_BASE + ga[0].replace('.', '/') + "/" + ga[1] + "/maven-metadata.xml";
        ThirdPartyHttp.Resp r = ThirdPartyHttp.get("maven", url, null, TIMEOUT_MS);
        if (!r.ok() || r.body() == null || r.body().isBlank()) {
            return null;
        }
        try {
            Document doc = parseMavenMetadata(r.body());
            String release = firstText(doc, "release");
            if (release != null && !OssVersionUtil.isPrerelease(release) && !OssVersionUtil.parse(release).isEmpty()) {
                return release;
            }
            return OssVersionUtil.pickLatestStable(allTexts(doc, "version"));
        } catch (Exception e) {
            log.warn("parse maven metadata failed, ref={}", ref, e);
            return null;
        }
    }

    /** 解析 maven-metadata.xml:关闭 DTD 与外部实体,只读本地 XML 文本 */
    private Document parseMavenMetadata(String xml) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        f.setFeature("http://xml.org/sax/features/external-general-entities", false);
        f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        f.setXIncludeAware(false);
        f.setExpandEntityReferences(false);
        return f.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }

    private String firstText(Document doc, String tag) {
        NodeList nodes = doc.getElementsByTagName(tag);
        return nodes.getLength() == 0 ? null : nodes.item(0).getTextContent().trim();
    }

    private List<String> allTexts(Document doc, String tag) {
        NodeList nodes = doc.getElementsByTagName(tag);
        List<String> out = new ArrayList<>(nodes.getLength());
        for (int i = 0; i < nodes.getLength(); i++) {
            out.add(nodes.item(i).getTextContent().trim());
        }
        return out;
    }

    private String fetchGithubLatest(String repo) throws IOException {
        Map<String, String> headers = null;
        String token = githubToken();
        if (token != null) {
            headers = Map.of("Authorization", "Bearer " + token);
        }
        ThirdPartyHttp.Resp r = ThirdPartyHttp.get("github",
                "https://api.github.com/repos/" + repo + "/releases/latest", headers, TIMEOUT_MS);
        if (r.ok()) {
            String tag = json.readTree(r.body()).path("tag_name").asText(null);
            if (tag != null && !tag.isBlank() && !OssVersionUtil.isPrerelease(tag)) {
                return tag;
            }
        }
        // 403 限流 / 404(无 latest release)/ latest 是预发布:回退 tags 列表取最高稳定版
        ThirdPartyHttp.Resp t = ThirdPartyHttp.get("github",
                "https://api.github.com/repos/" + repo + "/tags", headers, TIMEOUT_MS);
        if (t.ok()) {
            JsonNode arr = json.readTree(t.body());
            if (arr.isArray()) {
                List<String> names = new ArrayList<>(arr.size());
                arr.forEach(n -> names.add(n.path("name").asText("")));
                return OssVersionUtil.pickLatestStable(names);
            }
        }
        return null;
    }

    private String githubToken() {
        String raw = parameterService.getString("oss.github.token");
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.startsWith("ENC(") ? parameterService.decrypt(raw) : raw;
    }

    /* ---------- 独立服务当前版本探测 ---------- */

    /** 手动探测单个组件的当前运行版本(仅配了探测方式的独立服务);失败原因写 probeMessage,不抛业务异常 */
    public OssComponent probe(Long id) {
        OssComponent c = mapper.selectById(id);
        if (c == null) {
            throw new BizException(ResultCode.NOT_FOUND, "组件不存在");
        }
        if (!DictConst.OSS_TYPE_SERVICE.equals(c.getComponentType())) {
            throw new BizException(ResultCode.BAD_REQUEST, "仅独立服务支持当前版本探测");
        }
        if (!hasProbe(c)) {
            throw new BizException(ResultCode.BAD_REQUEST, "该组件未开启当前版本探测,请先在编辑里选择探测方式");
        }
        probeAndPersist(c);
        return mask(mapper.selectById(id));
    }

    private boolean hasProbe(OssComponent c) {
        return DictConst.OSS_TYPE_SERVICE.equals(c.getComponentType())
                && c.getProbeType() != null && !c.getProbeType().isBlank();
    }

    /** 探测并回写当前版本:探到就覆盖 current_version(顺带按最新版重算更新类型),没探到只记原因保留原值 */
    private void probeAndPersist(OssComponent c) {
        String version = null;
        String message;
        try {
            version = probeCurrentVersion(c);
            if (version != null && version.isBlank()) {
                version = null;
            }
            message = version == null ? "服务未返回版本号" : "已探测到当前运行版本";
        } catch (BizException e) {
            message = e.getMessage();
        } catch (Exception e) {
            log.warn("oss current version probe failed, name={}, probeType={}", c.getName(), c.getProbeType(), e);
            message = "探测失败:" + brief(e);
        }
        LambdaUpdateWrapper<OssComponent> w = new LambdaUpdateWrapper<OssComponent>()
                .eq(OssComponent::getId, c.getId())
                .set(OssComponent::getProbedAt, LocalDateTime.now())
                .set(OssComponent::getProbeMessage, truncate(message, 240));
        if (version != null) {
            w.set(OssComponent::getCurrentVersion, version);
            if (c.getLatestVersion() != null && !c.getLatestVersion().isBlank()) {
                w.set(OssComponent::getUpdateType, OssVersionUtil.updateType(version, c.getLatestVersion()));
            }
        }
        mapper.update(null, w);
    }

    /** 按探测方式调对方接口取运行版本 */
    private String probeCurrentVersion(OssComponent c) throws IOException {
        String base = probeBaseUrl(c);
        return switch (c.getProbeType()) {
            // Nextcloud 公开状态接口:取 versionstring(与发布 tag 同口径,如 30.0.2)
            case DictConst.OSS_PROBE_NEXTCLOUD ->
                    fetchJson("nextcloud", base + "/status.php", null).path("versionstring").asText(null);
            // Jellyfin 公开信息接口(免认证):Version 如 10.9.11
            case DictConst.OSS_PROBE_JELLYFIN ->
                    fetchJson("jellyfin", base + "/System/Info/Public", null).path("Version").asText(null);
            // Home Assistant 配置接口:需长期访问令牌
            case DictConst.OSS_PROBE_HA ->
                    fetchJson("homeassistant", base + "/api/config", bearer(probeToken(c))).path("version").asText(null);
            default -> null;
        };
    }

    private JsonNode fetchJson(String service, String url, Map<String, String> headers) throws IOException {
        ThirdPartyHttp.Resp r = ThirdPartyHttp.get(service, url, headers, TIMEOUT_MS);
        if (!r.ok()) {
            throw new BizException(ResultCode.INTERNAL_ERROR, httpMessage(r.status()));
        }
        return json.readTree(r.body());
    }

    /** 探测地址:先用手工填的,留空则从已接入的服务配置里取 */
    private String probeBaseUrl(OssComponent c) {
        String explicit = c.getProbeUrl();
        if (explicit != null && !explicit.isBlank()) {
            return trimSlash(normalizeHttp(explicit));
        }
        String auto = switch (c.getProbeType()) {
            case DictConst.OSS_PROBE_NEXTCLOUD -> nextcloudRootFromDevice();
            case DictConst.OSS_PROBE_JELLYFIN -> mediaServerUrl();
            default -> null;
        };
        if (auto == null || auto.isBlank()) {
            throw new BizException(ResultCode.BAD_REQUEST, "未找到已接入的服务地址,请在编辑里填写探测地址");
        }
        return trimSlash(auto);
    }

    /**
     * 已接入 Nextcloud 设备的站点根地址:存储设备里存的是 WebDAV 目录地址
     * (站点根 + /remote.php/dav/files/{用户名}),探测状态接口要剥回站点根。
     */
    private String nextcloudRootFromDevice() {
        List<StorageDevice> rows = storageDeviceMapper.selectList(new LambdaQueryWrapper<StorageDevice>()
                .eq(StorageDevice::getDeviceType, "NEXTCLOUD")
                .eq(StorageDevice::getStatus, "ACTIVE")
                .orderByAsc(StorageDevice::getId));
        for (StorageDevice d : rows) {
            String root = d.getRootPath();
            if (root == null || root.isBlank()) {
                continue;
            }
            int sep = root.indexOf('|');
            String url = sep > 0 ? root.substring(0, sep) : root;
            int dav = url.indexOf("/remote.php/");
            if (dav > 0) {
                url = url.substring(0, dav);
            }
            return url;
        }
        return null;
    }

    /** 放映厅媒体引擎地址(每家庭一条,取首条启用的) */
    private String mediaServerUrl() {
        MediaServer row = mediaServerMapper.selectOne(new LambdaQueryWrapper<MediaServer>()
                .eq(MediaServer::getEnabled, 1)
                .orderByAsc(MediaServer::getId)
                .last("limit 1"));
        return row == null ? null : row.getServerUrl();
    }

    private Map<String, String> bearer(String token) {
        return token == null || token.isBlank() ? null : Map.of("Authorization", "Bearer " + token);
    }

    /** 探测令牌:与 GitHub 令牌同一套(ENC 密文,明文兼容) */
    private String probeToken(OssComponent c) {
        String raw = c.getProbeToken();
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.startsWith("ENC(") ? parameterService.decrypt(raw) : raw;
    }

    /** 探测地址规范化:补 http:// 前缀、去尾斜杠;带非 http(s) 协议的直接拒绝 */
    private String normalizeHttp(String raw) {
        String v = raw.trim();
        String lower = v.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            if (lower.contains("://")) {
                throw new BizException(ResultCode.BAD_REQUEST, "探测地址必须以 http(s):// 开头");
            }
            v = "http://" + v;
        }
        return v;
    }

    private String trimSlash(String u) {
        String v = u.trim();
        return v.endsWith("/") ? v.substring(0, v.length() - 1) : v;
    }

    private String httpMessage(int status) {
        if (status == 401 || status == 403) {
            return "服务拒绝访问,请检查探测令牌";
        }
        if (status == 404) {
            return "该地址上找不到探测接口,请确认填的是站点根地址";
        }
        return "服务返回状态码 " + status;
    }

    /** 异常摘要:取最内层原因(连接类异常外层消息通常没信息量),常见网络故障给中文提示 */
    private String brief(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        if (t instanceof UnknownHostException) {
            return "无法解析该地址,请检查域名";
        }
        if (t instanceof ConnectException) {
            return "无法连接该地址,请检查地址与端口";
        }
        if (t instanceof SocketTimeoutException) {
            return "连接超时,请检查地址与网络";
        }
        String msg = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
        return truncate(msg, 120);
    }

    private String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    /** 出接口前抹掉令牌密文,只留「是否已配」标记 */
    private OssComponent mask(OssComponent c) {
        if (c == null) {
            return null;
        }
        c.setHasProbeToken(c.getProbeToken() != null && !c.getProbeToken().isBlank());
        c.setProbeToken(null);
        return c;
    }
}
