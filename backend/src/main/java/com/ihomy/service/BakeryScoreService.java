package com.ihomy.service;

import com.ihomy.common.BizException;
import com.ihomy.common.ResultCode;
import com.ihomy.mapper.GameBakeryScoreMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 小面包房家庭榜:单人进度存本机,只有「当前净资产」一个数字上云用于家庭内比较。
 * 上报是覆盖更新(净值会因升级花费回落),查询只听 JWT 里的家庭,跨家庭永远查不到别人。
 */
@Service
@RequiredArgsConstructor
public class BakeryScoreService {

    /** 榜单返回条数(家庭人数通常远小于此,留足余量) */
    private static final int RANKING_LIMIT = 20;
    /** 与 DTO 上限一致:超出视为异常请求而不是合法成绩 */
    private static final long MAX_SCORE = 1_000_000_000_000_000L;

    private final GameBakeryScoreMapper scoreMapper;

    /**
     * 上报本人净资产:首次上榜插入,之后覆盖。
     * 家庭内互信场景,只做范围校验与家庭隔离,不做产线回放校验。
     */
    public Map<String, Object> submit(Long familyId, Long userId, Long score) {
        if (familyId == null) {
            throw new BizException(ResultCode.BAD_REQUEST);
        }
        if (score == null || score < 0 || score > MAX_SCORE) {
            throw new BizException(ResultCode.BAD_REQUEST);
        }
        int rows = scoreMapper.upsert(familyId, userId, score);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("score", score);
        out.put("saved", rows > 0);
        return out;
    }

    /**
     * 家庭净资产榜:前 N 名(只在榜内标名次)+「我」那一行(不在前 N 也返回,名次为 null)。
     * 已退家庭/被移除的成员不会出现(查询按 sys_user_role 的当前家庭过滤)。
     */
    public Map<String, Object> ranking(Long familyId, Long userId) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ranking", List.of());
        out.put("me", null);
        if (familyId == null) {
            return out;
        }
        List<Map<String, Object>> rows = scoreMapper.selectRanking(familyId, RANKING_LIMIT);
        List<Map<String, Object>> ranking = new ArrayList<>(rows.size());
        Map<String, Object> me = null;
        int rank = 0;
        for (Map<String, Object> row : rows) {
            rank++;
            Map<String, Object> view = new LinkedHashMap<>(row);
            boolean mine = userId != null && userId.equals(asLong(row.get("userId")));
            view.put("rank", rank);
            view.put("isMe", mine);
            ranking.add(view);
            if (mine) {
                me = view;
            }
        }
        if (me == null && userId != null) {
            Map<String, Object> mine = scoreMapper.selectMyRow(familyId, userId);
            if (mine != null) {
                me = new LinkedHashMap<>(mine);
                me.put("rank", null);
                me.put("isMe", true);
            }
        }
        out.put("ranking", ranking);
        out.put("me", me);
        return out;
    }

    private Long asLong(Object value) {
        return value instanceof Number n ? n.longValue() : null;
    }
}
