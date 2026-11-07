package com.ihomy.service;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ihomy.common.BizException;
import com.ihomy.common.GameConst;
import com.ihomy.common.PointsRuleConst;
import com.ihomy.common.ResultCode;
import com.ihomy.entity.GameMatch;
import com.ihomy.entity.SysUser;
import com.ihomy.mapper.GameMatchMapper;
import com.ihomy.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 家庭游戏对战:建赛/加入/取消/结算(幂等)。同种子赛(2048)与实时对战(贪吃蛇)共用一张
 * family_game_match;比分列 NULL=未上报、数字=已上报,同一用户重复 finish 不会覆盖首报成绩。
 * 双方都报完 → 判胜者并广播结果卡;胜者积分走 family_points_rule(game_match_win),
 * 同一对手同游戏每日计分上限防互刷(超限比赛照打,积分不发)。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GameMatchService {

    private final GameMatchMapper matchMapper;
    private final SysUserMapper sysUserMapper;
    private final PointsService pointsService;
    private final StringRedisTemplate redis;

    /** 创建对战(发起人即 host):同种子赛/实时对战统一由服务端生成随机种子 */
    public Map<String, Object> create(Long userId, Long familyId, String gameCode, String mode) {
        if (!GameConst.MATCH_GAME_CODES.contains(gameCode)) {
            throw new BizException(ResultCode.BAD_REQUEST, "这个游戏还不支持对战");
        }
        if (!GameConst.MATCH_MODE_ASYNC.equals(mode) && !GameConst.MATCH_MODE_REALTIME.equals(mode)) {
            throw new BizException(ResultCode.BAD_REQUEST, "对战方式不正确");
        }
        GameMatch m = new GameMatch();
        m.setFamilyId(familyId);
        m.setGameCode(gameCode);
        m.setMode(mode);
        m.setSeed(Math.abs(RandomUtil.randomLong()));
        m.setHostId(userId);
        m.setStatus(GameConst.MATCH_WAITING);
        matchMapper.insert(m);
        return view(m);
    }

    /** 家庭对战列表(最近在前,≤20 条) */
    public List<Map<String, Object>> list(Long familyId, String status, int limit) {
        List<GameMatch> rows = matchMapper.selectList(new LambdaQueryWrapper<GameMatch>()
                .eq(GameMatch::getFamilyId, familyId)
                .eq(status != null && !status.isBlank(), GameMatch::getStatus, status)
                .orderByDesc(GameMatch::getId)
                .last("LIMIT " + Math.min(Math.max(limit, 1), 20)));
        return views(rows);
    }

    /** 单场对战(家庭隔离:跨家庭一律 NOT_FOUND) */
    public Map<String, Object> get(Long familyId, Long id) {
        return view(require(familyId, id));
    }

    /** 对手加入:WAITING → PLAYING */
    public Map<String, Object> join(Long userId, Long familyId, Long id) {
        GameMatch m = require(familyId, id);
        if (!GameConst.MATCH_WAITING.equals(m.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST, "这场对战已经不能加入了");
        }
        if (userId.equals(m.getHostId())) {
            throw new BizException(ResultCode.BAD_REQUEST, "不能加入自己发起的对战");
        }
        // 条件更新抢占唯一对手位(并发 join 只有一个成功)
        boolean ok = matchMapper.update(null, new LambdaUpdateWrapper<GameMatch>()
                .eq(GameMatch::getId, id)
                .eq(GameMatch::getStatus, GameConst.MATCH_WAITING)
                .isNull(GameMatch::getGuestId)
                .set(GameMatch::getGuestId, userId)
                .set(GameMatch::getStatus, GameConst.MATCH_PLAYING)) > 0;
        if (!ok) {
            throw new BizException(ResultCode.BAD_REQUEST, "这场对战已经有人加入了");
        }
        return get(familyId, id);
    }

    /** 发起人取消未开赛的对战 */
    public Map<String, Object> cancel(Long userId, Long familyId, Long id) {
        GameMatch m = require(familyId, id);
        if (!userId.equals(m.getHostId()) || !GameConst.MATCH_WAITING.equals(m.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST, "只能取消自己发起且还没开赛的对战");
        }
        matchMapper.update(null, new LambdaUpdateWrapper<GameMatch>()
                .eq(GameMatch::getId, id)
                .eq(GameMatch::getStatus, GameConst.MATCH_WAITING)
                .set(GameMatch::getStatus, GameConst.MATCH_CANCELLED));
        return get(familyId, id);
    }

    /**
     * 上报成绩(幂等):首报生效、重复上报不覆盖;双方都报完即判胜者并发积分。
     * 返回 {match, finished, awarded}:finished=本次调用是否促成终局(供控制器发结果卡)。
     */
    public Map<String, Object> finish(Long userId, Long familyId, Long id, long score) {
        GameMatch m = require(familyId, id);
        if (!userId.equals(m.getHostId()) && !userId.equals(m.getGuestId())) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        boolean isHost = userId.equals(m.getHostId());
        // 条件更新:该侧成绩为 NULL 才写入(原子,并发重复上报只有一个生效)
        LambdaUpdateWrapper<GameMatch> uw = new LambdaUpdateWrapper<GameMatch>().eq(GameMatch::getId, id);
        if (isHost) {
            uw.isNull(GameMatch::getHostScore).set(GameMatch::getHostScore, Math.max(0, score));
        } else {
            uw.isNull(GameMatch::getGuestScore).set(GameMatch::getGuestScore, Math.max(0, score));
        }
        boolean firstReport = matchMapper.update(null, uw) > 0;
        if (!firstReport) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("match", get(familyId, id));
            r.put("finished", false);
            r.put("awarded", 0);
            return r;
        }
        GameMatch after = matchMapper.selectById(id);
        boolean bothReported = after.getHostScore() != null && after.getGuestScore() != null;
        boolean finishedNow = false;
        long awarded = 0;
        if (bothReported) {
            Long winner = after.getHostScore().equals(after.getGuestScore()) ? null
                    : (after.getHostScore() > after.getGuestScore() ? after.getHostId() : after.getGuestId());
            // 终局转换唯一:仍为 PLAYING 的那个更新生效,胜者积分随之只发一次
            finishedNow = matchMapper.update(null, new LambdaUpdateWrapper<GameMatch>()
                    .eq(GameMatch::getId, id)
                    .eq(GameMatch::getStatus, GameConst.MATCH_PLAYING)
                    .set(GameMatch::getStatus, GameConst.MATCH_FINISHED)
                    .set(GameMatch::getWinnerId, winner)) > 0;
            if (finishedNow && winner != null) {
                awarded = awardWinner(after, winner);
            }
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("match", get(familyId, id));
        r.put("finished", finishedNow);
        r.put("awarded", awarded);
        return r;
    }

    /** 胜者积分:同一对手同游戏每日上限(GameConst.MATCH_DAILY_SCORE_LIMIT),超限不发仍算胜 */
    private long awardWinner(GameMatch m, Long winnerId) {
        String pair = m.getHostId() < m.getGuestId()
                ? m.getHostId() + "-" + m.getGuestId()
                : m.getGuestId() + "-" + m.getHostId();
        String key = "ihomy:game:match:" + m.getGameCode() + ":" + pair + ":" + LocalDate.now();
        long count;
        try {
            count = redis.opsForValue().increment(key);
            if (count == 1) {
                redis.expire(key, Duration.ofDays(2));
            }
        } catch (Exception e) {
            log.warn("对战计分计数失败,按首日处理 key={}", key, e);
            count = 1;
        }
        if (count > GameConst.MATCH_DAILY_SCORE_LIMIT) {
            return 0;
        }
        int awarded = pointsService.rulePoints(m.getFamilyId(), PointsRuleConst.GAME_MATCH_WIN);
        if (awarded > 0) {
            pointsService.addRecord(winnerId, m.getFamilyId(), "REWARD", awarded, "家庭对战获胜(" + m.getGameCode() + ")");
        }
        return awarded;
    }

    private GameMatch require(Long familyId, Long id) {
        GameMatch m = matchMapper.selectOne(new LambdaQueryWrapper<GameMatch>()
                .eq(GameMatch::getFamilyId, familyId)
                .eq(GameMatch::getId, id));
        if (m == null) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        return m;
    }

    /** 参与中的对局(WS game-signal 转发用):非参与者/不存在返回 null */
    public GameMatch participantMatch(Long userId, Long familyId, Long matchId) {
        GameMatch m = matchMapper.selectOne(new LambdaQueryWrapper<GameMatch>()
                .eq(GameMatch::getFamilyId, familyId)
                .eq(GameMatch::getId, matchId));
        if (m == null || (!userId.equals(m.getHostId()) && !userId.equals(m.getGuestId()))) {
            return null;
        }
        return m;
    }

    /** 视图(带双方昵称;比分未报为 null 原样输出) */
    private Map<String, Object> view(GameMatch m) {
        return views(List.of(m)).get(0);
    }

    private List<Map<String, Object>> views(List<GameMatch> rows) {
        List<Long> ids = rows.stream()
                .flatMap(m -> java.util.stream.Stream.of(m.getHostId(), m.getGuestId()))
                .filter(java.util.Objects::nonNull).distinct().collect(Collectors.toList());
        Map<Long, String> names = ids.isEmpty() ? Map.of()
                : sysUserMapper.selectBatchIds(ids).stream()
                        .collect(Collectors.toMap(SysUser::getId, u -> u.getNickname() != null ? u.getNickname() : u.getUsername()));
        List<Map<String, Object>> out = new ArrayList<>();
        for (GameMatch m : rows) {
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("id", m.getId());
            v.put("gameCode", m.getGameCode());
            v.put("mode", m.getMode());
            v.put("seed", m.getSeed());
            v.put("status", m.getStatus());
            v.put("hostId", m.getHostId());
            v.put("hostName", names.getOrDefault(m.getHostId(), "成员"));
            v.put("guestId", m.getGuestId());
            v.put("guestName", m.getGuestId() == null ? null : names.getOrDefault(m.getGuestId(), "成员"));
            v.put("hostScore", m.getHostScore());
            v.put("guestScore", m.getGuestScore());
            v.put("winnerId", m.getWinnerId());
            v.put("createdAt", m.getCreatedAt());
            out.add(v);
        }
        return out;
    }
}
