package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ihomy.common.BizException;
import com.ihomy.common.KadaSnippetScanner;
import com.ihomy.common.ResultCode;
import com.ihomy.dto.KadaSnippetSubmitRequest;
import com.ihomy.entity.KadaSnippet;
import com.ihomy.mapper.KadaSnippetMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 咔哒卡带社区发布与人工审核。
 *
 * <p>流程:提交 → 服务端规范化 + 机审 → 落库 PENDING → OPS 人工通过 → 写卡带文件 +
 * 重建社区索引。**任何等级都不会自动上架**;驳回已上架卡带会删文件并重建索引(下架)。
 *
 * <p>安全:卡带内容只当 JSON 数据处理,不反序列化到可执行类型;文件路径由服务端生成;
 * 发布入口按 IP 限流 + 内容 SHA-256 去重,防止刷接口与重复灌库。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KadaSnippetService {

    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";

    private final KadaSnippetMapper mapper;
    private final FileService fileService;
    private final ObjectMapper json = new ObjectMapper();

    /** 同一 IP 24 小时内最多提交数(匿名入口,只靠审核与限流兜底)。 */
    @Value("${app.kada.publish.max-per-day:5}")
    private int maxPerDay;

    /** 社区索引里的卡带地址必须是绝对 URL(咔哒客户端只收 http(s));部署到别的域名时改这里。 */
    @Value("${app.kada.public-base-url:https://ihomy.top}")
    private String publicBaseUrl = "https://ihomy.top";

    /** 提交并进入待审队列。返回受理编号、状态与服务端判定的危险等级。 */
    @Transactional
    public Map<String, Object> submit(KadaSnippetSubmitRequest req, String ip) {
        String name = cleanLine(req.getName());
        if (name.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "名称不能为空");
        }
        if (name.codePointCount(0, name.length()) > 60) {
            throw new BizException(ResultCode.BAD_REQUEST, "名称最多 60 个字");
        }
        String description = req.getDescription() == null ? "" : req.getDescription().trim();
        if (description.codePointCount(0, description.length()) > 500) {
            throw new BizException(ResultCode.BAD_REQUEST, "说明最多 500 个字");
        }
        String author = cleanLine(req.getAuthor());
        if (author.isEmpty()) {
            author = "匿名";
        }
        if (author.codePointCount(0, author.length()) > 40) {
            throw new BizException(ResultCode.BAD_REQUEST, "作者最多 40 个字");
        }
        List<String> tags = new ArrayList<>();
        if (req.getTags() != null) {
            for (String raw : req.getTags()) {
                String tag = cleanLine(raw);
                if (!tag.isEmpty() && !tags.contains(tag)) {
                    tags.add(tag);
                }
            }
        }
        if (tags.size() > 8 || tags.stream().anyMatch(t -> t.codePointCount(0, t.length()) > 20)) {
            throw new BizException(ResultCode.BAD_REQUEST, "标签最多 8 个、每个最多 20 个字");
        }

        String clientIp = ip == null ? "" : ip.trim();
        if (clientIp.length() > 64) {
            clientIp = clientIp.substring(0, 64);
        }
        LocalDateTime since = LocalDateTime.now().minusHours(24);
        long recent = mapper.selectCount(new LambdaQueryWrapper<KadaSnippet>()
                .eq(KadaSnippet::getSubmitterIp, clientIp)
                .ge(KadaSnippet::getCreatedAt, since));
        if (recent >= maxPerDay) {
            throw new BizException(ResultCode.TOO_MANY_REQUESTS, "今天提交的卡带太多了，请明天再试");
        }

        // 先规范化 / 剥本地数据,再写元信息,最后机审——存进库的就是将来上架的那份字节。
        String canonical = KadaSnippetScanner.canonicalize(req.getContent());
        canonical = KadaSnippetScanner.withMeta(canonical, name, description, author, tags);
        KadaSnippetScanner.Result scan = KadaSnippetScanner.analyze(canonical);
        String sha = KadaSnippetScanner.sha256Hex(canonical);
        long dup = mapper.selectCount(new LambdaQueryWrapper<KadaSnippet>()
                .eq(KadaSnippet::getContentSha256, sha));
        if (dup > 0) {
            throw new BizException(ResultCode.CONFLICT, "相同内容的卡带已提交过（可能正在审核或已上架）");
        }

        KadaSnippet row = new KadaSnippet();
        row.setName(name);
        row.setDescription(description);
        row.setAuthor(author);
        row.setTags(writeJson(tags));
        row.setContentJson(canonical);
        row.setContentSha256(sha);
        row.setDangerLevel(scan.level());
        row.setFindingsJson(writeJson(scan.findings()));
        row.setLegalFlagsJson(writeJson(scan.legalFlags()));
        row.setStatus(PENDING);
        row.setSubmitterIp(clientIp);
        mapper.insert(row);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", row.getId());
        out.put("status", PENDING);
        out.put("level", scan.level());
        return out;
    }

    /** OPS 列表:默认按提交时间倒序;列表不带 content_json(详情接口单独取)。 */
    public IPage<KadaSnippet> page(int current, int size, String status, Integer level, String q) {
        long pageNo = Math.max(1, current);
        long pageSize = Math.min(100, Math.max(1, size));
        LambdaQueryWrapper<KadaSnippet> w = new LambdaQueryWrapper<KadaSnippet>()
                .select(KadaSnippet.class, f -> !"contentJson".equals(f.getProperty()));
        if (StringUtils.hasText(status)) {
            w.eq(KadaSnippet::getStatus, status.trim().toUpperCase());
        }
        if (level != null && level >= 0 && level <= 3) {
            w.eq(KadaSnippet::getDangerLevel, level);
        }
        if (StringUtils.hasText(q)) {
            String kw = q.trim();
            w.and(x -> x.like(KadaSnippet::getName, kw)
                    .or().like(KadaSnippet::getAuthor, kw)
                    .or().like(KadaSnippet::getDescription, kw));
        }
        w.orderByDesc(KadaSnippet::getCreatedAt).orderByDesc(KadaSnippet::getId);
        return mapper.selectPage(new Page<>(pageNo, pageSize), w);
    }

    public Map<String, Object> summary() {
        long pending = countByStatus(PENDING);
        long risky = mapper.selectCount(new LambdaQueryWrapper<KadaSnippet>()
                .eq(KadaSnippet::getStatus, PENDING)
                .and(x -> x.ge(KadaSnippet::getDangerLevel, 2)
                        .or().ne(KadaSnippet::getLegalFlagsJson, "[]")));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("pending", pending);
        out.put("risky", risky);
        out.put("approved", countByStatus(APPROVED));
        out.put("rejected", countByStatus(REJECTED));
        return out;
    }

    public KadaSnippet get(Long id) {
        KadaSnippet row = mapper.selectById(id);
        if (row == null) {
            throw new BizException(ResultCode.NOT_FOUND, "卡带不存在");
        }
        return row;
    }

    /**
     * 人工通过:通过前**再跑一遍机审**(防库被改 / 规则更新),然后写文件、重建索引。
     * 若是已上架卡带的重复通过,直接返回。
     */
    @Transactional
    public KadaSnippet approve(Long id, Long reviewerId, String note) {
        KadaSnippet row = get(id);
        if (APPROVED.equals(row.getStatus())) {
            return row;
        }
        KadaSnippetScanner.Result scan = KadaSnippetScanner.analyze(row.getContentJson());
        String url = fileService.saveKadaSnippet(id, row.getContentJson());
        row.setDangerLevel(scan.level());
        row.setFindingsJson(writeJson(scan.findings()));
        row.setLegalFlagsJson(writeJson(scan.legalFlags()));
        row.setStatus(APPROVED);
        row.setPublishedUrl(url);
        row.setReviewerId(reviewerId);
        row.setReviewNote(trimNote(note));
        row.setReviewedAt(LocalDateTime.now());
        mapper.updateById(row);
        rebuildCommunityIndex();
        log.info("咔哒卡带审核通过: id={}, level={}, reviewer={}", id, scan.level(), reviewerId);
        return row;
    }

    /** 人工驳回:待审卡带直接拒绝;已上架卡带同时下架(删文件 + 重建索引)。 */
    @Transactional
    public KadaSnippet reject(Long id, Long reviewerId, String note) {
        KadaSnippet row = get(id);
        if (REJECTED.equals(row.getStatus())) {
            return row;
        }
        boolean wasApproved = APPROVED.equals(row.getStatus());
        if (wasApproved) {
            fileService.deleteKadaSnippet(id);
        }
        row.setStatus(REJECTED);
        row.setPublishedUrl("");
        row.setReviewerId(reviewerId);
        row.setReviewNote(StringUtils.hasText(note) ? trimNote(note) : "未通过人工审核");
        row.setReviewedAt(LocalDateTime.now());
        mapper.updateById(row);
        if (wasApproved) {
            rebuildCommunityIndex();
        }
        log.info("咔哒卡带驳回: id={}, wasApproved={}, reviewer={}", id, wasApproved, reviewerId);
        return row;
    }

    /** 社区索引(与官方 index.json 分开):只列已通过人工审核的卡带。 */
    private void rebuildCommunityIndex() {
        List<KadaSnippet> rows = mapper.selectList(new LambdaQueryWrapper<KadaSnippet>()
                .select(KadaSnippet::getId, KadaSnippet::getName, KadaSnippet::getDescription,
                        KadaSnippet::getAuthor, KadaSnippet::getTags, KadaSnippet::getDangerLevel,
                        KadaSnippet::getPublishedUrl, KadaSnippet::getReviewedAt)
                .eq(KadaSnippet::getStatus, APPROVED)
                .ne(KadaSnippet::getPublishedUrl, "")
                .orderByDesc(KadaSnippet::getReviewedAt)
                .orderByDesc(KadaSnippet::getId));
        ObjectNode root = json.createObjectNode();
        root.put("version", 1);
        ArrayNode snippets = root.putArray("snippets");
        for (KadaSnippet row : rows) {
            ObjectNode e = snippets.addObject();
            e.put("name", row.getName());
            e.put("url", absoluteUrl(row.getPublishedUrl()));
            e.put("description", row.getDescription() == null ? "" : row.getDescription());
            e.put("author", row.getAuthor() == null ? "" : row.getAuthor());
            ArrayNode tags = e.putArray("tags");
            for (String tag : readTags(row.getTags())) {
                tags.add(tag);
            }
            e.put("level", row.getDangerLevel() == null ? 0 : row.getDangerLevel());
        }
        fileService.saveKadaCommunityIndex(json.writeValueAsString(root));
    }

    private long countByStatus(String status) {
        return mapper.selectCount(new LambdaQueryWrapper<KadaSnippet>().eq(KadaSnippet::getStatus, status));
    }

    private List<String> readTags(String tagsJson) {
        List<String> tags = new ArrayList<>();
        if (!StringUtils.hasText(tagsJson)) {
            return tags;
        }
        try {
            JsonNode node = json.readTree(tagsJson);
            if (node != null && node.isArray()) {
                for (JsonNode item : node) {
                    if (item.isString()) {
                        tags.add(item.asString());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("卡带标签解析失败(忽略): {}", tagsJson);
        }
        return tags;
    }

    private String writeJson(Object value) {
        return json.writeValueAsString(value);
    }

    /** 库里存站内路径(`/files/...`),索引里必须给绝对地址,否则客户端按非 http(s) 拒绝下载。 */
    private String absoluteUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return "";
        }
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }
        String base = publicBaseUrl == null ? "" : publicBaseUrl.trim().replaceAll("/+$", "");
        return base + (url.startsWith("/") ? url : "/" + url);
    }

    /** 单行字段去掉换行 / 多余空白(名称、作者、标签不允许多行)。 */
    private String cleanLine(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\s+", " ").trim();
    }

    private String trimNote(String note) {
        if (note == null) {
            return "";
        }
        String trimmed = note.trim();
        return trimmed.length() > 500 ? trimmed.substring(0, 500) : trimmed;
    }
}
