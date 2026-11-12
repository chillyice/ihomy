package com.ihomy.controller;

import com.ihomy.annotation.OperationLog;
import com.ihomy.common.ChatMsgType;
import com.ihomy.common.GameConst;
import com.ihomy.common.Result;
import com.ihomy.dto.GameRoomCreateDTO;
import com.ihomy.dto.GameRoomFinishDTO;
import com.ihomy.dto.GameRoomGameDTO;
import com.ihomy.dto.GameRoomReadyDTO;
import com.ihomy.security.LoginUser;
import com.ihomy.security.SecurityHelper;
import com.ihomy.service.ChatService;
import com.ihomy.service.GameRoomService;
import com.ihomy.websocket.ChatWebSocketHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 「一起玩」房间接口:建房/加入/准备/开赛/上报/结束/离开。
 * 建房即往聊天流发 GAME_INVITE 邀请卡(带 roomId 与玩法),在线成员点卡片进房间;
 * 房间状态变化经 WS game-signal(event=room)广播给全部成员,实时对局状态由房主广播。
 */
@Tag(name = "游戏房间")
@RestController
@RequestMapping("/game/room")
@RequiredArgsConstructor
public class GameRoomController {

    private final GameRoomService roomService;
    private final ChatService chatService;
    private final ChatWebSocketHandler wsHandler;
    private final SecurityHelper securityHelper;
    private final StringRedisTemplate redis;
    private final tools.jackson.databind.ObjectMapper objectMapper;

    private LoginUser current() {
        return securityHelper.current();
    }

    @Operation(summary = "创建房间(同时往聊天室发邀请卡)")
    @OperationLog(module = "GAME", operationType = "CREATE", description = "创建游戏房间")
    @PostMapping
    public Result<Map<String, Object>> create(@RequestBody @jakarta.validation.Valid GameRoomCreateDTO dto) {
        LoginUser user = current();
        Map<String, Object> room = roomService.create(user.getUserId(), user.getFamilyId(),
                dto.getGameCode(), dto.getMode(), dto.getMaxPlayers());
        String name = String.valueOf(room.get("gameName"));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("gameCode", dto.getGameCode());
        payload.put("roomId", room.get("id"));
        payload.put("mode", room.get("mode"));
        Map<String, Object> view = chatService.sendMessage(user.getFamilyId(), user.getUserId(),
                ChatMsgType.GAME_INVITE, name, json(payload));
        wsHandler.broadcastMessage(user.getFamilyId(), view);
        return Result.success(room);
    }

    @Operation(summary = "家庭房间列表(最近 20 场)")
    @GetMapping
    public Result<List<Map<String, Object>>> list(@RequestParam(required = false) String status,
                                                  @RequestParam(defaultValue = "20") int limit) {
        return Result.success(roomService.list(current().getFamilyId(), status, limit));
    }

