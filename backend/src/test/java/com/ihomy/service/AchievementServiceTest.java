package com.ihomy.service;

import com.ihomy.common.AchievementCatalog;
import com.ihomy.entity.FamilyAchievement;
import com.ihomy.entity.FamilyGameRecord;
import com.ihomy.mapper.FamilyAchievementMapper;
import com.ihomy.mapper.FamilyGameRecordMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 成就结算:阈值判定、唯一键幂等、进度封顶、并发(影响行数 0)不算新解锁、成就分与等级、
 * 游戏留痕的编码归一。
 */
class AchievementServiceTest {

    private final FamilyAchievementMapper achievements = mock(FamilyAchievementMapper.class);
    private final FamilyGameRecordMapper gameRecords = mock(FamilyGameRecordMapper.class);
    private final AchievementService service = new AchievementService(achievements, gameRecords);

    @Test
    void evaluateUnlocksOnlyQualifiedAchievements() {
        Map<String, Object> metrics = metrics(Map.of("checkinTotal", 1, "checkinMaxStreak", 7));
        when(achievements.loadMetrics(11L)).thenReturn(metrics);
        when(achievements.selectList(any())).thenReturn(List.of(), List.of(unlocked("checkin_first", 10)));
        when(achievements.insertIgnore(eq(11L), eq(2L), anyString(), anyInt())).thenReturn(1);
        when(achievements.selectRanking(2L, 50)).thenReturn(List.of());

        Map<String, Object> out = service.evaluate(11L, 2L);

        // 达到 1 次签到与连续 7 天两条,其余指标为 0 不解锁
        verify(achievements).insertIgnore(11L, 2L, "checkin_first", 10);
        verify(achievements).insertIgnore(11L, 2L, "checkin_week", 20);
        verify(achievements, times(2)).insertIgnore(anyLong(), anyLong(), anyString(), anyInt());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> newly = (List<Map<String, Object>>) out.get("newlyUnlocked");
        assertEquals(2, newly.size());
        assertEquals("checkin_first", newly.get(0).get("code"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) out.get("items");
        assertEquals(AchievementCatalog.ACHIEVEMENTS.size(), items.size());
    }

    @Test
    void evaluateIsIdempotentForAlreadyUnlockedCodes() {
        when(achievements.loadMetrics(11L)).thenReturn(metrics(Map.of("checkinTotal", 3)));
        when(achievements.selectList(any())).thenReturn(List.of(unlocked("checkin_first", 10)));
        when(achievements.selectRanking(2L, 50)).thenReturn(List.of());

        Map<String, Object> out = service.evaluate(11L, 2L);

        verify(achievements, never()).insertIgnore(anyLong(), anyLong(), anyString(), anyInt());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> newly = (List<Map<String, Object>>) out.get("newlyUnlocked");
        assertTrue(newly.isEmpty());
        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) out.get("summary");
        assertEquals(1, summary.get("unlocked"));
        assertEquals(10, summary.get("score"));
    }

    @Test
    void concurrentInsertCountsAsNotNewlyUnlocked() {
        when(achievements.loadMetrics(11L)).thenReturn(metrics(Map.of("checkinTotal", 1)));
        when(achievements.selectList(any())).thenReturn(List.of());
        // 唯一键已存在(别的请求先写),影响行数 0 → 本次不报新解锁,也不算插入
        when(achievements.insertIgnore(anyLong(), anyLong(), anyString(), anyInt())).thenReturn(0);
        when(achievements.selectRanking(2L, 50)).thenReturn(List.of());

        Map<String, Object> out = service.evaluate(11L, 2L);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> newly = (List<Map<String, Object>>) out.get("newlyUnlocked");
        assertTrue(newly.isEmpty());
        // 没有新增就不重复查已解锁列表
        verify(achievements, times(1)).selectList(any());
    }

