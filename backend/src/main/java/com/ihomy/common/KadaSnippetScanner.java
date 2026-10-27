package com.ihomy.common;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * 咔哒卡带机审:把「发布 / 上架」的卡带 JSON 收敛成一份**只含数据**的子集配置,并给出
 * 危险等级、风险发现与法务关键词。
 *
 * <p>安全口径(与服务端存储/展示一起看):
 * <ul>
 *   <li>只解析 JSON 树,不反序列化到任何可执行类型,不执行内容;</li>
 *   <li>顶层字段白名单;剥掉 settings / folders / 本机路径类元信息;</li>
 *   <li>体积 / 条目 / 嵌套上限,先于解析判体积;</li>
 *   <li>输出重新序列化(规范化),不保存调用方原始字节;</li>
 *   <li>危险等级只作提示,是否上架一律由 OPS 人工审核决定。</li>
 * </ul>
 *
 * <p>规则必须与咔哒客户端 {@code kada-core::snippet::scan_snippet} 保持一致——两边算出的
 * 等级不同时,以服务端为准并写入审核页。
 */
public final class KadaSnippetScanner {

    public static final int MAX_BYTES = 256 * 1024;
    public static final int MAX_ENTRIES = 200;
    public static final int MAX_DEPTH = 16;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 卡带允许的顶层字段(其余一律拒,避免把未来格式 / 隐藏载荷带进市场)。 */
    private static final Set<String> ALLOWED_TOP = Set.of(
            "layers", "shortcuts", "remaps", "expansions", "gestures", "hot_corners", "wheel_edges",
            "kada_snippet", "settings", "folders", "version");

    private static final List<String> ENTRY_FIELDS = List.of(
            "shortcuts", "remaps", "expansions", "gestures", "hot_corners", "wheel_edges");

    private static final String[] SUSPICIOUS = {
            "-encodedcommand", "frombase64string", "invoke-expression", "iex(", "downloadstring",
            "downloadfile", "certutil -urlcache", "bitsadmin", "mshta ", "rundll32 ", "regsvr32 ",
            "schtasks ", "vssadmin delete", "bcdedit ", "netsh advfirewall", "set-mppreference",
            "add-mppreference", "mimikatz", "-windowstyle hidden", "powershell -w hidden"
    };

    /** 法务关键词:只标记、不自动拒绝(同一个词可能出现在「防诈骗提醒」这类正常文本里)。 */
    private static final List<LegalRule> LEGAL = List.of(
            new LegalRule("分裂国家 / 颠覆",
                    "分裂国家", "颠覆国家", "台独", "藏独", "疆独", "港独", "东突", "法轮功", "六四事件"),
            new LegalRule("侮辱 / 人身攻击", "傻逼", "操你妈", "去死", "贱人", "杂种", "狗东西"),
            new LegalRule("违法 / 违禁",
                    "毒品", "冰毒", "K粉", "买枪", "枪支弹药", "赌博", "代开发票", "洗钱", "诈骗", "裸聊", "成人视频")
    );

    private KadaSnippetScanner() {
    }

    /** 机审结果。 */
    public record Result(int level, int entries, List<String> findings, List<String> legalFlags) {
    }

