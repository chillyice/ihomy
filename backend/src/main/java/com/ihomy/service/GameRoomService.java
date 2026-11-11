package com.ihomy.service;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ihomy.common.BizException;
import com.ihomy.common.GameConst;
import com.ihomy.common.PointsRuleConst;
import com.ihomy.common.ResultCode;
import com.ihomy.entity.GameRoom;
import com.ihomy.entity.GameRoomMember;
import com.ihomy.entity.SysUser;
import com.ihomy.mapper.GameRoomMapper;
import com.ihomy.mapper.GameRoomMemberMapper;
import com.ihomy.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 家庭「一起玩」房间:建房/加入/准备/开赛/上报/结算。
 * 与旧的 family_game_match(固定 1v1)并存:2048/贪吃蛇的单挑入口继续走旧表,
 * 新的「一起玩」统一走房间(2~8 人,PK/COOP)。实时对局状态由房主经 WS 广播,服务端不落盘。
 * 结算:PK 按游戏口径(先完成/高分)判胜者并发积分;COOP 只记团队完成,不发积分。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GameRoomService {

    private final GameRoomMapper roomMapper;
    private final GameRoomMemberMapper memberMapper;
    private final SysUserMapper sysUserMapper;
    private final PointsService pointsService;
    private final StringRedisTemplate redis;

    /** 创建房间:发起人即房主,并自动准备 */
    public Map<String, Object> create(Long userId, Long familyId, String gameCode, String mode, Integer maxPlayers) {
        GameConst.RoomGame spec = GameConst.ROOM_GAMES.get(gameCode);
        if (spec == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "这个游戏还不支持一起玩");
        }
        if (!spec.modes().contains(mode)) {
            throw new BizException(ResultCode.BAD_REQUEST, "这个游戏不支持这种玩法");
        }
        int max = maxPlayers == null ? GameConst.ROOM_DEFAULT_PLAYERS : maxPlayers;
        max = Math.max(2, Math.min(GameConst.ROOM_MAX_PLAYERS, max));
        GameRoom room = new GameRoom();
        room.setFamilyId(familyId);
        room.setGameCode(gameCode);
        room.setMode(mode);
        room.setSeed(Math.abs(RandomUtil.randomLong()));
        room.setHostId(userId);
        room.setMaxPlayers(max);
        room.setStatus(GameConst.ROOM_WAITING);
        roomMapper.insert(room);
        addMember(room, userId, "HOST");
        return view(room);
    }

    /** 家庭房间列表(最近在前,≤20 条) */
    public List<Map<String, Object>> list(Long familyId, String status, int limit) {
        List<GameRoom> rows = roomMapper.selectList(new LambdaQueryWrapper<GameRoom>()
                .eq(GameRoom::getFamilyId, familyId)
                .eq(status != null && !status.isBlank(), GameRoom::getStatus, status)
                .orderByDesc(GameRoom::getId)
                .last("LIMIT " + Math.min(Math.max(limit, 1), 20)));
        return views(rows);
    }

    /** 房间详情(家庭隔离:跨家庭一律 NOT_FOUND) */
    public Map<String, Object> get(Long familyId, Long id) {
        return view(require(familyId, id));
    }

    /** 加入房间:仅 WAITING;已在房间内幂等返回 */
    public Map<String, Object> join(Long userId, Long familyId, Long id) {
        synchronized (("game-room-" + id).intern()) {
            GameRoom room = require(familyId, id);
            if (!GameConst.ROOM_WAITING.equals(room.getStatus())) {
                throw new BizException(ResultCode.BAD_REQUEST, "这个房间已经不能加入了");
            }
            if (memberOf(id, userId) != null) {
                return view(room);
            }
            long count = memberMapper.selectCount(new LambdaQueryWrapper<GameRoomMember>()
                    .eq(GameRoomMember::getRoomId, id));
            if (count >= room.getMaxPlayers()) {
                throw new BizException(ResultCode.BAD_REQUEST, "房间已经满员了");
            }
            addMember(room, userId, "PLAYER");
            return view(room);
        }
    }

    /** 准备/取消准备:仅房主之外成员可切换(房主默认已准备) */
    public Map<String, Object> ready(Long userId, Long familyId, Long id, Boolean ready) {
        GameRoom room = require(familyId, id);
        if (!GameConst.ROOM_WAITING.equals(room.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST, "对局已经开始");
        }
        GameRoomMember member = requireMember(id, userId);
        if ("HOST".equals(member.getRole())) {
            return view(room);
        }
        memberMapper.update(null, new LambdaUpdateWrapper<GameRoomMember>()
                .eq(GameRoomMember::getId, member.getId())
                .set(GameRoomMember::getReady, Boolean.TRUE.equals(ready) ? 1 : 0));
        return view(room);
    }

    /** 房主开赛:至少 2 人且全部准备 */
    public Map<String, Object> start(Long userId, Long familyId, Long id) {
        GameRoom room = require(familyId, id);
        if (!Objects.equals(room.getHostId(), userId)) {
            throw new BizException(ResultCode.BAD_REQUEST, "只有房主可以开始游戏");
        }
        if (!GameConst.ROOM_WAITING.equals(room.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST, "这个房间已经开过了");
        }
        List<GameRoomMember> members = members(id);
        if (members.size() < 2) {
            throw new BizException(ResultCode.BAD_REQUEST, "至少要有两个人才能开始");
        }
        boolean allReady = members.stream().allMatch(m -> Integer.valueOf(1).equals(m.getReady()));
        if (!allReady) {
            throw new BizException(ResultCode.BAD_REQUEST, "还有成员没有准备好");
        }
        int updated = roomMapper.update(null, new LambdaUpdateWrapper<GameRoom>()
                .eq(GameRoom::getId, id)
                .eq(GameRoom::getStatus, GameConst.ROOM_WAITING)
                .set(GameRoom::getStatus, GameConst.ROOM_PLAYING)
                .set(GameRoom::getStartedAt, LocalDateTime.now()));
        if (updated == 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "这个房间已经开过了");
        }
        return get(familyId, id);
    }

    /**
     * 上报成绩(幂等):首报生效。
     * PK 先完成制:第一个上报的人直接结束房间并判胜;
     * PK 高分制:所有人上报完才结算,房主可提前结束;
     * COOP:仅房主上报完成,房间结束,不判胜负。
     */
    public Map<String, Object> finish(Long userId, Long familyId, Long id, long score) {
        GameRoom room = require(familyId, id);
        if (!GameConst.ROOM_PLAYING.equals(room.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST, "对局还没有开始");
        }
        GameRoomMember member = requireMember(id, userId);
        boolean firstReport = memberMapper.update(null, new LambdaUpdateWrapper<GameRoomMember>()
                .eq(GameRoomMember::getId, member.getId())
                .isNull(GameRoomMember::getFinishedAt)
                .set(GameRoomMember::getScore, Math.max(0, score))
                .set(GameRoomMember::getFinishedAt, LocalDateTime.now())) > 0;
        if (!firstReport) {
            return get(familyId, id);
        }

        GameConst.RoomGame spec = GameConst.ROOM_GAMES.get(room.getGameCode());
        String result = spec == null ? GameConst.ROOM_RESULT_SCORE : spec.result();
        if (GameConst.ROOM_RESULT_COOP.equals(result)) {
            if (!Objects.equals(room.getHostId(), userId)) {
                return get(familyId, id);
            }
            finishRoom(room, null);
            return get(familyId, id);
        }
        if (GameConst.ROOM_RESULT_FIRST.equals(result)) {
            GameRoom finished = finishFirst(room, userId);
            if (finished != null && finished.getWinnerId() != null) {
                awardWinner(finished, finished.getWinnerId());
            }
            return get(familyId, id);
        }
        // 高分制:所有成员都上报后自动结算
        List<GameRoomMember> members = members(id);
        boolean allReported = members.stream().allMatch(m -> m.getFinishedAt() != null);
        if (allReported) {
            GameRoom finished = finishByScore(room);
            if (finished != null && finished.getWinnerId() != null) {
                awardWinner(finished, finished.getWinnerId());
            }
        }
        return get(familyId, id);
    }

    /** 房主结束:WAITING→CANCELLED;PLAYING→FINISHED(高分制按当前成绩判胜) */
    public Map<String, Object> end(Long userId, Long familyId, Long id) {
        GameRoom room = require(familyId, id);
        if (!Objects.equals(room.getHostId(), userId)) {
            throw new BizException(ResultCode.BAD_REQUEST, "只有房主可以结束房间");
        }
        if (GameConst.ROOM_WAITING.equals(room.getStatus())) {
            roomMapper.update(null, new LambdaUpdateWrapper<GameRoom>()
                    .eq(GameRoom::getId, id)
                    .eq(GameRoom::getStatus, GameConst.ROOM_WAITING)
                    .set(GameRoom::getStatus, GameConst.ROOM_CANCELLED)
                    .set(GameRoom::getFinishedAt, LocalDateTime.now()));
            return get(familyId, id);
        }
        if (GameConst.ROOM_PLAYING.equals(room.getStatus())) {
            GameConst.RoomGame spec = GameConst.ROOM_GAMES.get(room.getGameCode());
            String result = spec == null ? GameConst.ROOM_RESULT_SCORE : spec.result();
            GameRoom finished = GameConst.ROOM_RESULT_SCORE.equals(result) ? finishByScore(room) : finishRoom(room, null);
            if (finished != null && finished.getWinnerId() != null) {
                awardWinner(finished, finished.getWinnerId());
            }
        }
        return get(familyId, id);
    }

    /** 离开房间:未开赛直接移除;房主离开则结束/取消房间 */
    public Map<String, Object> leave(Long userId, Long familyId, Long id) {
        GameRoom room = require(familyId, id);
        GameRoomMember member = memberOf(id, userId);
        if (member == null) {
            return view(room);
        }
        boolean isHost = Objects.equals(room.getHostId(), userId);
        if (GameConst.ROOM_WAITING.equals(room.getStatus())) {
            memberMapper.deleteById(member.getId());
            if (isHost) {
                roomMapper.update(null, new LambdaUpdateWrapper<GameRoom>()
                        .eq(GameRoom::getId, id)
                        .eq(GameRoom::getStatus, GameConst.ROOM_WAITING)
                        .set(GameRoom::getStatus, GameConst.ROOM_CANCELLED)
                        .set(GameRoom::getFinishedAt, LocalDateTime.now()));
            }
        } else if (GameConst.ROOM_PLAYING.equals(room.getStatus())) {
            memberMapper.deleteById(member.getId());
            if (isHost) {
                GameConst.RoomGame spec = GameConst.ROOM_GAMES.get(room.getGameCode());
                String result = spec == null ? GameConst.ROOM_RESULT_SCORE : spec.result();
                GameRoom finished = GameConst.ROOM_RESULT_SCORE.equals(result) ? finishByScore(room) : finishRoom(room, null);
                if (finished != null && finished.getWinnerId() != null) {
                    awardWinner(finished, finished.getWinnerId());
                }
            }
        }
        return get(familyId, id);
    }

    /** 房间成员 userId 列表(WS 定向广播用) */
    public List<Long> memberIds(Long familyId, Long id) {
        require(familyId, id);
        return members(id).stream().map(GameRoomMember::getUserId).collect(Collectors.toList());
    }

    /** 参与者校验(WS game-signal 用) */
    public boolean isMember(Long familyId, Long id, Long userId) {
        GameRoom room = roomMapper.selectOne(new LambdaQueryWrapper<GameRoom>()
                .eq(GameRoom::getFamilyId, familyId)
                .eq(GameRoom::getId, id));
        if (room == null) {
            return false;
        }
        return memberOf(id, userId) != null;
    }

    // ---------- 结算 ----------

    private GameRoom finishFirst(GameRoom room, Long winnerId) {
        int updated = roomMapper.update(null, new LambdaUpdateWrapper<GameRoom>()
                .eq(GameRoom::getId, room.getId())
                .eq(GameRoom::getStatus, GameConst.ROOM_PLAYING)
                .set(GameRoom::getStatus, GameConst.ROOM_FINISHED)
                .set(GameRoom::getWinnerId, winnerId)
                .set(GameRoom::getFinishedAt, LocalDateTime.now()));
        return updated > 0 ? roomMapper.selectById(room.getId()) : null;
    }

    private GameRoom finishByScore(GameRoom room) {
        List<GameRoomMember> members = members(room.getId()).stream()
                .filter(m -> m.getScore() != null)
                .sorted(Comparator
                        .comparing(GameRoomMember::getScore, Comparator.reverseOrder())
                        .thenComparing(GameRoomMember::getFinishedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        Long winnerId = null;
        if (members.size() == 1) {
            winnerId = members.get(0).getUserId();
        } else if (members.size() > 1) {
            GameRoomMember top = members.get(0);
            GameRoomMember second = members.get(1);
            boolean sameScore = Objects.equals(top.getScore(), second.getScore());
            if (!sameScore) {
                winnerId = top.getUserId();
            }
        }
        int updated = roomMapper.update(null, new LambdaUpdateWrapper<GameRoom>()
                .eq(GameRoom::getId, room.getId())
                .eq(GameRoom::getStatus, GameConst.ROOM_PLAYING)
                .set(GameRoom::getStatus, GameConst.ROOM_FINISHED)
                .set(GameRoom::getWinnerId, winnerId)
                .set(GameRoom::getFinishedAt, LocalDateTime.now()));
        return updated > 0 ? roomMapper.selectById(room.getId()) : null;
    }

    private GameRoom finishRoom(GameRoom room, Long winnerId) {
        int updated = roomMapper.update(null, new LambdaUpdateWrapper<GameRoom>()
                .eq(GameRoom::getId, room.getId())
                .eq(GameRoom::getStatus, GameConst.ROOM_PLAYING)
                .set(GameRoom::getStatus, GameConst.ROOM_FINISHED)
                .set(GameRoom::getWinnerId, winnerId)
                .set(GameRoom::getFinishedAt, LocalDateTime.now()));
        return updated > 0 ? roomMapper.selectById(room.getId()) : null;
    }

    /** 胜者积分:同一游戏同一人每日上限(防互刷,超限对局照打积分不发) */
    private void awardWinner(GameRoom room, Long winnerId) {
        String key = "ihomy:game:room:" + room.getGameCode() + ":" + winnerId + ":" + LocalDate.now();
        long count;
        try {
            count = redis.opsForValue().increment(key);
            if (count == 1) {
                redis.expire(key, Duration.ofDays(2));
            }
        } catch (Exception e) {
            log.warn("房间计分计数失败,按首日处理 key={}", key, e);
            count = 1;
        }
        if (count > GameConst.MATCH_DAILY_SCORE_LIMIT) {
            return;
        }
        int awarded = pointsService.rulePoints(room.getFamilyId(), PointsRuleConst.GAME_MATCH_WIN);
        if (awarded > 0) {
            String name = GameConst.ROOM_GAME_NAMES.getOrDefault(room.getGameCode(), room.getGameCode());
            pointsService.addRecord(winnerId, room.getFamilyId(), "REWARD", awarded, "一起玩获胜(" + name + ")");
        }
    }

    // ---------- 基础查询 ----------

    private void addMember(GameRoom room, Long userId, String role) {
        GameRoomMember m = new GameRoomMember();
        m.setRoomId(room.getId());
        m.setFamilyId(room.getFamilyId());
        m.setUserId(userId);
        m.setRole(role);
        m.setReady("HOST".equals(role) ? 1 : 0);
        memberMapper.insert(m);
    }

    private GameRoom require(Long familyId, Long id) {
        GameRoom room = roomMapper.selectOne(new LambdaQueryWrapper<GameRoom>()
                .eq(GameRoom::getFamilyId, familyId)
                .eq(GameRoom::getId, id));
        if (room == null) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        return room;
    }

    private GameRoomMember memberOf(Long roomId, Long userId) {
        return memberMapper.selectOne(new LambdaQueryWrapper<GameRoomMember>()
                .eq(GameRoomMember::getRoomId, roomId)
                .eq(GameRoomMember::getUserId, userId)
                .last("LIMIT 1"));
    }

    private GameRoomMember requireMember(Long roomId, Long userId) {
        GameRoomMember member = memberOf(roomId, userId);
        if (member == null) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        return member;
    }

    private List<GameRoomMember> members(Long roomId) {
        return memberMapper.selectList(new LambdaQueryWrapper<GameRoomMember>()
                .eq(GameRoomMember::getRoomId, roomId)
                .orderByAsc(GameRoomMember::getId));
    }

    private Map<String, Object> view(GameRoom room) {
        return views(List.of(room)).get(0);
    }

    private List<Map<String, Object>> views(List<GameRoom> rooms) {
        if (rooms.isEmpty()) {
            return List.of();
        }
        List<Long> roomIds = rooms.stream().map(GameRoom::getId).collect(Collectors.toList());
        List<GameRoomMember> allMembers = memberMapper.selectList(new LambdaQueryWrapper<GameRoomMember>()
                .in(GameRoomMember::getRoomId, roomIds)
                .orderByAsc(GameRoomMember::getId));
        List<Long> userIds = java.util.stream.Stream.concat(
                        rooms.stream().map(GameRoom::getHostId),
                        allMembers.stream().map(GameRoomMember::getUserId))
                .filter(Objects::nonNull).distinct().collect(Collectors.toList());
        Map<Long, String> names = userIds.isEmpty() ? Map.of()
                : sysUserMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(SysUser::getId,
                                u -> u.getNickname() != null ? u.getNickname() : u.getUsername()));
        Map<Long, List<GameRoomMember>> byRoom = allMembers.stream()
                .collect(Collectors.groupingBy(GameRoomMember::getRoomId));

        List<Map<String, Object>> out = new ArrayList<>();
        for (GameRoom room : rooms) {
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("id", room.getId());
            v.put("gameCode", room.getGameCode());
            v.put("gameName", GameConst.ROOM_GAME_NAMES.getOrDefault(room.getGameCode(), room.getGameCode()));
            v.put("mode", room.getMode());
            v.put("seed", room.getSeed());
            v.put("status", room.getStatus());
            v.put("hostId", room.getHostId());
            v.put("hostName", names.getOrDefault(room.getHostId(), "成员"));
            v.put("maxPlayers", room.getMaxPlayers());
            v.put("winnerId", room.getWinnerId());
            v.put("startedAt", room.getStartedAt());
            v.put("finishedAt", room.getFinishedAt());
            v.put("createdAt", room.getCreatedAt());
            List<Map<String, Object>> members = new ArrayList<>();
            for (GameRoomMember m : byRoom.getOrDefault(room.getId(), List.of())) {
                Map<String, Object> mv = new LinkedHashMap<>();
                mv.put("userId", m.getUserId());
                mv.put("name", names.getOrDefault(m.getUserId(), "成员"));
                mv.put("role", m.getRole());
                mv.put("ready", Integer.valueOf(1).equals(m.getReady()));
                mv.put("score", m.getScore());
                mv.put("finishedAt", m.getFinishedAt());
                members.add(mv);
            }
            v.put("members", members);
            v.put("readyCount", members.stream().filter(m -> Boolean.TRUE.equals(m.get("ready"))).count());
            out.add(v);
        }
        return out;
    }
}
