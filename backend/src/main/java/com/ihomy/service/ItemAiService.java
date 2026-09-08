package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ihomy.common.AiConst;
import com.ihomy.common.BizException;
import com.ihomy.common.ResultCode;
import com.ihomy.entity.Furniture;
import com.ihomy.entity.House;
import com.ihomy.entity.Item;
import com.ihomy.entity.Room;
import com.ihomy.mapper.FurnitureMapper;
import com.ihomy.mapper.HouseMapper;
import com.ihomy.mapper.ItemMapper;
import com.ihomy.mapper.RoomMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 物品定位 AI 语义(3期,V9.40;V9.49 改「本地规则优先 + LLM 兜底」):
 * 找物/放物默认走 ItemLocalParser 本地规则(零 token 离线);绑定 LLM 时本地覆盖不了才回 aiService.chatJson。
 * 找物:本地反向闭集匹配,或 LLM 提取关键词后内存打分(名称>别名>位置/家具/房间/房子);
 * 放物:本地正则抽取「把X放Y(的Z)里」,或 LLM 解析五级粒度目标(roomId/furnitureId 必须从上下文清单选防编造);
 * 落库统一走 executePut(命中同名/别名且 move 则 UPDATE,否则 INSERT)。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ItemAiService {

    private static final String FIND_SYSTEM_PROMPT = """
            你是家庭物品定位助手。用户会用自然语言描述要找的物品(如"我的充电器在哪""谁看见遥控器了")。
            你的任务是从描述中提取用于搜索物品表的关键词。物品可搜索的字段:物品名、别名、位置描述,以及所在家具名、房间名、房子名。
            物品类型仅六种:KITCHENWARE(厨具)、INGREDIENT(食材)、DAILY(日化)、CLOTHES(衣服)、TOOL(工具)、OTHER(其他)。
            只输出 JSON 对象,不要输出任何其他内容:
            {"keywords": ["关键词1", "关键词2", "关键词3"], "type": null}
            keywords 提取 2~5 个:核心物品名(含常见叫法/别名/量词变体)、可能的放置位置词;type 仅在用户明确说出物品类别时填对应代码,否则必须为 null。""";

    private static final String PUT_SYSTEM_PROMPT = """
            你是家庭物品位置记录助手。用户会说"把X放在Y""X在Y的Z里"等,请解析出物品与位置。
            规则:
            1. 目标位置 furnitureId/roomId 必须从提供的上下文清单(furnitures/rooms)中选 id,严禁编造;
            2. 用户提到家具时优先给 furnitureId(家具会决定房间);只提到房间(如"在客厅地上")给 roomId;
            3. 上下文里找不到匹配的家具/房间时对应字段填 null(物品仍会登记,位置留空);
            4. type 仅六种:KITCHENWARE/INGREDIENT/DAILY/CLOTHES/TOOL/OTHER,判断不出填 OTHER;
            5. 物品名提取简短通用叫法(如"剪刀"),aliases 数组给同义叫法;quantity/unit 仅在用户说明数量时给;
            6. 若用户描述的是已有物品换了位置(如"剪刀现在在厨房"),move 填 true;明确是新买的物品填 false。
               items 清单是"名称(别名1/别名2)"紧凑文本,仅供判定用户说的是否为已有物品,不含位置信息。
            只输出 JSON 对象,不要输出任何其他内容:
            {"name":"物品名","aliases":["别名"],"type":"TOOL","position":"最上层抽屉","quantity":null,"unit":null,"roomId":null,"furnitureId":null,"move":true}""";

    private static final Set<String> ITEM_TYPES = Set.of("KITCHENWARE", "INGREDIENT", "DAILY", "CLOTHES", "TOOL", "OTHER");
    /** 上下文清单上限:防止大户型提示词膨胀(超出截断,按 id 升序保前 150) */
    private static final int CONTEXT_LIMIT = 150;

    private final AiService aiService;
    private final FamilyAiConfigService familyAiConfigService;
    private final ItemLocalParser itemLocalParser;
    private final HouseMapper houseMapper;
    private final RoomMapper roomMapper;
    private final FurnitureMapper furnitureMapper;
    private final ItemMapper itemMapper;
    private final ObjectMapper mapper = new ObjectMapper();

    // ---------- 找物 ----------

    public Map<String, Object> find(Long familyId, String query) {
        String q = requireText(query, "请描述要找的物品");
        FamilyAiConfigService.AiConfig cfg = familyAiConfigService.resolveForFeature(familyId, AiConst.FEATURE_ITEM_FIND);
        // LOCAL 或未配置 → 纯本地规则;绑定 LLM → 本地优先,无命中才 LLM 兜底
        boolean localOnly = cfg.type() == null || AiConst.TYPE_LOCAL.equals(cfg.type());
        if (localOnly) {
            return findResult(q, itemLocalParser.findLocal(familyId, q), true);
        }
        List<Map<String, Object>> localMatches = itemLocalParser.findLocal(familyId, q);
        if (!localMatches.isEmpty()) {
            return findResult(q, localMatches, true);
        }
        return llmFind(familyId, q);
    }

    /** LLM 找物兜底:提取关键词+类型 → 内存打分;模型不可用/异常回退原文关键词 */
    private Map<String, Object> llmFind(Long familyId, String q) {
        boolean aiParsed = true;
        JsonNode plan = null;
        try {
            plan = aiService.chatJson(familyId, AiConst.FEATURE_ITEM_FIND, FIND_SYSTEM_PROMPT, "用户找物描述:" + q);
        } catch (Exception e) {
            aiParsed = false;
            log.warn("[AI找物] AI 解析不可用,回退原文关键词 family={} query={}", familyId, q);
        }
        List<String> parsed = plan == null ? List.of() : strings(plan.get("keywords"));
        String type = plan == null ? null : normTypeOrNull(plan.path("type"));
        List<String> keywords = parsed.isEmpty() ? List.of(q.trim()) : parsed; // AI 拆解失败回退原文

        List<Map<String, Object>> matches = itemMapper.selectItemByFamily(familyId, null, null, null, null)
                .stream()
                .filter(row -> type == null || type.equals(str(row.get("type"))))
                .map(row -> Map.entry(score(row, keywords), row))
                .filter(e -> e.getKey() > 0)
                .sorted((a, b) -> b.getKey() - a.getKey())
                .limit(20)
                .map(Map.Entry::getValue)
                .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("aiParsed", aiParsed);
        result.put("reply", matches.isEmpty() ? "没有找到相关物品" : "找到 " + matches.size() + " 件相关物品");
        result.put("keywords", keywords);
        result.put("matches", matches);
        return result;
    }

    private Map<String, Object> findResult(String q, List<Map<String, Object>> matches, boolean aiParsed) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("aiParsed", aiParsed);
        result.put("reply", matches.isEmpty() ? "没有找到相关物品" : "找到 " + matches.size() + " 件相关物品");
        result.put("keywords", List.of(q.trim()));
        result.put("matches", matches);
        return result;
    }

    /** 关键词打分:物品名 3 > 别名 2 > 位置/家具/房间/房子 1;任一关键词命中即保留 */
    private int score(Map<String, Object> row, List<String> keywords) {
        String name = str(row.get("name"));
        String aliases = str(row.get("aliases"));
        String position = str(row.get("position"));
        String furniture = str(row.get("furniture_name"));
        String room = str(row.get("room_name"));
        String house = str(row.get("house_name"));
        int s = 0;
        for (String kw : keywords) {
            String k = kw == null ? "" : kw.trim();
            if (k.isEmpty()) continue;
            if (name.contains(k)) s += 3;
            if (aliases.contains(k)) s += 2;
            if (position.contains(k) || furniture.contains(k) || room.contains(k) || house.contains(k)) s += 1;
        }
        return s;
    }

    // ---------- 放物 ----------

    public Map<String, Object> put(Long userId, Long familyId, String text) {
        String q = requireText(text, "请描述物品放置位置");
        FamilyAiConfigService.AiConfig cfg = familyAiConfigService.resolveForFeature(familyId, AiConst.FEATURE_ITEM_PUT);
        boolean localOnly = cfg.type() == null || AiConst.TYPE_LOCAL.equals(cfg.type());
        if (localOnly) {
            ItemLocalParser.PutPlan plan = itemLocalParser.parsePut(familyId, q);
            if (plan == null) {
                throw new BizException(ResultCode.BAD_REQUEST, "未能识别物品与位置,请换个说法(如「把温度计放厨房的橱柜里」)");
            }
            return executePut(userId, familyId, plan);
        }
        // 绑定 LLM:本地优先,不自信(解析不出物品名或位置)才 LLM 兜底
        ItemLocalParser.PutPlan localPlan = itemLocalParser.parsePut(familyId, q);
        if (localPlan != null) {
            return executePut(userId, familyId, localPlan);
        }
        return llmPut(userId, familyId, q);
    }

    /** LLM 放物兜底:解析五级粒度目标(防编造),再走统一落库 */
    private Map<String, Object> llmPut(Long userId, Long familyId, String q) {
        JsonNode plan = aiService.chatJson(familyId, AiConst.FEATURE_ITEM_PUT, PUT_SYSTEM_PROMPT,
                "家庭上下文清单:\n" + toJson(buildContext(familyId)) + "\n\n用户描述:" + q);

        String name = plan.path("name").asText("").trim();
        if (name.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "AI 未能识别物品名,请换个说法重试");
        }
        if (name.length() > 100) name = name.substring(0, 100);
        String type = normType(plan.path("type").asText(null));
        String position = truncateTo(blankToNull(plan.path("position").asText(null)), 50);
        String aliases = joinAliases(plan.get("aliases"));
        BigDecimal quantity = plan.path("quantity").isNumber() ? plan.path("quantity").decimalValue() : null;
        String unit = truncateTo(blankToNull(plan.path("unit").asText(null)), 20);
        boolean move = plan.path("move").asBoolean(true);

        Set<Long> roomIds = roomMapper.selectList(null).stream().map(Room::getId).collect(Collectors.toSet());
        Set<Long> furnitureIds = furnitureMapper.selectList(null).stream().map(Furniture::getId).collect(Collectors.toSet());
        Long furnitureId = validId(plan.path("furnitureId"), furnitureIds);
        Long roomId = validId(plan.path("roomId"), roomIds);
        Furniture furniture = furnitureId == null ? null : furnitureMapper.selectById(furnitureId);
        if (furnitureId != null) {
            if (furniture == null) {
                throw new BizException(ResultCode.NOT_FOUND, "家具不存在");
            }
            // 家具决定房间(与画布/裁剪的家具分配口径一致);家具在库中(roomId 空)时物品仅锚家具
            roomId = furniture.getRoomId();
        }

        return executePut(userId, familyId, new ItemLocalParser.PutPlan(
                name, aliases, type, position, quantity, unit, roomId, furnitureId, move));
    }

    /** 统一落库:命中同名/别名且 move 则 UPDATE(挪位),否则 INSERT */
    private Map<String, Object> executePut(Long userId, Long familyId, ItemLocalParser.PutPlan p) {
        Item existing = findByNameOrAlias(familyId, p.name());
        boolean moved;
        Long itemId;
        String itemName;
        if (existing != null && p.move()) {
            LambdaUpdateWrapper<Item> uw = new LambdaUpdateWrapper<Item>()
                    .eq(Item::getId, existing.getId())
                    .set(Item::getRoomId, p.roomId())
                    .set(Item::getFurnitureId, p.furnitureId())
                    .set(Item::getPosition, p.position());
            if (!Objects.equals(existing.getFurnitureId(), p.furnitureId())) {
                uw.set(Item::getRelX, BigDecimal.valueOf(0.5)).set(Item::getRelY, BigDecimal.valueOf(0.5));
            }
            itemMapper.update(null, uw);
            moved = true;
            itemId = existing.getId();
            itemName = existing.getName();
        } else {
            Item item = new Item();
            item.setFamilyId(familyId);
            item.setFurnitureId(p.furnitureId());
            item.setRoomId(p.roomId());
            item.setName(p.name());
            item.setAliases(p.aliases());
            item.setPosition(p.position());
            item.setType(p.type());
            item.setQuantity(p.quantity());
            item.setUnit(p.unit());
            if (p.furnitureId() != null || p.roomId() != null) {
                item.setRelX(BigDecimal.valueOf(0.5));
                item.setRelY(BigDecimal.valueOf(0.5));
            }
            item.setCreatedBy(userId);
            itemMapper.insert(item);
            moved = false;
            itemId = item.getId();
            itemName = p.name();
        }

        String loc = locationDesc(p.roomId(), p.furnitureId(), p.position());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("moved", moved);
        result.put("itemId", itemId);
        result.put("name", itemName);
        result.put("location", loc);
        result.put("reply", loc == null
                ? (moved ? "已更新「" + itemName + "」,但没有识别到具体位置" : "已记录「" + itemName + "」,但没有识别到具体位置,可稍后在列表中补充")
                : (moved ? "已把「" + itemName + "」移到 " + loc : "已记录「" + itemName + "」,放在 " + loc));
        log.info("[AI放物] family={} item={} moved={} location={}", familyId, itemName, moved, loc);
        return result;
    }

    /** 同名或别名命中(忽略大小写;优先精确同名) */
    private Item findByNameOrAlias(Long familyId, String name) {
        List<Item> items = itemMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Item>()
                .eq(Item::getFamilyId, familyId));
        String lower = name.toLowerCase(Locale.ROOT);
        Item aliasHit = null;
        for (Item it : items) {
            if (it.getName() != null && it.getName().trim().toLowerCase(Locale.ROOT).equals(lower)) {
                return it;
            }
            if (aliasHit == null && it.getAliases() != null && it.getAliases().contains(name)) {
                aliasHit = it;
            }
        }
        return aliasHit;
    }

    private String locationDesc(Long roomId, Long furnitureId, String position) {
        StringBuilder sb = new StringBuilder();
        if (roomId != null) {
            Room r = roomMapper.selectById(roomId);
            if (r != null) sb.append(r.getName());
        }
        if (furnitureId != null) {
            Furniture f = furnitureMapper.selectById(furnitureId);
            if (f != null) {
                if (sb.length() > 0) sb.append("/");
                sb.append(f.getName());
            }
        }
        if (position != null && !position.isBlank()) {
            if (sb.length() > 0) sb.append("/");
            sb.append(position);
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    // ---------- 上下文 ----------

    /** 家庭五级粒度上下文(房子/房间/家具 + 已有物品),供 AI 从清单中选目标与判定挪位 */
    private Map<String, Object> buildContext(Long familyId) {
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("houses", houseMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<House>()
                .eq(House::getFamilyId, familyId)).stream()
                .limit(CONTEXT_LIMIT)
                .map(h -> brief(h.getId(), h.getName()))
                .toList());
        ctx.put("rooms", roomMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Room>()
                .eq(Room::getFamilyId, familyId)).stream()
                .limit(CONTEXT_LIMIT)
                .map(r -> {
                    Map<String, Object> m = brief(r.getId(), r.getName());
                    m.put("floor", r.getFloor());
                    m.put("houseId", r.getHouseId());
                    return m;
                })
                .toList());
        ctx.put("furnitures", furnitureMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Furniture>()
                .eq(Furniture::getFamilyId, familyId)).stream()
                .limit(CONTEXT_LIMIT)
                .map(f -> {
                    Map<String, Object> m = brief(f.getId(), f.getName());
                    if (f.getRoomId() != null) m.put("roomId", f.getRoomId());
                    return m;
                })
                .toList());
        ctx.put("items", itemMapper.selectItemByFamily(familyId, null, null, null, null).stream()
                .limit(CONTEXT_LIMIT)
                .map(row -> {
                    // 紧凑"名称(别名1/别名2)"文本:省 id(AI 无需回传物品 id,后端按名称/别名匹配)与 JSON 键名 token
                    String name = str(row.get("name"));
                    String aliases = str(row.get("aliases"));
                    return aliases.isEmpty() ? name : name + "(" + aliases.replace(",", "/") + ")";
                })
                .toList());
        return ctx;
    }

    private Map<String, Object> brief(Long id, String name) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        return m;
    }

    private String toJson(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (Exception e) {
            return "{}";
        }
    }

    // ---------- 小工具 ----------

    /** AI 返回的 id 必须在家庭清单内(防编造/防跨家庭),否则视为未识别 */
    private Long validId(JsonNode node, Set<Long> allowed) {
        if (node == null || !node.canConvertToLong()) return null;
        long v = node.asLong();
        return allowed.contains(v) ? v : null;
    }

    private String normType(String type) {
        String t = type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
        return ITEM_TYPES.contains(t) ? t : "OTHER";
    }

    /** 找物用:AI 未给类型(或非法)返回 null=不按类型过滤;normType 的 OTHER 默认仅放物入参用 */
    private String normTypeOrNull(JsonNode node) {
        if (node == null || !node.isTextual()) return null;
        String t = node.asText("").trim().toUpperCase(Locale.ROOT);
        return ITEM_TYPES.contains(t) ? t : null;
    }

    private List<String> strings(JsonNode array) {
        List<String> out = new ArrayList<>();
        if (array != null && array.isArray()) {
            for (JsonNode n : array) {
                String s = n.asText("").trim();
                if (!s.isEmpty()) out.add(s);
            }
        }
        return out;
    }

    private String joinAliases(JsonNode array) {
        List<String> list = strings(array);
        if (list.isEmpty()) return null;
        String joined = String.join(",", list);
        return truncateTo(joined, 200);
    }

    private String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private String truncateTo(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    private String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private String requireText(String s, String msg) {
        if (s == null || s.isBlank()) throw new BizException(ResultCode.BAD_REQUEST, msg);
        return s.trim();
    }
}