    /**
     * 校验并规范化卡带内容:剥掉 settings / folders(设置与本机目录都不该跟着分享),
     * 保留业务数组与层定义,返回紧凑 JSON。
     *
     * @throws BizException 内容不是合法 JSON / 超限 / 含未知顶层字段 / 没有条目
     */
    public static String canonicalize(String content) {
        if (content == null || content.isBlank()) {
            throw new BizException(ResultCode.BAD_REQUEST, "卡带内容为空");
        }
        if (content.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new BizException(ResultCode.BAD_REQUEST, "卡带太大(上限 256 KB)");
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(content);
        } catch (Exception e) {
            throw new BizException(ResultCode.BAD_REQUEST, "卡带不是合法 JSON");
        }
        if (root == null || !root.isObject()) {
            throw new BizException(ResultCode.BAD_REQUEST, "卡带顶层必须是 JSON 对象");
        }
        for (String key : root.propertyNames()) {
            if (!ALLOWED_TOP.contains(key)) {
                throw new BizException(ResultCode.BAD_REQUEST, "卡带包含未知顶层字段:" + key);
            }
        }
        ObjectNode clean = MAPPER.createObjectNode();
        JsonNode layers = root.get("layers");
        if (layers != null) {
            if (!layers.isArray()) {
                throw new BizException(ResultCode.BAD_REQUEST, "layers 必须是数组");
            }
            clean.set("layers", layers);
        }
        int entries = 0;
        for (String key : ENTRY_FIELDS) {
            JsonNode arr = root.get(key);
            if (arr == null) {
                continue;
            }
            if (!arr.isArray()) {
                throw new BizException(ResultCode.BAD_REQUEST, key + " 必须是数组");
            }
            if (arr.size() > MAX_ENTRIES) {
                throw new BizException(ResultCode.BAD_REQUEST, "单个列表条目过多");
            }
            entries += arr.size();
            // 目录 id 是本机 UUID,带出去只会指向别人的目录;客户端已剥,这里兜底再剥一次。
            for (JsonNode item : arr) {
                if (item instanceof ObjectNode obj) {
                    obj.remove("folder");
                }
            }
            clean.set(key, arr);
        }
        if (entries == 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "卡带里没有可发布条目");
        }
        if (entries > MAX_ENTRIES) {
            throw new BizException(ResultCode.BAD_REQUEST, "卡带条目过多(上限 " + MAX_ENTRIES + " 条)");
        }
        return MAPPER.writeValueAsString(clean);
    }

    /** 把发布元信息写进顶层 `kada_snippet`(服务端为准,不信任客户端自带的那份)。 */
    public static String withMeta(String canonicalJson, String name, String description, String author, List<String> tags) {
        try {
            ObjectNode root = (ObjectNode) MAPPER.readTree(canonicalJson);
            ObjectNode meta = MAPPER.createObjectNode();
            meta.put("version", 1);
            meta.put("name", name);
            meta.put("description", description);
            meta.put("author", author);
            ArrayNode tagArray = meta.putArray("tags");
            for (String t : tags) {
                tagArray.add(t);
            }
            root.set("kada_snippet", meta);
            return MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            throw new BizException(ResultCode.BAD_REQUEST, "卡带元信息写入失败");
        }
    }

    /**
     * 对**已规范化**的卡带做机审:危险等级 / 条目数 / 风险发现 / 法务关键词。
     * 只读 JSON 树,不执行、不联网。
     */
    public static Result analyze(String canonicalJson) {
        JsonNode root;
        try {
            root = MAPPER.readTree(canonicalJson);
        } catch (Exception e) {
            throw new BizException(ResultCode.BAD_REQUEST, "卡带不是合法 JSON");
        }
        if (root == null || !root.isObject()) {
            throw new BizException(ResultCode.BAD_REQUEST, "卡带顶层必须是 JSON 对象");
        }
        int entries = 0;
        for (String key : ENTRY_FIELDS) {
            JsonNode arr = root.get(key);
            if (arr != null && arr.isArray()) {
                entries += arr.size();
            }
        }
        State state = new State();
        walk(root, 0, state);
        List<String> findings = new ArrayList<>();
        state.findings.forEach((desc, n) -> findings.add(n > 1 ? desc + " × " + n : desc));
        List<String> legalFlags = new ArrayList<>();
        state.legal.forEach((category, hits) ->
                legalFlags.add(category + "(" + String.join("、", hits) + ")"));
        return new Result(state.level, entries, findings, legalFlags);
    }

    /** SHA-256(十六进制小写):内容去重,不保存原文即可比对。 */
    public static String sha256Hex(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static void walk(JsonNode node, int depth, State state) {
        if (depth > MAX_DEPTH) {
            state.bump(3, "结构嵌套过深(超过 16 层)");
            return;
        }
        if (node.isObject()) {
            JsonNode type = node.get("type");
            if (type != null && type.isString()) {
                classify(type.asString(), state);
            }
            for (Map.Entry<String, JsonNode> e : node.properties()) {
                walk(e.getValue(), depth + 1, state);
            }
        } else if (node.isArray()) {
            if (node.size() > MAX_ENTRIES) {
                state.bump(state.level, "单个列表条目过多(" + node.size() + " 条)");
            }
            for (JsonNode item : node) {
                walk(item, depth + 1, state);
            }
        } else if (node.isString()) {
            String text = node.asString();
            String lower = text.toLowerCase(Locale.ROOT);
            for (String pat : SUSPICIOUS) {
                if (lower.contains(pat)) {
                    state.bump(state.level, "疑似命令混淆 / 下载执行:" + pat.trim());
                }
            }
            for (LegalRule rule : LEGAL) {
                for (String term : rule.terms) {
                    if (text.contains(term)) {
                        state.legal.computeIfAbsent(rule.category, k -> new TreeSet<>()).add(term);
                    }
                }
            }
        }
    }

    private static void classify(String kind, State state) {
        switch (kind) {
            case "command", "cmd", "powershell", "script" ->
                    state.bump(3, "执行命令 / 脚本(可运行任意代码)");
            case "launch", "open_folder", "close_program", "os", "system", "app", "open_url",
                    "window", "capture", "tool", "brightness" ->
                    state.bump(2, "启动程序 / 打开文件或网址 / 改系统状态");
            case "text", "keys", "mouse", "clipboard", "transform", "send_to" ->
                    state.bump(1, "注入按键或文本 / 操作鼠标 / 改剪贴板");
            case "pause_ms", "group", "if", "parallel" -> {
                // 组织 / 等待类动作，不改变等级。
            }
            default -> state.bump(3, "未知动作类型「" + kind + "」(可能来自更新版本，需人工确认)");
        }
    }

    private static final class State {
        int level;
        final Map<String, Integer> findings = new TreeMap<>();
        final Map<String, TreeSet<String>> legal = new LinkedHashMap<>();

        void bump(int lv, String desc) {
            if (lv > level) {
                level = lv;
            }
            findings.merge(desc, 1, Integer::sum);
        }
    }

    private record LegalRule(String category, String... terms) {
    }
}
