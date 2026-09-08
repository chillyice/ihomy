package com.ihomy.service;

import cn.hutool.core.text.TextSimilarity;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ihomy.entity.Furniture;
import com.ihomy.entity.Room;
import com.ihomy.mapper.FurnitureMapper;
import com.ihomy.mapper.ItemMapper;
import com.ihomy.mapper.RoomMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 物品定位「本地规则解析」(V9.49 第一层):零 token、离线、毫秒级,替代 LLM 做常见找物/放物解析。
 * 找物=闭集反向匹配(把家庭已知的名称/别名/位置/家具/房间/房子当候选词,检查是否出现在用户 query 里,双向包含 + 短词近似);
 * 放物=正则抽取「把X放Y(的Z)里」结构,目标 roomId/furnitureId 从家庭清单闭集子串匹配(防编造)。
 * 只做第一层规则匹配;embedding 语义兜底为后续优化,本层不实现。LLM 兜底由 ItemAiService 在本层不自信时接管。
 */
@Service
@RequiredArgsConstructor
public class ItemLocalParser {

    /** 放物结构:可选「把」+ 物品名 + 动作词 + 位置描述 */
    private static final Pattern PUT_RE = Pattern.compile(
            "^(?:把)?(.+?)(?:放(?:在|到|进)?|收(?:在|进|到)?|摆(?:在|到|进)?|搁(?:在|到)?|塞(?:在|到|进)?)(.+)$");
    /** 尾部方位词(剥离后剩下的才是可匹配的目标名称) */
    private static final Pattern TAIL_PARTICLES = Pattern.compile(
            "(?:里|上|内|中|下|上面|下面|里面|中间|旁边|处|顶部|底下|内部|外面|之上|之下)$");
    /** 类型关键词(规则判断,默认 OTHER) */
    private static final Map<String, String> TYPE_KEYWORDS = Map.ofEntries(
            Map.entry("KITCHENWARE", "锅 碗 瓢 盆 刀 铲 勺 筷 叉 盘 杯 壶 砧板 锅铲"),
            Map.entry("INGREDIENT", "菜 米 面 油 盐 酱 醋 糖 肉 蛋 水果 蔬菜 调料 食材"),
            Map.entry("DAILY", "洗发水 沐浴露 肥皂 香皂 牙膏 牙刷 毛巾 纸巾 洗衣液 洗洁精 日化"),
            Map.entry("CLOTHES", "衣 裤 袜 鞋 帽 围巾 手套 外套 毛衣 裙子 衣服"),
            Map.entry("TOOL", "锤 螺丝刀 扳手 钳 剪 电钻 梯子 锯 工具"));

    private final ItemMapper itemMapper;
    private final RoomMapper roomMapper;
    private final FurnitureMapper furnitureMapper;

    /** 放物解析结果(供 ItemAiService 执行落库;aliases/quantity/unit 本地规则不强求) */
    public record PutPlan(String name, String aliases, String type, String position,
                          BigDecimal quantity, String unit, Long roomId, Long furnitureId, boolean move) {
    }

    // ---------- 找物 ----------

