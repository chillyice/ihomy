package com.ihomy.controller;

import com.ihomy.annotation.OperationLog;
import com.ihomy.common.GameConst;
import com.ihomy.common.Result;
import com.ihomy.dto.GameMatchCreateDTO;
import com.ihomy.dto.GameMatchFinishDTO;
import com.ihomy.security.LoginUser;
import com.ihomy.security.SecurityHelper;
import com.ihomy.service.ChatService;
import com.ihomy.service.GameMatchService;
import com.ihomy.websocket.ChatWebSocketHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 家庭游戏对战接口:创建/列表/详情/加入/取消/成绩上报。
 * 创建即往聊天流发游戏邀请卡(GAME_INVITE)并广播,终局发结果卡(CARD objType=MATCH)并广播,
 * 在线成员的浮窗/聊天页即时可见;实时对战的过程信令走 WS game-signal(不经 REST)。
 */
@Tag(name = "游戏对战")
@RestController
@RequestMapping("/game/match")
@RequiredArgsConstructor
public class GameMatchController {

    private final GameMatchService matchService;
    private final ChatService chatService;
    private final ChatWebSocketHandler wsHandler;
    private final SecurityHelper securityHelper;
    private final tools.jackson.databind.ObjectMapper objectMapper;

    private LoginUser current() {
        return securityHelper.current();
    }

    @Operation(summary = "创建对战(同时往聊天室发邀请卡)")
    @OperationLog(module = "GAME", operationType = "CREATE", description = "创建游戏对战")
    @PostMapping
    public Result<Map<String, Object>> create(@RequestBody @jakarta.validation.Valid GameMatchCreateDTO dto) {
        LoginUser user = current();
        Map<String, Object> match = matchService.create(user.getUserId(), user.getFamilyId(), dto.getGameCode(), dto.getMode());
        // 邀请卡:content=游戏名快照,payload 带 matchId(点击进对战页)
        String name = GameConst.MATCH_GAME_NAMES.getOrDefault(dto.getGameCode(), dto.getGameCode());
        Map<String, Object> view = chatService.sendMessage(user.getFamilyId(), user.getUserId(),
                com.ihomy.common.ChatMsgType.GAME_INVITE, name, json(Map.of("gameCode", dto.getGameCode(), "matchId", match.get("id"))));
        wsHandler.broadcastMessage(user.getFamilyId(), view);
        return Result.success(match);
    }

    @Operation(summary = "家庭对战列表(最近 20 场)")
    @GetMapping
    public Result<List<Map<String, Object>>> list(@RequestParam(required = false) String status,
                                                  @RequestParam(defaultValue = "20") int limit) {
        return Result.success(matchService.list(current().getFamilyId(), status, limit));
    }

    @Operation(summary = "对战详情")
    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable Long id) {
        return Result.success(matchService.get(current().getFamilyId(), id));
    }

    @Operation(summary = "加入对战")
    @OperationLog(module = "GAME", operationType = "UPDATE", description = "加入游戏对战")
    @PostMapping("/{id}/join")
    public Result<Map<String, Object>> join(@PathVariable Long id) {
        LoginUser user = current();
        return Result.success(matchService.join(user.getUserId(), user.getFamilyId(), id));
    }

    @Operation(summary = "取消对战(仅发起人,未开赛)")
    @OperationLog(module = "GAME", operationType = "UPDATE", description = "取消游戏对战")
    @PostMapping("/{id}/cancel")
    public Result<Map<String, Object>> cancel(@PathVariable Long id) {
        LoginUser user = current();
        return Result.success(matchService.cancel(user.getUserId(), user.getFamilyId(), id));
    }

    @Operation(summary = "上报成绩(幂等;双方都报完判胜者并发积分)")
    @OperationLog(module = "GAME", operationType = "UPDATE", description = "上报对战成绩")
    @PostMapping("/{id}/finish")
    public Result<Map<String, Object>> finish(@PathVariable Long id,
                                              @RequestBody @jakarta.validation.Valid GameMatchFinishDTO dto) {
        LoginUser user = current();
        Map<String, Object> r = matchService.finish(user.getUserId(), user.getFamilyId(), id,
                dto.getScore() == null ? 0 : dto.getScore());
        if (Boolean.TRUE.equals(r.get("finished"))) {
            postResultCard(user, (Map<String, Object>) r.get("match"));
        }
        return Result.success(r);
    }

    /** 结果卡:CARD objType=MATCH,点击进对战页看比分 */
    @SuppressWarnings("unchecked")
    private void postResultCard(LoginUser user, Map<String, Object> match) {
        Long matchId = ((Number) match.get("id")).longValue();
        String gameName = GameConst.MATCH_GAME_NAMES.getOrDefault((String) match.get("gameCode"), (String) match.get("gameCode"));
        Long winnerId = (Long) match.get("winnerId");
        String sub = winnerId == null ? "平局"
                : (winnerId.equals(match.get("hostId")) ? match.get("hostName") : match.get("guestName")) + " 获胜";
        Map<String, Object> payload = Map.of(
                "objType", "MATCH",
                "objId", matchId,
                "sub", sub,
                "url", "/games/match/" + matchId);
        Map<String, Object> view = chatService.sendMessage(user.getFamilyId(), user.getUserId(),
                com.ihomy.common.ChatMsgType.CARD, gameName + "对战结果", json(payload));
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
