package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ihomy.entity.Synonym;
import com.ihomy.mapper.SynonymMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 同义词服务(V9.52):sys_synonym 全表内存缓存(数据量小,不引第三方缓存),
 * 提供别名→规范词归一化(canonicalOf)、规范词→组内别名(aliasesOf)、
 * 查询词变体扩展(expand,找物命中多词一义)、写入(upsert,录入联想/LLM 学习)。
 * 预热 @PostConstruct + 懒加载兜底(参照 sys_home_module 的 globalLoaded 双检锁);
 * 写入后按当前线程快照重建内存,保证同请求内即时可见且线程安全。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SynonymService {

    /** 找物扩展变体上限(原文 + 替换变体,防止大词典组合爆炸) */
    private static final int MAX_VARIANTS = 8;

    private final SynonymMapper synonymMapper;

    /** 内存快照(volatile 引用整体替换,读线程要么见旧要么见新,无中间态) */
    private record Snapshot(Map<String, String> aliasToCanonical,
                            Map<String, List<String>> canonicalToAliases,
                            Set<String> canonicals) {
    }

    private volatile Snapshot snap = new Snapshot(Map.of(), Map.of(), Set.of());
    private volatile boolean loaded = false;

    @PostConstruct
    void init() {
        // 预热:@PostConstruct 阶段 mapper 可能未就绪,失败不致命,首次查询走懒加载兜底
        load();
    }

    /** 从 DB 重建内存快照(启动预热/查询兜底/写入后刷新共用) */
    private void load() {
        try {
            List<Synonym> rows = synonymMapper.selectList(new LambdaQueryWrapper<Synonym>()
                    .orderByAsc(Synonym::getId));
            Map<String, String> a2c = new LinkedHashMap<>();
            Map<String, List<String>> c2a = new LinkedHashMap<>();
            Set<String> cs = new LinkedHashSet<>();
            for (Synonym s : rows) {
                String c = trimToNull(s.getCanonical());
                String a = trimToNull(s.getAlias());
                if (c == null || a == null) continue;
                cs.add(c.toLowerCase(Locale.ROOT));
                a2c.put(a.toLowerCase(Locale.ROOT), c);
                c2a.computeIfAbsent(c, k -> new ArrayList<>()).add(a);
            }
            snap = new Snapshot(a2c, c2a, cs);
            loaded = true;
            log.info("loaded {} synonym rows", rows.size());
        } catch (Exception e) {
            log.warn("init synonyms failed, will lazy load", e);
        }
    }

    private void ensureLoaded() {
        if (!loaded) {
            synchronized (this) {
                if (!loaded) load();
            }
        }
    }

    /**
     * 归一化:词本身是规范词则原样返回,是别名则返回规范词,未知返回 null。
     * 用于放物解析把「手纸」归一为「纸巾」,修正类型判断与重名归并。
     */
    public String canonicalOf(String word) {
        if (word == null || word.isBlank()) return null;
        String w = word.trim();
        ensureLoaded();
        Snapshot s = snap;
        String lower = w.toLowerCase(Locale.ROOT);
        if (s.canonicals().contains(lower)) return w;
        return s.aliasToCanonical().get(lower);
    }

    /** 规范词 → 组内别名(排除规范词本身);无别名返回空列表 */
    public List<String> aliasesOf(String canonical) {
        if (canonical == null || canonical.isBlank()) return List.of();
        ensureLoaded();
        List<String> list = snap.canonicalToAliases().get(canonical.trim());
        return list == null ? List.of() : list;
    }

    /** 全部规范词(逗号拼接,供 LLM 提示词作「优先复用」上下文,截断防膨胀) */
    public String canonicalList() {
        ensureLoaded();
        return String.join("、", snap.canonicalToAliases().keySet());
    }

    /**
     * 找物查询变体扩展:原文 + 把 query 中命中的别名替换为组内其它成员(规范词/其余别名)的变体。
     * 别名按长度降序匹配,避免「面巾」类短词抢在「面巾纸」长词前;上限 MAX_VARIANTS。
     */
    public List<String> expand(String text) {
        String t = text == null ? "" : text.trim();
        if (t.isEmpty()) return List.of();
        ensureLoaded();
        Snapshot s = snap;
        List<String> out = new ArrayList<>();
        out.add(t);
        String lower = t.toLowerCase(Locale.ROOT);
        List<String> aliases = new ArrayList<>(s.aliasToCanonical().keySet());
        aliases.sort((a, b) -> Integer.compare(b.length(), a.length())); // 长词优先
        for (String alias : aliases) {
            if (out.size() >= MAX_VARIANTS) break;
            int idx = lower.indexOf(alias);
            if (idx < 0) continue;
            String canonical = s.aliasToCanonical().get(alias);
            List<String> group = new ArrayList<>();
            group.add(canonical);
            List<String> als = s.canonicalToAliases().get(canonical);
            if (als != null) group.addAll(als);
            for (String member : group) {
                if (member.equalsIgnoreCase(alias)) continue;
                String variant = t.substring(0, idx) + member + t.substring(idx + alias.length());
                if (!out.contains(variant)) out.add(variant);
                if (out.size() >= MAX_VARIANTS) break;
            }
        }
        return out;
    }

    /**
     * 写入同义词对(幂等):已存在则跳过(不覆盖 BUILTIN 来源);新对入库并刷新内存。
     * 由录入联想(USER)/LLM 学习(LLM)调用,规范化词与别名须不同且长度 ≤50。
     */
    public synchronized void upsert(String canonical, String alias, String source) {
        String c = trimToNull(canonical);
        String a = trimToNull(alias);
        if (c == null || a == null) return;
        if (c.length() > 50) c = c.substring(0, 50);
        if (a.length() > 50) a = a.substring(0, 50);
        if (c.equalsIgnoreCase(a)) return;
        ensureLoaded();
        Snapshot s = snap;
        String existing = s.aliasToCanonical().get(a.toLowerCase(Locale.ROOT));
        if (existing != null && existing.equalsIgnoreCase(c)) return; // 已存在,不覆盖来源
        Synonym row = new Synonym();
        row.setCanonical(c);
        row.setAlias(a);
        row.setSource(source == null || source.isBlank() ? "USER" : source.trim());
        try {
            synonymMapper.insert(row);
        } catch (DuplicateKeyException e) {
            return; // 并发重复,uk_syn 兜底
        }
        load(); // 刷新内存快照
    }

    private String trimToNull(String s) {
        if (s == null) return null;
        String v = s.trim();
        return v.isEmpty() ? null : v;
    }
}
