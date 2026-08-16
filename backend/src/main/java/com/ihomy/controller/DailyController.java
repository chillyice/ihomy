package com.ihomy.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ihomy.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.nio.charset.StandardCharsets;

/**
 * 每日内容接口:
 * 1) 每日一图 - 代理微软必应每日图片(无需 Key,公开接口,带当日缓存);
 * 2) 每日知识 - 内置分类知识库(历史/科学/文学/生活),按开启的分类随机返回,支持"换一条"。
 */
@Tag(name = "每日内容")
@RestController
@RequestMapping("/public")
@RequiredArgsConstructor
public class DailyController {

    private static final String BING_ENDPOINT =
            "https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=1&mkt=zh-CN";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();

    /** 当日图片缓存:key 为日期字符串,避免频繁请求必应 */
    private final Map<String, Map<String, Object>> imageCache = new ConcurrentHashMap<>();

    /** 内置知识库:分类 → 知识条目列表,可扩展 */
    private static final Map<String, List<String>> KNOWLEDGE = new HashMap<>();

    static {
        KNOWLEDGE.put("history", List.of(
                "秦始皇于公元前221年统一六国,建立中国历史上第一个中央集权王朝——秦朝。",
                "丝绸之路始于汉代张骞出使西域,此后成为东西方贸易与文化交流的大动脉。",
                "唐朝开元盛世(713-741)人口达约8000万,是当时世界最庞大繁荣的帝国之一。",
                "宋代发明了活字印刷术(毕昇,1041-1048),比欧洲古登堡早约400年。",
                "明成祖朱棣迁都北京,营建紫禁城,历时14年,历经明清两代24位皇帝。"));
        KNOWLEDGE.put("science", List.of(
                "光年不是时间单位而是长度单位,一光年约等于9.46万亿公里。",
                "人体细胞数量约37万亿,每天约有3000亿个细胞更新换代。",
                "水在4℃时密度最大,所以冰会浮在水面上,湖底冬季不会结冰。",
                "LED灯在通电时电子在半导体中产生光,效率高达90%以上,白炽灯只有5%。",
                "中国天眼(FAST)直径500米,是全世界最大口径的射电望远镜,可捕捉130亿光年外的信号。"));
        KNOWLEDGE.put("literature", List.of(
                "《红楼梦》前80回为曹雪芹所著,后40回通说为高鹗续写,全书人物超700个。",
                "李白一生创作诗歌900余首,「蜀道之难,难于上青天」传诵千年。",
                "《西游记》原著中孙悟空的金箍棒重一万三千五百斤。",
                "宋代三苏——苏洵、苏轼、苏辙,父子三人同列唐宋八大家。",
                "《荷马史诗》(伊利亚特/奥德赛)共约27600行,是西方文学源头之一。"));
        KNOWLEDGE.put("life", List.of(
                "煮饺子加少许盐或油,可减少粘连且让饺子皮更筋道。",
                "冰箱冷藏室温度应保持在2-5℃,冷冻室建议-18℃以下保存食物。",
                "白米饭剩饭加少许温水放入微波炉加热1分钟,口感如新蒸一般。",
                "鞋盒里放几包干燥剂,可防潮除异味,延长鞋子寿命。",
                "蔬菜水果放同一个塑料袋会加快腐烂,香蕉苹果和马铃薯分开存放更耐久。"));
    }

    @Operation(summary = "微软必应每日一图(当日缓存)")
    @GetMapping("/daily-image")
    public Result<Map<String, Object>> dailyImage() {
        String today = LocalDate.now().toString();
        Map<String, Object> cached = imageCache.get(today);
        if (cached != null) {
            return Result.success(cached);
        }
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(BING_ENDPOINT))
                    .timeout(Duration.ofSeconds(8)).GET().build();
            String json = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).body();
            JsonNode root = objectMapper.readTree(json);
            JsonNode item = root.path("images").get(0);
            String url = item.path("url").asText();
            Map<String, Object> data = new HashMap<>();
            data.put("url", "https://www.bing.com" + url);
            data.put("copyright", item.path("copyright").asText());
            imageCache.put(today, data);
            return Result.success(data);
        } catch (Exception e) {
            return Result.fail(500, "每日一图获取失败");
        }
    }

    @Operation(summary = "每日知识:按开启分类返回一条(types 逗号分隔,缺省=仅历史)")
    @GetMapping("/daily-knowledge")
    public Result<Map<String, Object>> dailyKnowledge(
            @RequestParam(defaultValue = "history") String types) {
        List<String> pools = new ArrayList<>();
        for (String t : types.split(",")) {
            List<String> list = KNOWLEDGE.get(t.trim());
            if (list != null) pools.addAll(list);
        }
        if (pools.isEmpty()) {
            pools.addAll(KNOWLEDGE.get("history"));
        }
        String content = pools.get((int) (System.currentTimeMillis() % pools.size()));
        Map<String, Object> data = new HashMap<>();
        data.put("content", content);
        data.put("types", types);
        return Result.success(data);
    }
}