    @Test
    void progressIsCappedAtThreshold() {
        when(achievements.loadMetrics(11L)).thenReturn(metrics(Map.of("blogCount", 50)));
        when(achievements.selectList(any())).thenReturn(List.of());
        when(achievements.insertIgnore(anyLong(), anyLong(), anyString(), anyInt())).thenReturn(0);
        when(achievements.selectRanking(2L, 50)).thenReturn(List.of());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) service.evaluate(11L, 2L).get("items");

        Map<String, Object> blog10 = items.stream()
                .filter(i -> "blog_10".equals(i.get("code"))).findFirst().orElseThrow();
        assertEquals(10, blog10.get("progress"));
        assertEquals(10, blog10.get("threshold"));
    }

    @Test
    void summaryCarriesLevelAndNextLevelScore() {
        FamilyAchievement a = unlocked("points_5000", 100);
        FamilyAchievement b = unlocked("blog_10", 40);
        when(achievements.loadMetrics(11L)).thenReturn(metrics(Map.of("pointsEarned", 6000, "blogCount", 12)));
        when(achievements.selectList(any())).thenReturn(List.of(a, b));
        when(achievements.selectRanking(2L, 50)).thenReturn(List.of());

        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) service.evaluate(11L, 2L).get("summary");

        assertEquals(140, summary.get("score"));
        assertEquals(2, summary.get("level"));
        assertEquals(250, summary.get("nextLevelScore"));
        assertEquals(AchievementCatalog.ACHIEVEMENTS.size(), summary.get("total"));
    }

    @Test
    void rankingIsPassedThroughWithFamilyScope() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("userId", 11L);
        row.put("name", "小饼干");
        row.put("score", 140);
        when(achievements.loadMetrics(11L)).thenReturn(metrics(Map.of()));
        when(achievements.selectList(any())).thenReturn(List.of());
        when(achievements.selectRanking(2L, 50)).thenReturn(List.of(row));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> ranking =
                (List<Map<String, Object>>) service.evaluate(11L, 2L).get("ranking");

        assertEquals(1, ranking.size());
        assertEquals(11L, ranking.get(0).get("userId"));
    }

    @Test
    void recordGameNormalizesRuleCodePrefix() {
        service.recordGame(11L, 2L, "game_mahjong", AchievementCatalog.SOURCE_ROOM, AchievementCatalog.RESULT_WIN);

        ArgumentCaptor<FamilyGameRecord> captor = ArgumentCaptor.forClass(FamilyGameRecord.class);
        verify(gameRecords).insert(captor.capture());
        assertEquals("mahjong", captor.getValue().getGameCode());
        assertEquals(AchievementCatalog.SOURCE_ROOM, captor.getValue().getSource());
        assertEquals(AchievementCatalog.RESULT_WIN, captor.getValue().getResult());
        assertEquals(11L, captor.getValue().getUserId());
        assertEquals(2L, captor.getValue().getFamilyId());
    }

    @Test
    void recordGameSkipsIncompleteCallers() {
        service.recordGame(null, 2L, "mahjong", AchievementCatalog.SOURCE_SOLO, AchievementCatalog.RESULT_FINISH);
        service.recordGame(11L, null, "mahjong", AchievementCatalog.SOURCE_SOLO, AchievementCatalog.RESULT_FINISH);
        service.recordGame(11L, 2L, " ", AchievementCatalog.SOURCE_SOLO, AchievementCatalog.RESULT_FINISH);

        verifyNoInteractions(gameRecords);
    }

    private static Map<String, Object> metrics(Map<String, Object> values) {
        return new LinkedHashMap<>(values);
    }

    private static FamilyAchievement unlocked(String code, int score) {
        FamilyAchievement row = new FamilyAchievement();
        row.setUserId(11L);
        row.setFamilyId(2L);
        row.setCode(code);
        row.setScore(score);
        row.setUnlockedAt(LocalDateTime.now());
        return row;
    }
}
