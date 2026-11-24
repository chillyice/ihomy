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
import com.ihomy.entity.GameRoomRound;
import com.ihomy.entity.SysUser;
import com.ihomy.mapper.GameRoomMapper;
import com.ihomy.mapper.GameRoomMemberMapper;
import com.ihomy.mapper.GameRoomRoundMapper;
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

    /** 换位申请待处理时长,超时自动作废 */
    private static final long SWAP_TTL_MINUTES = 10;

    private final GameRoomMapper roomMapper;
    private final GameRoomMemberMapper memberMapper;
    private final GameRoomRoundMapper roundMapper;
    private final SysUserMapper sysUserMapper;
    private final PointsService pointsService;
    private final StringRedisTemplate redis;
    private final tools.jackson.databind.ObjectMapper objectMapper;

    /** 创建房间:发起人即房主,并自动准备;房间容量固定 8 人,玩家席位按游戏能力分配 */
    public Map<String, Object> create(Long userId, Long familyId, String gameCode, String mode, Integer maxPlayers) {
        GameConst.RoomGame spec = GameConst.ROOM_GAMES.get(gameCode);
        if (spec == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "这个游戏还不支持一起玩");
        }
        String finalMode = mode == null || mode.isBlank() ? defaultMode(spec) : mode;
        if (!spec.modes().contains(finalMode)) {
            throw new BizException(ResultCode.BAD_REQUEST, "这个游戏不支持这种玩法");
        }
        int max = maxPlayers == null ? GameConst.ROOM_MAX_PLAYERS : maxPlayers;
        max = Math.max(2, Math.min(GameConst.ROOM_MAX_PLAYERS, max));
        GameRoom room = new GameRoom();
        room.setFamilyId(familyId);
        room.setGameCode(gameCode);
        room.setMode(finalMode);
        room.setSeed(Math.abs(RandomUtil.randomLong()));
        room.setHostId(userId);
        room.setMaxPlayers(max);
        room.setStatus(GameConst.ROOM_WAITING);
        roomMapper.insert(room);
        addMember(room, userId, GameConst.ROOM_ROLE_HOST);
        return view(room);
    }

    /** 玩法缺省口径:能 PK 就 PK,否则 COOP(与前端 defaultRoomMode 同口径) */
    private String defaultMode(GameConst.RoomGame spec) {
        return spec.modes().contains(GameConst.ROOM_MODE_PK) ? GameConst.ROOM_MODE_PK : GameConst.ROOM_MODE_COOP;
    }

    /**
     * 房主换游戏/玩法(仅 WAITING):
     * 换游戏——按新游戏的玩家席位、按进房顺序重新分配席位(房主恒为玩家,不踢人,超出的转观战),seed 重掷;
     * 只换玩法——不动席位与 seed(避免切 PK⇄COOP 白白打散座位)。
     */
    public Map<String, Object> setGame(Long userId, Long familyId, Long id, String gameCode, String mode) {
        GameRoom room = require(familyId, id);
        if (!Objects.equals(room.getHostId(), userId)) {
            throw new BizException(ResultCode.BAD_REQUEST, "只有房主可以换游戏");
        }
        if (!GameConst.ROOM_WAITING.equals(room.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST, "对局已经开始,下一局再换");
        }
        GameConst.RoomGame spec = GameConst.ROOM_GAMES.get(gameCode);
        if (spec == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "这个游戏还不支持一起玩");
        }
        boolean sameGame = gameCode.equals(room.getGameCode());
        if (sameGame && (mode == null || mode.isBlank() || mode.equals(room.getMode()))) {
            return get(familyId, id);
        }
        String finalMode = mode == null || mode.isBlank() ? (sameGame ? room.getMode() : defaultMode(spec)) : mode;
        if (!spec.modes().contains(finalMode)) {
            throw new BizException(ResultCode.BAD_REQUEST, "这个游戏不支持这种玩法");
        }
        if (sameGame) {
            roomMapper.update(null, new LambdaUpdateWrapper<GameRoom>()
                    .eq(GameRoom::getId, id)
                    .set(GameRoom::getMode, finalMode));
            return get(familyId, id);
        }
        roomMapper.update(null, new LambdaUpdateWrapper<GameRoom>()
                .eq(GameRoom::getId, id)
                .set(GameRoom::getGameCode, gameCode)
                .set(GameRoom::getMode, finalMode)
                .set(GameRoom::getSeed, Math.abs(RandomUtil.randomLong())));
        if (GameConst.GAME_EIGHT_BALL.equals(gameCode)) {
            restoreDuelHost(room);
        }
        assignSeats(id, spec);
        clearSwap(id);
        return get(familyId, id);
    }

    /** 按游戏席位、按进房顺序分配:房主恒为玩家,其余按加入先后先补满玩家席,剩下的转观战;非房主重新准备 */
    private void assignSeats(Long roomId, GameConst.RoomGame spec) {
        List<GameRoomMember> others = members(roomId).stream()
                .filter(m -> !GameConst.ROOM_ROLE_HOST.equals(m.getRole()))
                .collect(Collectors.toList());
        int playerSeats = Math.max(1, spec.maxSeats()) - 1; // 房主已占一席
        for (int i = 0; i < others.size(); i++) {
            GameRoomMember m = others.get(i);
            String role = i < playerSeats ? GameConst.ROOM_ROLE_PLAYER : GameConst.ROOM_ROLE_SPECTATOR;
            memberMapper.update(null, new LambdaUpdateWrapper<GameRoomMember>()
                    .eq(GameRoomMember::getId, m.getId())
                    .set(GameRoomMember::getRole, role)
                    .set(GameRoomMember::getReady, 0));
        }
    }

    /** 房主可能在上一款游戏中让了座，或在上一局结束时已经离开。 */
    private boolean restoreDuelHost(GameRoom room) {
        GameRoomMember host = memberOf(room.getId(), room.getHostId());
        if (host == null) {
            addMember(room, room.getHostId(), GameConst.ROOM_ROLE_HOST);
        } else if (!GameConst.ROOM_ROLE_HOST.equals(host.getRole())) {
            memberMapper.update(null, new LambdaUpdateWrapper<GameRoomMember>()
                    .eq(GameRoomMember::getId, host.getId())
                    .set(GameRoomMember::getRole, GameConst.ROOM_ROLE_HOST)
                    .set(GameRoomMember::getReady, 1));
        } else {
            return false;
        }
        return true;
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

    /** 加入房间:仅 WAITING;已在房间内幂等返回。玩家席满则自动转为观战 */
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
            addMember(room, userId, joinRole(room, id));
            return view(room);
        }
    }

    /** 加入时的席位:游戏玩家席未满则当玩家,满了当观战 */
    private String joinRole(GameRoom room, Long roomId) {
        GameConst.RoomGame spec = GameConst.ROOM_GAMES.get(room.getGameCode());
        if (spec == null) {
            return GameConst.ROOM_ROLE_PLAYER;
        }
        long players = memberMapper.selectCount(new LambdaQueryWrapper<GameRoomMember>()
                .eq(GameRoomMember::getRoomId, roomId)
                .ne(GameRoomMember::getRole, GameConst.ROOM_ROLE_SPECTATOR));
        return players < spec.maxSeats() ? GameConst.ROOM_ROLE_PLAYER : GameConst.ROOM_ROLE_SPECTATOR;
    }

    /** 准备/取消准备:仅房主之外成员可切换(房主默认已准备);观战席没有准备动作 */
    public Map<String, Object> ready(Long userId, Long familyId, Long id, Boolean ready) {
        GameRoom room = require(familyId, id);
        if (!GameConst.ROOM_WAITING.equals(room.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST, "对局已经开始");
        }
        GameRoomMember member = requireMember(id, userId);
        if (GameConst.ROOM_ROLE_HOST.equals(member.getRole())
                || GameConst.ROOM_ROLE_SPECTATOR.equals(member.getRole())) {
            return view(room);
        }
        memberMapper.update(null, new LambdaUpdateWrapper<GameRoomMember>()
                .eq(GameRoomMember::getId, member.getId())
                .set(GameRoomMember::getReady, Boolean.TRUE.equals(ready) ? 1 : 0));
        return view(room);
    }

    // ---------- 席位自助调整(参战⇄观战) ----------

    /**
     * 直切席位:参战玩家随时可转观战让席;观战者在席位有空时可直接参战。
     * 对局进行中锁定;房主恒为玩家,不能转观战;转参战后需重新准备。
     */
    public Map<String, Object> switchRole(Long userId, Long familyId, Long id, String role) {
        boolean toPlayer = GameConst.ROOM_ROLE_PLAYER.equals(role);
        boolean toSpectator = GameConst.ROOM_ROLE_SPECTATOR.equals(role);
        if (!toPlayer && !toSpectator) {
            throw new BizException(ResultCode.BAD_REQUEST, "要切换的席位不对");
        }
        synchronized (("game-room-" + id).intern()) {
            GameRoom room = require(familyId, id);
            requireSwitchable(room);
            GameRoomMember member = requireMember(id, userId);
            if (GameConst.ROOM_ROLE_HOST.equals(member.getRole())) {
                throw new BizException(ResultCode.BAD_REQUEST, "房主要负责开局,不能转观战");
            }
            if (role.equals(member.getRole())) {
                return view(room);
            }
            if (toPlayer) {
                requireFreeSeat(room, id);
            }
            updateRole(member, role);
            return view(room);
        }
    }

    /** 观战者向参战玩家发起换位申请:对方同意后两人互换席位(每房间同时只有一份待处理申请) */
    public Map<String, Object> requestSwap(Long userId, Long familyId, Long id, Long targetUserId) {
        if (targetUserId == null || targetUserId.equals(userId)) {
            throw new BizException(ResultCode.BAD_REQUEST, "要交换的对象不对");
        }
        synchronized (("game-room-" + id).intern()) {
            GameRoom room = require(familyId, id);
            requireSwitchable(room);
            GameRoomMember me = requireMember(id, userId);
            if (!GameConst.ROOM_ROLE_SPECTATOR.equals(me.getRole())) {
                throw new BizException(ResultCode.BAD_REQUEST, "你在参战中,不需要申请换位");
            }
            GameRoomMember target = memberOf(id, targetUserId);
            if (target == null || !GameConst.ROOM_ROLE_PLAYER.equals(target.getRole())) {
                throw new BizException(ResultCode.BAD_REQUEST, "只能和参战中的玩家换位");
            }
            if (readSwap(id) != null) {
                throw new BizException(ResultCode.BAD_REQUEST, "已有一份换位申请待处理,先等等");
            }
            try {
                redis.opsForValue().set(swapKey(id),
                        objectMapper.writeValueAsString(Map.of("from", userId, "target", targetUserId)),
                        Duration.ofMinutes(SWAP_TTL_MINUTES));
            } catch (Exception e) {
                log.warn("房间换位申请写入失败 roomId={} from={} target={}", id, userId, targetUserId, e);
                throw new BizException(ResultCode.BAD_REQUEST, "换位申请没发出去,再试一次");
            }
            return view(room);
        }
    }

    /** 参战玩家同意换位:与申请者互换席位,双方重新准备 */
    public Map<String, Object> acceptSwap(Long userId, Long familyId, Long id) {
        synchronized (("game-room-" + id).intern()) {
            GameRoom room = require(familyId, id);
            Map<String, Long> swap = readSwap(id);
            if (swap == null || !userId.equals(swap.get("target"))) {
                throw new BizException(ResultCode.BAD_REQUEST, "没有等你处理的换位申请");
            }
            clearSwap(id);
            requireSwitchable(room);
            GameRoomMember from = memberOf(id, swap.get("from"));
            GameRoomMember me = requireMember(id, userId);
            if (from == null || !GameConst.ROOM_ROLE_SPECTATOR.equals(from.getRole())
                    || !GameConst.ROOM_ROLE_PLAYER.equals(me.getRole())) {
                throw new BizException(ResultCode.BAD_REQUEST, "席位已经变了,这份申请作废了");
            }
            updateRole(from, GameConst.ROOM_ROLE_PLAYER);
            updateRole(me, GameConst.ROOM_ROLE_SPECTATOR);
            return view(room);
        }
    }

    /** 取消/拒绝换位申请:发起者或目标都可撤 */
    public Map<String, Object> cancelSwap(Long userId, Long familyId, Long id) {
        synchronized (("game-room-" + id).intern()) {
            GameRoom room = require(familyId, id);
            Map<String, Long> swap = readSwap(id);
            if (swap != null && (userId.equals(swap.get("from")) || userId.equals(swap.get("target")))) {
                clearSwap(id);
            }
            return view(room);
        }
    }

    /**
     * 让位(对局进行中):参战玩家把自己的席位让给指定的观战者,两人立即互换角色。
     * 与局中锁定的自助换位不同,让位是牌局进行时的主动交接,由游戏页把席位接管给新人。
     */
    public Map<String, Object> yieldSeat(Long userId, Long familyId, Long id, Long toUserId) {
        if (toUserId == null || toUserId.equals(userId)) {
            throw new BizException(ResultCode.BAD_REQUEST, "要让位的对象不对");
        }
        synchronized (("game-room-" + id).intern()) {
            GameRoom room = require(familyId, id);
            if (!GameConst.ROOM_PLAYING.equals(room.getStatus())) {
                throw new BizException(ResultCode.BAD_REQUEST, "对局中才能让位,等待中直接切换席位");
            }
            if (GameConst.GAME_EIGHT_BALL.equals(room.getGameCode()) && Objects.equals(room.getHostId(), userId)) {
                throw new BizException(ResultCode.BAD_REQUEST, "房主需留在球桌上主持对局");
            }
            GameRoomMember me = requireMember(id, userId);
            if (GameConst.ROOM_ROLE_SPECTATOR.equals(me.getRole())) {
                throw new BizException(ResultCode.BAD_REQUEST, "你在观战中,不需要让位");
            }
            GameRoomMember target = memberOf(id, toUserId);
            if (target == null || !GameConst.ROOM_ROLE_SPECTATOR.equals(target.getRole())) {
                throw new BizException(ResultCode.BAD_REQUEST, "只能让位给观战中的成员");
            }
            updateRole(me, GameConst.ROOM_ROLE_SPECTATOR);
            updateRole(target, GameConst.ROOM_ROLE_PLAYER);
            return view(room);
        }
    }

    /** 席位调整的时间窗:等待中/已结算可调,对局进行中与已取消锁定 */
    private void requireSwitchable(GameRoom room) {
        if (GameConst.ROOM_PLAYING.equals(room.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST, "对局进行中,这局结束后再换");
        }
        if (GameConst.ROOM_CANCELLED.equals(room.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST, "房间已经结束了");
        }
    }

    /** 参战席是否还有空位(与 joinRole 同口径:非观战成员数 < 游戏席位数) */
    private void requireFreeSeat(GameRoom room, Long roomId) {
        GameConst.RoomGame spec = GameConst.ROOM_GAMES.get(room.getGameCode());
        int seats = spec == null ? GameConst.ROOM_MAX_PLAYERS : spec.maxSeats();
        long players = memberMapper.selectCount(new LambdaQueryWrapper<GameRoomMember>()
                .eq(GameRoomMember::getRoomId, roomId)
                .ne(GameRoomMember::getRole, GameConst.ROOM_ROLE_SPECTATOR));
        if (players >= seats) {
            throw new BizException(ResultCode.BAD_REQUEST, "参战席位已满");
        }
    }

    /** 换席位并重置准备:转回参战后要重新点准备,与随机分席口径一致 */
    private void updateRole(GameRoomMember member, String role) {
        memberMapper.update(null, new LambdaUpdateWrapper<GameRoomMember>()
                .eq(GameRoomMember::getId, member.getId())
                .set(GameRoomMember::getRole, role)
                .set(GameRoomMember::getReady, 0));
    }

    private String swapKey(Long roomId) {
        return "ihomy:game:room:swap:" + roomId;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Long> readSwap(Long roomId) {
        try {
            String json = redis.opsForValue().get(swapKey(roomId));
            if (json == null || json.isBlank()) {
                return null;
            }
            Map<String, Object> raw = objectMapper.readValue(json, Map.class);
            Object from = raw.get("from");
            Object target = raw.get("target");
            if (from instanceof Number n1 && target instanceof Number n2) {
                return Map.of("from", n1.longValue(), "target", n2.longValue());
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private void clearSwap(Long roomId) {
        try {
            redis.delete(swapKey(roomId));
        } catch (Exception e) {
            log.warn("房间换位申请清理失败 roomId={}", roomId, e);
        }
    }

    /** 房主开赛:至少 2 名玩家且玩家全部准备(观战不要求准备) */
    public Map<String, Object> start(Long userId, Long familyId, Long id) {
        GameRoom room = require(familyId, id);
        if (!Objects.equals(room.getHostId(), userId)) {
            throw new BizException(ResultCode.BAD_REQUEST, "只有房主可以开始游戏");
        }
        if (!GameConst.ROOM_WAITING.equals(room.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST, "这个房间已经开过了");
        }
        List<GameRoomMember> players = members(id).stream()
                .filter(m -> !GameConst.ROOM_ROLE_SPECTATOR.equals(m.getRole()))
                .toList();
        GameConst.RoomGame spec = GameConst.ROOM_GAMES.get(room.getGameCode());
        int minPlayers = spec != null && spec.ai() ? 1 : 2; // 有电脑玩家的游戏 1 名真人即可开局,空位由电脑补
        if (players.size() < minPlayers) {
            throw new BizException(ResultCode.BAD_REQUEST, minPlayers < 2 ? "至少要有一名玩家才能开始" : "至少要有两名玩家才能开始");
        }
        boolean allReady = players.stream().allMatch(m -> Integer.valueOf(1).equals(m.getReady()));
        if (!allReady) {
            throw new BizException(ResultCode.BAD_REQUEST, "还有玩家没有准备好");
        }
        int updated = roomMapper.update(null, new LambdaUpdateWrapper<GameRoom>()
                .eq(GameRoom::getId, id)
                .eq(GameRoom::getStatus, GameConst.ROOM_WAITING)
                .set(GameRoom::getStatus, GameConst.ROOM_PLAYING)
                .set(GameRoom::getStartedAt, LocalDateTime.now()));
        if (updated == 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "这个房间已经开过了");
        }
        clearSwap(id);
        return get(familyId, id);
    }

    /**
     * 上报成绩(幂等):首报生效。
     * PK 先完成制:第一个上报的人直接结束房间并判胜;
     * PK 高分制:所有人上报完才结算,房主可提前结束;
     * COOP:仅房主上报完成,房间结束,不判胜负。
     */
    public Map<String, Object> finish(Long userId, Long familyId, Long id, long score, Long winnerId, String roundToken) {
        GameRoom room = require(familyId, id);
        if (!GameConst.ROOM_PLAYING.equals(room.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST, "对局还没有开始");
        }
        GameRoomMember member = requireMember(id, userId);
        if (GameConst.GAME_EIGHT_BALL.equals(room.getGameCode())) {
            if (!Objects.equals(room.getHostId(), userId) || !String.valueOf(room.getSeed()).equals(roundToken)) {
                throw new BizException(ResultCode.BAD_REQUEST, "请由本局房主确认胜负");
            }
            if (winnerId == null || members(id).stream().noneMatch(m -> winnerId.equals(m.getUserId())
                    && !GameConst.ROOM_ROLE_SPECTATOR.equals(m.getRole()))) {
                throw new BizException(ResultCode.BAD_REQUEST, "请选择本局的参战玩家");
            }
            settle(finishFirst(room, winnerId));
            return get(familyId, id);
        }
        if (GameConst.ROOM_ROLE_SPECTATOR.equals(member.getRole())) {
            return get(familyId, id);
        }
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
            settle(finishRoom(room, null));
            return get(familyId, id);
        }
        if (GameConst.ROOM_RESULT_FIRST.equals(result)) {
            settle(finishFirst(room, userId));
            return get(familyId, id);
        }
        // 高分制:所有玩家(观战不算)都上报后自动结算
        List<GameRoomMember> members = members(id);
        boolean allReported = members.stream()
                .filter(m -> !GameConst.ROOM_ROLE_SPECTATOR.equals(m.getRole()))
                .allMatch(m -> m.getFinishedAt() != null);
        if (allReported) {
            settle(finishByScore(room));
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
            settle(GameConst.ROOM_RESULT_SCORE.equals(result) ? finishByScore(room) : finishRoom(room, null));
        }
        return get(familyId, id);
    }

    /** 房主再来一局:保留成员与席位,重置房间与成绩回到等待(不用重建房间、重新邀请) */
    public Map<String, Object> again(Long userId, Long familyId, Long id) {
        GameRoom room = require(familyId, id);
        if (!Objects.equals(room.getHostId(), userId)) {
            throw new BizException(ResultCode.BAD_REQUEST, "只有房主可以再来一局");
        }
        if (!GameConst.ROOM_FINISHED.equals(room.getStatus())
                && !GameConst.ROOM_CANCELLED.equals(room.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST, "这局还没结束");
        }
        int updated = roomMapper.update(null, new LambdaUpdateWrapper<GameRoom>()
                .eq(GameRoom::getId, id)
                .in(GameRoom::getStatus, GameConst.ROOM_FINISHED, GameConst.ROOM_CANCELLED)
                .set(GameRoom::getStatus, GameConst.ROOM_WAITING)
                .set(GameRoom::getWinnerId, null)
                .set(GameRoom::getStartedAt, null)
                .set(GameRoom::getFinishedAt, null)
                .set(GameRoom::getSeed, Math.abs(RandomUtil.randomLong())));
        if (updated == 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "这局还没结束");
        }
        if (GameConst.GAME_EIGHT_BALL.equals(room.getGameCode()) && restoreDuelHost(room)) {
            assignSeats(id, GameConst.ROOM_GAMES.get(room.getGameCode()));
        }
        for (GameRoomMember m : members(id)) {
            memberMapper.update(null, new LambdaUpdateWrapper<GameRoomMember>()
                    .eq(GameRoomMember::getId, m.getId())
                    .set(GameRoomMember::getReady,
                            GameConst.ROOM_ROLE_HOST.equals(m.getRole()) ? 1 : 0)
                    .set(GameRoomMember::getScore, null)
                    .set(GameRoomMember::getFinishedAt, null));
        }
        clearSwap(id);
        return get(familyId, id);
    }

    /**
     * 离开房间:成员行物理删除(再进屋时不会撞唯一键,已结束的房间也能真正退出)。
     * 房主离开 = 房主端引擎关闭:WAITING→取消;PLAYING→按当前成绩结算;
     * 已结束的房间房主保留成员行,否则「再来一局」回来时没有自己的席位。
     */
    public Map<String, Object> leave(Long userId, Long familyId, Long id) {
        GameRoom room = require(familyId, id);
        GameRoomMember member = memberOf(id, userId);
        if (member == null) {
            return view(room);
        }
        Map<String, Long> swap = readSwap(id);
        if (swap != null && (userId.equals(swap.get("from")) || userId.equals(swap.get("target")))) {
            clearSwap(id);
        }
        boolean isHost = Objects.equals(room.getHostId(), userId);
        if (GameConst.ROOM_WAITING.equals(room.getStatus())) {
            memberMapper.hardDelete(member.getId());
            if (isHost) {
                roomMapper.update(null, new LambdaUpdateWrapper<GameRoom>()
                        .eq(GameRoom::getId, id)
                        .eq(GameRoom::getStatus, GameConst.ROOM_WAITING)
                        .set(GameRoom::getStatus, GameConst.ROOM_CANCELLED)
                        .set(GameRoom::getFinishedAt, LocalDateTime.now()));
            }
        } else if (GameConst.ROOM_PLAYING.equals(room.getStatus())) {
            memberMapper.hardDelete(member.getId());
            boolean duelPlayerLeft = GameConst.GAME_EIGHT_BALL.equals(room.getGameCode())
                    && !GameConst.ROOM_ROLE_SPECTATOR.equals(member.getRole());
            if (isHost || duelPlayerLeft) {
                GameConst.RoomGame spec = GameConst.ROOM_GAMES.get(room.getGameCode());
                String result = spec == null ? GameConst.ROOM_RESULT_SCORE : spec.result();
                settle(GameConst.ROOM_RESULT_SCORE.equals(result) ? finishByScore(room) : finishRoom(room, null));
            }
        } else if (!isHost) {
            // FINISHED/CANCELLED:局已结束,成员可以直接退出;房主留着当「再来一局」的锚点
            memberMapper.hardDelete(member.getId());
        }
        return get(familyId, id);
    }

    /** 房主请人离开房间(不能踢自己;对局中踢出后由房主决定是否结束本局) */
    public Map<String, Object> kick(Long userId, Long familyId, Long id, Long targetUserId) {
        GameRoom room = require(familyId, id);
        if (!Objects.equals(room.getHostId(), userId)) {
            throw new BizException(ResultCode.BAD_REQUEST, "只有房主可以请成员离开房间");
        }
        if (targetUserId == null || targetUserId.equals(userId)) {
            throw new BizException(ResultCode.BAD_REQUEST, "不能请自己离开房间");
        }
        GameRoomMember target = memberOf(id, targetUserId);
        if (target == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "这个人已经不在房间里了");
        }
        if (GameConst.GAME_EIGHT_BALL.equals(room.getGameCode()) && GameConst.ROOM_PLAYING.equals(room.getStatus())
                && !GameConst.ROOM_ROLE_SPECTATOR.equals(target.getRole())) {
            throw new BizException(ResultCode.BAD_REQUEST, "请先结束本局，再移除参战玩家");
        }
        Map<String, Long> swap = readSwap(id);
        if (swap != null && (targetUserId.equals(swap.get("from")) || targetUserId.equals(swap.get("target")))) {
            clearSwap(id);
        }
        memberMapper.hardDelete(target.getId());
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
                .eq(GameRoom::getSeed, room.getSeed())
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

    /** 结算收尾:胜者积分 + 本局成绩落房间排行榜(跨「再来一局」累计,只记本房间) */
    private void settle(GameRoom finished) {
        if (finished == null) {
            return;
        }
        if (finished.getWinnerId() != null) {
            awardWinner(finished, finished.getWinnerId());
        }
        recordRound(finished);
    }

    /** 本局成绩入房间排行榜:每个参战成员一行(观战不入榜),round_no 按房间累计局数递增 */
    private void recordRound(GameRoom room) {
        try {
            long played = roundMapper.selectCount(new LambdaQueryWrapper<GameRoomRound>()
                    .eq(GameRoomRound::getRoomId, room.getId()));
            int roundNo = (int) played + 1;
            List<GameRoomMember> ms = members(room.getId()).stream()
                    .filter(m -> !GameConst.ROOM_ROLE_SPECTATOR.equals(m.getRole()))
                    .toList();
            List<Long> userIds = ms.stream().map(GameRoomMember::getUserId).filter(Objects::nonNull).toList();
            Map<Long, SysUser> users = userIds.isEmpty() ? Map.of()
                    : sysUserMapper.selectBatchIds(userIds).stream()
                            .collect(Collectors.toMap(SysUser::getId, u -> u));
            for (GameRoomMember m : ms) {
                GameRoomRound row = new GameRoomRound();
                row.setRoomId(room.getId());
                row.setFamilyId(room.getFamilyId());
                row.setGameCode(room.getGameCode());
                row.setRoundNo(roundNo);
                row.setUserId(m.getUserId());
                SysUser u = m.getUserId() == null ? null : users.get(m.getUserId());
                row.setSeatName(u == null ? "成员"
                        : (u.getNickname() != null && !u.getNickname().isBlank() ? u.getNickname() : u.getUsername()));
                row.setScore(m.getScore() == null ? null : m.getScore().intValue());
                row.setIsWinner(m.getUserId() != null && m.getUserId().equals(room.getWinnerId()) ? 1 : 0);
                row.setCreatedAt(LocalDateTime.now());
                roundMapper.insert(row);
            }
        } catch (Exception e) {
            log.warn("房间排行榜记录失败 roomId={}", room.getId(), e);
        }
    }

    /** 房间排行榜:各局流水 + 按人累计(局数/胜场/累计分),只统计本房间 */
    public Map<String, Object> board(Long familyId, Long id) {
        require(familyId, id);
        List<GameRoomRound> rounds = roundMapper.selectList(new LambdaQueryWrapper<GameRoomRound>()
                .eq(GameRoomRound::getRoomId, id)
                .orderByAsc(GameRoomRound::getRoundNo)
                .orderByAsc(GameRoomRound::getId));
        List<Long> userIds = rounds.stream().map(GameRoomRound::getUserId).filter(Objects::nonNull).distinct().toList();
        Map<Long, SysUser> users = userIds.isEmpty() ? Map.of()
                : sysUserMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(SysUser::getId, u -> u));
        List<Map<String, Object>> roundViews = new ArrayList<>();
        Map<Long, Map<String, Object>> totals = new LinkedHashMap<>();
        for (GameRoomRound r : rounds) {
            Map<String, Object> rv = new LinkedHashMap<>();
            rv.put("roundNo", r.getRoundNo());
            rv.put("gameCode", r.getGameCode());
            rv.put("userId", r.getUserId());
            SysUser u = r.getUserId() == null ? null : users.get(r.getUserId());
            String name = u == null ? r.getSeatName()
                    : (u.getNickname() != null && !u.getNickname().isBlank() ? u.getNickname() : u.getUsername());
            rv.put("name", name);
            rv.put("score", r.getScore());
            rv.put("winner", Integer.valueOf(1).equals(r.getIsWinner()));
            rv.put("createdAt", r.getCreatedAt());
            roundViews.add(rv);
            if (r.getUserId() == null) {
                continue;
            }
            Map<String, Object> t = totals.computeIfAbsent(r.getUserId(), k -> {
                Map<String, Object> init = new LinkedHashMap<>();
                init.put("userId", r.getUserId());
                init.put("name", name);
                init.put("rounds", 0);
                init.put("wins", 0);
                init.put("totalScore", 0L);
                return init;
            });
            t.put("rounds", (int) t.get("rounds") + 1);
            if (Integer.valueOf(1).equals(r.getIsWinner())) {
                t.put("wins", (int) t.get("wins") + 1);
            }
            t.put("totalScore", (long) t.get("totalScore") + (r.getScore() == null ? 0 : r.getScore()));
        }
        List<Map<String, Object>> ranking = new ArrayList<>(totals.values());
        ranking.sort((a, b) -> {
            int byWins = Integer.compare((int) b.get("wins"), (int) a.get("wins"));
            if (byWins != 0) {
                return byWins;
            }
            return Long.compare((long) b.get("totalScore"), (long) a.get("totalScore"));
        });
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("roundsCount", roundViews.isEmpty() ? 0
                : ((Number) roundViews.get(roundViews.size() - 1).get("roundNo")).intValue());
        out.put("rounds", roundViews);
        out.put("ranking", ranking);
        return out;
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

    /** 单房间视图:附带待处理的换位申请(列表视图不带,前端从 members 里解析名字) */
    private Map<String, Object> view(GameRoom room) {
        Map<String, Object> v = views(List.of(room)).get(0);
        Map<String, Long> swap = readSwap(room.getId());
        if (swap != null) {
            v.put("pendingSwap", Map.of("userId", swap.get("from"), "targetUserId", swap.get("target")));
        }
        return v;
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
        Map<Long, SysUser> users = userIds.isEmpty() ? Map.of()
                : sysUserMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(SysUser::getId, u -> u));
        Map<Long, String> names = users.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        e -> e.getValue().getNickname() != null ? e.getValue().getNickname() : e.getValue().getUsername()));
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
            if (GameConst.GAME_EIGHT_BALL.equals(room.getGameCode())) {
                v.put("roundToken", String.valueOf(room.getSeed()));
            }
            v.put("status", room.getStatus());
            v.put("hostId", room.getHostId());
            v.put("hostName", names.getOrDefault(room.getHostId(), "成员"));
            v.put("maxPlayers", room.getMaxPlayers());
            GameConst.RoomGame spec = GameConst.ROOM_GAMES.get(room.getGameCode());
            v.put("maxSeatPlayers", spec == null ? GameConst.ROOM_MAX_PLAYERS : spec.maxSeats());
            v.put("winnerId", room.getWinnerId());
            v.put("startedAt", room.getStartedAt());
            v.put("finishedAt", room.getFinishedAt());
            v.put("createdAt", room.getCreatedAt());
            List<Map<String, Object>> members = new ArrayList<>();
            for (GameRoomMember m : byRoom.getOrDefault(room.getId(), List.of())) {
                Map<String, Object> mv = new LinkedHashMap<>();
                SysUser u = users.get(m.getUserId());
                mv.put("userId", m.getUserId());
                mv.put("name", names.getOrDefault(m.getUserId(), "成员"));
                mv.put("avatar", u == null ? null : u.getAvatar());
                mv.put("role", m.getRole());
                mv.put("ready", Integer.valueOf(1).equals(m.getReady()));
                mv.put("score", m.getScore());
                mv.put("finishedAt", m.getFinishedAt());
                members.add(mv);
            }
            v.put("members", members);
            v.put("playersCount", members.stream()
                    .filter(m -> !GameConst.ROOM_ROLE_SPECTATOR.equals(m.get("role"))).count());
            v.put("readyCount", members.stream().filter(m -> Boolean.TRUE.equals(m.get("ready"))).count());
            out.add(v);
        }
        return out;
    }
}