    @Operation(summary = "房间详情")
    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable Long id) {
        return Result.success(roomService.get(current().getFamilyId(), id));
    }

    @Operation(summary = "加入房间")
    @OperationLog(module = "GAME", operationType = "UPDATE", description = "加入游戏房间")
    @PostMapping("/{id}/join")
    public Result<Map<String, Object>> join(@PathVariable Long id) {
        LoginUser user = current();
        Map<String, Object> room = roomService.join(user.getUserId(), user.getFamilyId(), id);
        broadcast(id, room);
        return Result.success(room);
    }

    @Operation(summary = "准备/取消准备")
    @OperationLog(module = "GAME", operationType = "UPDATE", description = "切换房间准备状态")
    @PostMapping("/{id}/ready")
    public Result<Map<String, Object>> ready(@PathVariable Long id, @RequestBody GameRoomReadyDTO dto) {
        LoginUser user = current();
        Map<String, Object> room = roomService.ready(user.getUserId(), user.getFamilyId(), id, dto.getReady());
        broadcast(id, room);
        return Result.success(room);
    }

    @Operation(summary = "房主换游戏(随机重新分配席位)")
    @OperationLog(module = "GAME", operationType = "UPDATE", description = "房间切换游戏")
    @PostMapping("/{id}/game")
    public Result<Map<String, Object>> setGame(@PathVariable Long id,
                                               @RequestBody @jakarta.validation.Valid GameRoomGameDTO dto) {
        LoginUser user = current();
        Map<String, Object> room = roomService.setGame(user.getUserId(), user.getFamilyId(), id, dto.getGameCode());
        broadcast(id, room);
        return Result.success(room);
    }

    @Operation(summary = "房主开始游戏")
    @OperationLog(module = "GAME", operationType = "UPDATE", description = "开始游戏房间")
    @PostMapping("/{id}/start")
    public Result<Map<String, Object>> start(@PathVariable Long id) {
        LoginUser user = current();
        Map<String, Object> room = roomService.start(user.getUserId(), user.getFamilyId(), id);
        broadcast(id, room);
        return Result.success(room);
    }

    @Operation(summary = "上报成绩(按房间玩法结算)")
    @OperationLog(module = "GAME", operationType = "UPDATE", description = "上报房间成绩")
    @PostMapping("/{id}/finish")
    public Result<Map<String, Object>> finish(@PathVariable Long id,
                                              @RequestBody @jakarta.validation.Valid GameRoomFinishDTO dto) {
        LoginUser user = current();
        Map<String, Object> room = roomService.finish(user.getUserId(), user.getFamilyId(), id,
                dto.getScore() == null ? 0 : dto.getScore());
        broadcast(id, room);
        postResultCard(user, room);
        return Result.success(room);
    }

    @Operation(summary = "房主结束/取消房间")
    @OperationLog(module = "GAME", operationType = "UPDATE", description = "结束游戏房间")
    @PostMapping("/{id}/end")
    public Result<Map<String, Object>> end(@PathVariable Long id) {
        LoginUser user = current();
        Map<String, Object> room = roomService.end(user.getUserId(), user.getFamilyId(), id);
        broadcast(id, room);
        postResultCard(user, room);
        return Result.success(room);
    }

    @Operation(summary = "离开房间")
    @OperationLog(module = "GAME", operationType = "UPDATE", description = "离开游戏房间")
    @PostMapping("/{id}/leave")
    public Result<Map<String, Object>> leave(@PathVariable Long id) {
        LoginUser user = current();
        Map<String, Object> room = roomService.leave(user.getUserId(), user.getFamilyId(), id);
        broadcast(id, room);
        return Result.success(room);
    }

    /** 房间状态变化:定向推给房间成员,前端据此刷新大厅/自动进入游戏 */
    private void broadcast(Long roomId, Map<String, Object> room) {
        List<Long> members = roomService.memberIds(current().getFamilyId(), roomId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("roomId", roomId);
        data.put("event", "room");
        data.put("room", room);
        wsHandler.sendToUsers(members, "game-signal", data, null);
    }

    /** 终局结果卡(Redis 幂等,重复上报只发一次) */
    @SuppressWarnings("unchecked")
    private void postResultCard(LoginUser user, Map<String, Object> room) {
        if (!GameConst.ROOM_FINISHED.equals(room.get("status"))) {
            return;
        }
        Long roomId = ((Number) room.get("id")).longValue();
        String key = "ihomy:game:room:result:" + roomId;
        try {
            if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, "1", Duration.ofDays(2)))) {
                return;
            }
        } catch (Exception e) {
            // Redis 不可用时宁可重复发卡也不丢结果
        }
        String name = String.valueOf(room.get("gameName"));
        Object winnerId = room.get("winnerId");
        String sub;
        if (GameConst.ROOM_MODE_COOP.equals(room.get("mode"))) {
            sub = "合作完成";
        } else if (winnerId == null) {
            sub = "平局";
        } else {
            List<Map<String, Object>> members = (List<Map<String, Object>>) room.get("members");
            String winnerName = members.stream()
                    .filter(m -> winnerId.equals(m.get("userId")))
                    .map(m -> String.valueOf(m.get("name")))
                    .findFirst().orElse("成员");
            sub = winnerName + " 获胜";
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("objType", "MATCH");
        payload.put("objId", roomId);
        payload.put("sub", sub);
        payload.put("url", "/games/room/" + roomId);
        Map<String, Object> view = chatService.sendMessage(user.getFamilyId(), user.getUserId(),
                ChatMsgType.CARD, name + "一起玩结果", json(payload));
        if (view != null) {
            wsHandler.broadcastMessage(user.getFamilyId(), view);
        }
    }

    private String json(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return null;
        }
    }
}
