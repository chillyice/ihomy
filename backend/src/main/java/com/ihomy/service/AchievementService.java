package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ihomy.common.AchievementCatalog;
import com.ihomy.common.AchievementCatalog.Achievement;
import com.ihomy.entity.FamilyAchievement;
import com.ihomy.entity.FamilyGameRecord;
import com.ihomy.mapper.FamilyAchievementMapper;
import com.ihomy.mapper.FamilyGameRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 成就业务(V15.17):目录在 AchievementCatalog,解锁行落 family_achievement。
 *
 * <p>惰性结算:只有读取成就墙(或签到后由控制器触发)时才聚合指标并写解锁行,
 * 游戏/内容写入路径只负责留痕(family_game_record)与内容表本身,不在这里做判定,
 * 避免每次发奖都跑一轮统计。指标实时聚合,所以不存在「进度卡住」问题。</p>
 */
@Service
@RequiredArgsConstructor
public class AchievementService {

    /** 家庭成就榜最多展示的成员数(家庭规模远小于此) */
    private static final int RANKING_LIMIT = 50;

    private final FamilyAchievementMapper achievementMapper;
    private final FamilyGameRecordMapper gameRecordMapper;

    /** 记录一局游戏完成:单人 reward / 一起玩结算 / 家庭对战终局各调用一次 */
    public void recordGame(Long userId, Long familyId, String gameCode, String source, String result) {
        if (userId == null || familyId == null || gameCode == null || gameCode.isBlank()) {
            return;
        }
        FamilyGameRecord record = new FamilyGameRecord();
        record.setUserId(userId);
        record.setFamilyId(familyId);
        // 统一去前缀:单人入口传裸码(mahjong),房间/对战传积分规则码(game_mahjong),库内只存一种口径
        record.setGameCode(normalizeGameCode(gameCode));
        record.setSource(source);
        record.setResult(result);
        gameRecordMapper.insert(record);
    }

    private static String normalizeGameCode(String gameCode) {
        String code = gameCode.trim();
        return code.startsWith("game_") ? code.substring("game_".length()) : code;
    }

    /**
     * 结算并返回成就墙:解锁本次达标项,返回全量目录 + 概览 + 家庭榜 + 本次新解锁。
     * 返回值里 items 的顺序与目录一致,前端按分类分组即可。
     */
    @Transactional
    public Map<String, Object> evaluate(Long userId, Long familyId) {
        Map<String, Object> metrics = achievementMapper.loadMetrics(userId);
        List<FamilyAchievement> existing = unlocked(userId);
        Map<String, FamilyAchievement> unlockedByCode = byCode(existing);

        List<Map<String, Object>> newlyUnlocked = new ArrayList<>();
        boolean inserted = false;
        for (Achievement a : AchievementCatalog.ACHIEVEMENTS) {
            if (unlockedByCode.containsKey(a.code())) {
                continue;
            }
            if (metric(metrics, a.metric()) < a.threshold()) {
                continue;
            }
            // 唯一键兜底并发:影响行数为 1 才算本次新解锁,为 0 说明其他请求已先写入
            if (achievementMapper.insertIgnore(userId, familyId, a.code(), a.score()) > 0) {
                inserted = true;
                Map<String, Object> toast = new LinkedHashMap<>();
                toast.put("code", a.code());
                toast.put("icon", a.icon());
                toast.put("score", a.score());
                newlyUnlocked.add(toast);
            }
        }
        // 新解锁的解锁时间以库为准(默认 CURRENT_TIMESTAMP),刷新一次列表即可拿到
        List<FamilyAchievement> all = inserted ? unlocked(userId) : existing;
        Map<String, FamilyAchievement> allByCode = byCode(all);

        List<Map<String, Object>> items = new ArrayList<>();
        int score = 0;
        for (Achievement a : AchievementCatalog.ACHIEVEMENTS) {
            long value = metric(metrics, a.metric());
            FamilyAchievement row = allByCode.get(a.code());
            if (row != null && row.getScore() != null) {
                score += row.getScore();
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("code", a.code());
            item.put("category", a.category());
            item.put("icon", a.icon());
            item.put("threshold", a.threshold());
            item.put("score", a.score());
            item.put("progress", (int) Math.min(value, a.threshold()));
            item.put("unlocked", row != null);
            item.put("unlockedAt", row == null ? null : row.getUnlockedAt());
            items.add(item);
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("unlocked", allByCode.size());
        summary.put("total", AchievementCatalog.ACHIEVEMENTS.size());
        summary.put("score", score);
        summary.put("level", AchievementCatalog.levelOf(score));
        summary.put("nextLevelScore", AchievementCatalog.nextLevelScore(score));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summary", summary);
        result.put("items", items);
        result.put("ranking", achievementMapper.selectRanking(familyId, RANKING_LIMIT));
        result.put("newlyUnlocked", newlyUnlocked);
        return result;
    }

    private List<FamilyAchievement> unlocked(Long userId) {
        return achievementMapper.selectList(new LambdaQueryWrapper<FamilyAchievement>()
                .eq(FamilyAchievement::getUserId, userId));
    }

    private static Map<String, FamilyAchievement> byCode(List<FamilyAchievement> rows) {
        return rows.stream().collect(Collectors.toMap(FamilyAchievement::getCode, r -> r, (a, b) -> a));
    }

    /** 指标值统一取 long(COUNT 为 Long、SUM 为 BigDecimal) */
    private static long metric(Map<String, Object> metrics, String key) {
        Object value = metrics == null ? null : metrics.get(key);
        return value instanceof Number n ? n.longValue() : 0L;
    }
}
