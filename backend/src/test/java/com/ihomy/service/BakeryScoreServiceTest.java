package com.ihomy.service;

import com.ihomy.common.BizException;
import com.ihomy.mapper.GameBakeryScoreMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 小面包房家庭榜:上报覆盖、范围校验、榜内名次/我那一行、家庭隔离参数传递。
 */
class BakeryScoreServiceTest {

    private final GameBakeryScoreMapper scores = mock(GameBakeryScoreMapper.class);
    private final BakeryScoreService service = new BakeryScoreService(scores);

    @Test
    void submitInsertsOrOverwritesOwnRow() {
        when(scores.upsert(2L, 11L, 1234L)).thenReturn(1);

        Map<String, Object> out = service.submit(2L, 11L, 1234L);

        assertEquals(1234L, out.get("score"));
        assertEquals(true, out.get("saved"));
        verify(scores).upsert(2L, 11L, 1234L);
    }

    @Test
    void submitRejectsMissingFamily() {
        assertThrows(BizException.class, () -> service.submit(null, 11L, 1L));
        verifyNoInteractions(scores);
    }

    @Test
    void submitRejectsOutOfRangeScore() {
        assertThrows(BizException.class, () -> service.submit(2L, 11L, -1L));
        assertThrows(BizException.class, () -> service.submit(2L, 11L, 1_000_000_000_000_001L));
        assertThrows(BizException.class, () -> service.submit(2L, 11L, null));
        verifyNoInteractions(scores);
    }

    @Test
    void rankingMarksRankAndMe() {
        when(scores.selectRanking(2L, 20)).thenReturn(List.of(row(9L, 5000), row(11L, 3000), row(12L, 1000)));

        Map<String, Object> out = service.ranking(2L, 11L);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> ranking = (List<Map<String, Object>>) out.get("ranking");
        assertEquals(3, ranking.size());
        assertEquals(1, ranking.get(0).get("rank"));
        assertEquals(2, ranking.get(1).get("rank"));
        assertEquals(true, ranking.get(1).get("isMe"));
        assertEquals(false, ranking.get(0).get("isMe"));
        @SuppressWarnings("unchecked")
        Map<String, Object> me = (Map<String, Object>) out.get("me");
        assertEquals(2, me.get("rank"));
        assertEquals(3000L, me.get("score"));
        verify(scores, never()).selectMyRow(anyLong(), anyLong());
    }

    @Test
    void rankingFallsBackToMyRowWhenOutsideTop() {
        when(scores.selectRanking(2L, 20)).thenReturn(List.of(row(9L, 5000)));
        when(scores.selectMyRow(2L, 11L)).thenReturn(row(11L, 10));

        Map<String, Object> out = service.ranking(2L, 11L);

        @SuppressWarnings("unchecked")
        Map<String, Object> me = (Map<String, Object>) out.get("me");
        assertNotNull(me);
        assertNull(me.get("rank"));
        assertEquals(true, me.get("isMe"));
    }

    @Test
    void rankingIsEmptyWithoutFamily() {
        Map<String, Object> out = service.ranking(null, 11L);

        assertEquals(List.of(), out.get("ranking"));
        assertNull(out.get("me"));
        verifyNoInteractions(scores);
    }

    /** 榜单查询只用调用者自己的家庭,不做任何跨家庭合并 */
    @Test
    void rankingQueriesOnlyCallerFamily() {
        when(scores.selectRanking(2L, 20)).thenReturn(List.of());
        when(scores.selectMyRow(2L, 11L)).thenReturn(null);

        service.ranking(2L, 11L);

        verify(scores, times(1)).selectRanking(2L, 20);
        verify(scores, never()).selectRanking(eq(3L), anyInt());
    }

    private static Map<String, Object> row(long userId, long score) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("userId", userId);
        row.put("score", score);
        row.put("name", "成员" + userId);
        row.put("avatar", null);
        return row;
    }
}
