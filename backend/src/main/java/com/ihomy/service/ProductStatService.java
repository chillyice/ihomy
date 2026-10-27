package com.ihomy.service;

import com.ihomy.entity.ProductStat;
import com.ihomy.mapper.ProductStatMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 咔哒 / 比特口袋访问与下载统计:
 * <ul>
 *   <li>访问 = 落地页打点(POST /public/page-view),下载 = 安装包下载接口命中一次;</li>
 *   <li>按天累加进 report_product_stat,与访问日志解耦——nginx 静态直出 /files 的下载请求
 *       不进后端日志,日志口径永远统计不到下载;</li>
 *   <li>运维页支持全部/日/周/月/年区间,跨度大于两个月时自动按月分桶。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductStatService {

    /** 产品键 = 落地页打点 ?page= 值 = /files 下的分发目录名 */
    public static final List<String> PRODUCTS = List.of("kada", "bit_pouch");

    /** 安装包后缀:只认这些才算下载,排除 latest.json/.sig 等更新清单轮询 */
    private static final List<String> INSTALLER_SUFFIXES =
            List.of(".exe", ".msi", ".zip", ".dmg", ".deb", ".appimage", ".apk");
    /** 下载文件名:只放行纯文件名(无路径分隔符),挡目录穿越 */
    private static final Pattern SAFE_FILE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");

    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");
    /** 全部区间跨度超过这么多天就按月分桶,否则按天 */
    private static final long MONTHLY_THRESHOLD_DAYS = 62;

    private final ProductStatMapper statMapper;

    public void recordView(String product) {
        record(product, 1, 0);
    }

    public void recordDownload(String product) {
        record(product, 0, 1);
    }

    private void record(String product, int views, int downloads) {
        if (product == null || !PRODUCTS.contains(product)) return;
        try {
            statMapper.increment(product, views, downloads);
        } catch (Exception e) {
            // 统计失败不影响落地页与下载本身
            log.warn("产品统计写入失败, product={}", product, e);
        }
    }

    /** 安装包文件名是否可下载(纯文件名 + 安装包后缀) */
    public static boolean isInstaller(String file) {
        if (file == null || !SAFE_FILE.matcher(file).matches()) return false;
        String n = file.toLowerCase(Locale.ROOT);
        return INSTALLER_SUFFIXES.stream().anyMatch(n::endsWith);
    }

    /** 归一化区间键:非法值退回全部 */
    static String normalizeRange(String range) {
        if (range == null) return "all";
        return switch (range) {
            case "day", "week", "month", "year" -> range;
            default -> "all";
        };
    }

    /** 区间起点(含);全部返回 null 表示不限 */
    static LocalDate startOf(String range, LocalDate today) {
        return switch (range) {
            case "day" -> today;
            case "week" -> today.with(DayOfWeek.MONDAY);
            case "month" -> today.withDayOfMonth(1);
            case "year" -> today.withDayOfYear(1);
            default -> null;
        };
    }

    /**
     * 区间统计:products 为区间内合计,buckets 为按天/按月分桶(新→旧,无数据的桶补 0)。
     *
     * @param range all(默认)/day/week/month/year
     */
    public Map<String, Object> stats(String rangeParam) {
        LocalDate today = LocalDate.now();
        String range = normalizeRange(rangeParam);
        LocalDate start = startOf(range, today);

        LocalDate first = start != null ? start : statMapper.selectMinDate();
        boolean monthly = "year".equals(range)
                || ("all".equals(range) && first != null
                    && ChronoUnit.DAYS.between(first, today) > MONTHLY_THRESHOLD_DAYS);

        LocalDate from = start != null ? start : first;
        List<String> periods = new ArrayList<>();
        if (from != null) {
            if (monthly) {
                for (LocalDate d = from.withDayOfMonth(1); !d.isAfter(today); d = d.plusMonths(1)) {
                    periods.add(d.format(MONTH_FMT));
                }
            } else {
                for (LocalDate d = from; !d.isAfter(today); d = d.plusDays(1)) {
                    periods.add(d.format(DAY_FMT));
                }
            }
        }

        List<Map<String, Object>> rows = statMapper.selectBuckets(start, today, monthly ? "%Y-%m" : "%Y-%m-%d");

        Map<String, long[]> totals = new LinkedHashMap<>();
        PRODUCTS.forEach(k -> totals.put(k, new long[2]));
        Map<String, Map<String, long[]>> byPeriod = new LinkedHashMap<>();
        periods.forEach(p -> byPeriod.put(p, new LinkedHashMap<>()));
        for (Map<String, Object> row : rows) {
            String product = String.valueOf(row.get("productCode"));
            long[] total = totals.get(product);
            if (total == null) continue; // 历史脏产品键不计入
            long v = num(row.get("views"));
            long d = num(row.get("downloads"));
            total[0] += v;
            total[1] += d;
            byPeriod.computeIfAbsent(String.valueOf(row.get("period")), k -> new LinkedHashMap<>())
                    .put(product, new long[] { v, d });
        }

        List<Map<String, Object>> products = new ArrayList<>();
        for (String key : PRODUCTS) {
            long[] total = totals.get(key);
            Map<String, Object> one = new LinkedHashMap<>();
            one.put("key", key);
            one.put("views", total[0]);
            one.put("downloads", total[1]);
            products.add(one);
        }

        List<Map<String, Object>> buckets = new ArrayList<>();
        for (Map.Entry<String, Map<String, long[]>> entry : byPeriod.entrySet()) {
            Map<String, Object> items = new LinkedHashMap<>();
            for (String key : PRODUCTS) {
                long[] a = entry.getValue().get(key);
                Map<String, Object> one = new LinkedHashMap<>();
                one.put("views", a == null ? 0 : a[0]);
                one.put("downloads", a == null ? 0 : a[1]);
                items.put(key, one);
            }
            Map<String, Object> bucket = new LinkedHashMap<>();
            bucket.put("period", entry.getKey());
            bucket.put("items", items);
            buckets.add(bucket);
        }
        Collections.reverse(buckets); // 运维页表格按新→旧展示

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("range", range);
        result.put("startDate", from == null ? null : from.format(DAY_FMT));
        result.put("endDate", today.format(DAY_FMT));
        result.put("granularity", monthly ? "month" : "day");
        result.put("products", products);
        result.put("buckets", buckets);
        return result;
    }

    private static long num(Object v) {
        return v instanceof Number n ? n.longValue() : 0L;
    }
}
