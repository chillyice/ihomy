package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ihomy.common.AiConst;
import com.ihomy.common.BizException;
import com.ihomy.common.DictConst;
import com.ihomy.common.OssVersionUtil;
import com.ihomy.common.ResultCode;
import com.ihomy.common.ThirdPartyHttp;
import com.ihomy.entity.OssComponent;
import com.ihomy.mapper.OssComponentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
    private final ParameterService parameterService;
    private final AiService aiService;
    private final ObjectMapper json = new ObjectMapper();

    private static final int TIMEOUT_MS = 5000;
    private static final int CHECK_POOL = 8;
    /** 触发 Renovate 的 GitHub 仓库(owner/repo),可被 sys_parameter oss.github.repo 覆盖 */
    private static final String DEFAULT_GITHUB_REPO = "chillyice/ihomy";
    private final AtomicBoolean checking = new AtomicBoolean(false);

    /** 台账列表:可升级项排前(MAJOR > MINOR > PATCH),其余按名称 */
    public List<OssComponent> list() {
        List<OssComponent> all = mapper.selectList(null);
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
        return all;
    }

    /** 汇总:总数 / 可升级数 / 最近检测时间(供导航角标) */
    public Map<String, Object> summary() {
        List<OssComponent> all = mapper.selectList(null);
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

    /** 生成升级方案(按组件类型确定性输出命令步骤,无 AI) */
    public Map<String, Object> buildUpgradePlan(Long id) {
        OssComponent c = mapper.selectById(id);
        if (c == null) {
            throw new BizException(ResultCode.NOT_FOUND, "组件不存在");
        }
        if (c.getLatestVersion() == null || c.getLatestVersion().isBlank()) {
            throw new BizException(ResultCode.BAD_REQUEST, "尚未检测到最新版本,请先执行检查");
        }
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("id", c.getId());
        plan.put("name", c.getName());
        plan.put("componentType", c.getComponentType());
        plan.put("currentVersion", c.getCurrentVersion());
        plan.put("latestVersion", c.getLatestVersion());
        plan.put("updateType", c.getUpdateType());
        plan.put("repoUrl", c.getRepoUrl());

        List<String> steps = new ArrayList<>();
        String type = c.getComponentType() == null ? "" : c.getComponentType();
        switch (type) {
            case DictConst.OSS_TYPE_NPM, DictConst.OSS_TYPE_MAVEN -> {
                steps.add("该组件由 Renovate 管理:先「AI 评估」判断升级影响,评估可行后再「生成升级 PR」");
                steps.add("Renovate 将生成 " + c.getName() + " 从 " + c.getCurrentVersion() + " → " + c.getLatestVersion() + " 的升级 PR,CI 构建通过后人工 review 合并");
            }
            case DictConst.OSS_TYPE_SERVICE -> {
                String deploy = c.getDeployType() == null ? "" : c.getDeployType();
                if (DictConst.OSS_DEPLOY_CONTAINER.equals(deploy)) {
                    steps.add("该服务以容器部署:修改 compose/运行命令中的镜像 tag 为 " + c.getLatestVersion() + ",按现有部署流程重启");
                } else if (DictConst.OSS_DEPLOY_SYSTEMD.equals(deploy)) {
                    steps.add("该服务以 systemd 部署:按官方文档更新到 " + c.getLatestVersion() + " 后重启对应服务");
                } else {
                    steps.add("该组件为独立部署服务,升级在其自身管理界面/服务器完成(参考官方文档)");
                }
                steps.add("升级完成后回到本页,点击「确认已升级」把当前版本更新为 " + c.getLatestVersion());
            }
            default -> steps.add("未知组件类型,请参考官方文档手动升级");
        }
        if (DictConst.OSS_UPDATE_MAJOR.equals(c.getUpdateType())) {
            steps.add("⚠ 大版本升级,可能含破坏性变更,升级前先查看变更日志并备份");
        }
        plan.put("steps", steps);
        return plan;
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
                    "AI 评估不可用:" + e.getMessage() + "(需在「设置-家庭 AI 配置」为 OSS_UPGRADE_EVAL 功能绑定 LLM 模型)");
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
        if (!approveRenovateDashboard(repo, c)) {
            throw new BizException(ResultCode.INTERNAL_ERROR,
                    "触发 Renovate 失败:未在 Dependency Dashboard 找到对应条目,请确认 Renovate 已运行过");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("component", c.getName());
        out.put("repo", repo);
        out.put("message", "已标记升级,待 Renovate 下一轮生成 PR");
        return out;
    }

    public void edit(Long id, OssComponent body) {
        if (mapper.selectById(id) == null) {
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
            Map<String, String> headers = null;
            String token = githubToken();
            if (token != null) {
                headers = Map.of("Authorization", "Bearer " + token);
            }
            ThirdPartyHttp.Resp list = ThirdPartyHttp.get("github",
                    "https://api.github.com/repos/" + repo + "/issues?state=open&per_page=100", headers, TIMEOUT_MS);
            if (!list.ok()) {
                return false;
            }
            Long issueNo = null;
            String body = null;
            for (JsonNode issue : json.readTree(list.body())) {
                if ("Dependency Dashboard".equals(issue.path("title").asText())) {
                    issueNo = issue.path("number").asLong();
                    body = issue.path("body").asText("");
                    break;
                }
            }
            if (issueNo == null || body == null) {
                return false;
            }
            String match = componentDashboardToken(c);
            StringBuilder sb = new StringBuilder();
            boolean changed = false;
            for (String line : body.split("\n", -1)) {
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
            if (headers != null) {
                patchHeaders.putAll(headers);
            }
            patchHeaders.put("Content-Type", "application/json");
            Map<String, Object> patch = Map.of("body", sb.toString());
            ThirdPartyHttp.Resp resp = ThirdPartyHttp.request("github", "PATCH",
                    "https://api.github.com/repos/" + repo + "/issues/" + issueNo,
                    patchHeaders, json.writeValueAsBytes(patch), TIMEOUT_MS);
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
                && !DictConst.OSS_UPDATE_NONE.equals(c.getUpdateType());
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
            String latest = fetchLatest(c);
            if (latest != null) {
                mapper.update(null, new LambdaUpdateWrapper<OssComponent>()
                        .eq(OssComponent::getId, c.getId())
                        .set(OssComponent::getLatestVersion, latest)
                        .set(OssComponent::getUpdateType, OssVersionUtil.updateType(c.getCurrentVersion(), latest))
                        .set(OssComponent::getLastCheckedAt, LocalDateTime.now()));
            } else {
                // 拉取失败:仅刷新检测时间,保留上次结果
                mapper.update(null, new LambdaUpdateWrapper<OssComponent>()
                        .eq(OssComponent::getId, c.getId())
                        .set(OssComponent::getLastCheckedAt, LocalDateTime.now()));
            }
        } catch (Exception e) {
            log.warn("oss check failed, name={}, ref={}", c.getName(), c.getPackageRef(), e);
        }
    }

    private String fetchLatest(OssComponent c) throws IOException {
        String type = c.getComponentType() == null ? "" : c.getComponentType();
        if (DictConst.OSS_TYPE_NPM.equals(type)) {
            JsonNode n = json.readTree(ThirdPartyHttp.get("npm",
                    "https://registry.npmjs.org/" + c.getPackageRef() + "/latest", null, TIMEOUT_MS).body());
            return n.path("version").asText(null);
        }
        if (DictConst.OSS_TYPE_MAVEN.equals(type)) {
            return fetchMavenLatest(c.getPackageRef());
        }
        if (DictConst.OSS_TYPE_SERVICE.equals(type)) {
            return fetchGithubLatest(c.getPackageRef());
        }
        return null;
    }

    private String fetchMavenLatest(String ref) throws IOException {
        String[] ga = ref.split(":", 2);
        if (ga.length < 2) {
            return null;
        }
        String q = "g:\"" + ga[0] + "\" AND a:\"" + ga[1] + "\"";
        String url = "https://search.maven.org/solrsearch/select?q="
                + URLEncoder.encode(q, StandardCharsets.UTF_8) + "&rows=1&wt=json";
        JsonNode n = json.readTree(ThirdPartyHttp.get("maven", url, null, TIMEOUT_MS).body());
        JsonNode docs = n.path("response").path("docs");
        if (docs.isArray() && docs.size() > 0) {
            return docs.get(0).path("latestVersion").asText(null);
        }
        return null;
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
            if (tag != null && !tag.isBlank()) {
                return tag;
            }
        }
        // 403 限流 / 404(无 latest release):回退 tags 列表第一条
        ThirdPartyHttp.Resp t = ThirdPartyHttp.get("github",
                "https://api.github.com/repos/" + repo + "/tags", headers, TIMEOUT_MS);
        if (t.ok()) {
            JsonNode arr = json.readTree(t.body());
            if (arr.isArray() && arr.size() > 0) {
                return arr.get(0).path("name").asText(null);
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
}
