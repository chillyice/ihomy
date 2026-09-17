package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ObjectMapper json = new ObjectMapper();

    private static final int TIMEOUT_MS = 5000;
    private static final int CHECK_POOL = 8;
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
            case DictConst.OSS_TYPE_NPM -> {
                steps.add("修改 frontend/package.json 中 \"" + c.getPackageRef() + "\" 版本号: " + c.getCurrentVersion() + " → " + c.getLatestVersion());
                steps.add("cd frontend && npm install " + c.getPackageRef() + "@" + c.getLatestVersion() + " && npm run build");
                steps.add("按现有 deploy 流程部署前端(仅前端)");
            }
            case DictConst.OSS_TYPE_MAVEN -> {
                steps.add("修改 backend/pom.xml 中 \"" + c.getName() + "\" 版本号: " + c.getCurrentVersion() + " → " + c.getLatestVersion());
                steps.add("cd backend && mvn package -DskipTests");
                steps.add("按现有 deploy 流程部署后端");
            }
            case DictConst.OSS_TYPE_SERVICE -> {
                steps.add("该组件为独立部署服务,升级在其自身管理界面/服务器完成(参考官方文档)");
                steps.add("升级完成后回到本页,编辑该组件并更新当前版本为 " + c.getLatestVersion());
            }
            default -> steps.add("未知组件类型,请参考官方文档手动升级");
        }
        if (DictConst.OSS_UPDATE_MAJOR.equals(c.getUpdateType())) {
            steps.add("⚠ 大版本升级,可能含破坏性变更,升级前先查看变更日志并备份");
        }
        plan.put("steps", steps);
        return plan;
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