    /** 闭集反向匹配:家庭物品行(name/aliases/position/room/furniture/house)逐候选词查是否为 query 子串,命中打分排序 */
    public List<Map<String, Object>> findLocal(Long familyId, String query) {
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) return List.of();
        return itemMapper.selectItemByFamily(familyId, null, null, null, null).stream()
                .map(row -> Map.entry(score(row, q), row))
                .filter(e -> e.getKey() > 0)
                .sorted((a, b) -> b.getKey() - a.getKey())
                .limit(20)
                .map(Map.Entry::getValue)
                .toList();
    }

    /** 反向打分:名称 3 > 别名 2(逗号 split 逐个匹配) > 位置/家具/房间/房子各 1;任一命中即保留 */
    private int score(Map<String, Object> row, String query) {
        int s = 0;
        if (hit(str(row.get("name")), query)) s += 3;
        String aliases = str(row.get("aliases"));
        if (!aliases.isEmpty()) {
            for (String a : aliases.split("[,，/|、]")) {
                String al = a.trim();
                if (!al.isEmpty() && hit(al, query)) {
                    s += 2;
                    break;
                }
            }
        }
        if (hit(str(row.get("position")), query)) s += 1;
        if (hit(str(row.get("furniture_name")), query)) s += 1;
        if (hit(str(row.get("room_name")), query)) s += 1;
        if (hit(str(row.get("house_name")), query)) s += 1;
        return s;
    }

    /** 双向包含 + 短词近似(长度≥2 且长度差≤1 时按 LCS 相似度 ≥0.6 视为近似命中) */
    private boolean hit(String candidate, String query) {
        if (candidate == null || candidate.isBlank() || query == null || query.isBlank()) return false;
        String c = candidate.trim().toLowerCase(Locale.ROOT);
        String q = query.trim().toLowerCase(Locale.ROOT);
        if (c.isEmpty() || q.isEmpty()) return false;
        if (c.contains(q) || q.contains(c)) return true;
        if (c.length() >= 2 && q.length() >= 2 && Math.abs(c.length() - q.length()) <= 1) {
            return TextSimilarity.similar(c, q) >= 0.6;
        }
        return false;
    }

    // ---------- 放物 ----------

    /**
     * 本地放物解析:返回 plan,或 null(不自信——解析不出物品名或位置,由 ItemAiService 回退 LLM)。
     * move 恒为 true(本地规则「已存在即挪位更新、否则新增」,无法表达「同名新购」,后者由 LLM 兜底)。
     */
    public PutPlan parsePut(Long familyId, String text) {
        String q = text == null ? "" : text.trim();
        Matcher m = PUT_RE.matcher(q);
        if (!m.matches()) return null;
        String name = m.group(1).trim();
        String locDesc = m.group(2).trim();
        if (name.isEmpty() || name.length() > 100 || locDesc.isEmpty()) return null;

        String loc = stripTailParticles(locDesc);
        if (loc.isEmpty()) return null;

        List<Room> rooms = roomMapper.selectList(new LambdaQueryWrapper<Room>().eq(Room::getFamilyId, familyId));
        List<Furniture> furnitures = furnitureMapper.selectList(new LambdaQueryWrapper<Furniture>()
                .eq(Furniture::getFamilyId, familyId));

        // 闭集子串匹配目标(精确子串,不近似;取最长命中避免「床头柜」被「柜」抢)
        Furniture furniture = matchLongest(furnitures, loc, Furniture::getName);
        Room room = matchLongest(rooms, loc, Room::getName);

        Long furnitureId = furniture != null ? furniture.getId() : null;
        Long roomId = room != null ? room.getId() : null;
        if (furnitureId != null && furniture.getRoomId() != null) {
            roomId = furniture.getRoomId(); // 家具决定房间(与画布/裁剪口径一致)
        }
        if (furnitureId == null && roomId == null) return null; // 位置解析不出 → 不自信

        String position = extractPosition(loc, furniture, room);
        String type = guessType(q);
        return new PutPlan(name, null, type, position, null, null, roomId, furnitureId, true);
    }

    /** 取名字在 text 中最长命中的实体(纯子串匹配,防近音误配到别的位置) */
    private <T> T matchLongest(List<T> list, String text, java.util.function.Function<T, String> nameFn) {
        T best = null;
        int bestLen = 0;
        for (T t : list) {
            String n = nameFn.apply(t);
            if (n == null || n.isBlank()) continue;
            String c = n.trim().toLowerCase(Locale.ROOT);
            String q = text.toLowerCase(Locale.ROOT);
            if (c.contains(q) || q.contains(c)) {
                if (c.length() > bestLen) {
                    best = t;
                    bestLen = c.length();
                }
            }
        }
        return best;
    }

    /** 从位置描述里去掉命中的家具/房间名与连接词后,剩下的作为位置词(如「最上层」) */
    private String extractPosition(String loc, Furniture furniture, Room room) {
        String rest = loc;
        if (furniture != null && furniture.getName() != null) rest = rest.replace(furniture.getName(), " ");
        if (room != null && room.getName() != null) rest = rest.replace(room.getName(), " ");
        rest = rest.replace("的", " ").trim();
        rest = stripTailParticles(rest).trim();
        return rest.isEmpty() ? null : rest;
    }

    private String stripTailParticles(String s) {
        Matcher m = TAIL_PARTICLES.matcher(s);
        while (m.find()) {
            s = s.substring(0, m.start());
            m = TAIL_PARTICLES.matcher(s);
        }
        return s.trim();
    }

    /** 规则判断物品类型:命中任一关键词即归类,否则 OTHER */
    private String guessType(String q) {
        String query = q.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> e : TYPE_KEYWORDS.entrySet()) {
            for (String kw : e.getValue().split(" ")) {
                if (!kw.isEmpty() && query.contains(kw)) return e.getKey();
            }
        }
        return "OTHER";
    }

    private String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}
