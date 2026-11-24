package com.ihomy.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.ihomy.common.BizException;
import com.ihomy.common.GameConst;
import com.ihomy.entity.GameRoom;
import com.ihomy.entity.GameRoomMember;
import com.ihomy.mapper.GameRoomMapper;
import com.ihomy.mapper.GameRoomMemberMapper;
import com.ihomy.mapper.GameRoomRoundMapper;
import com.ihomy.mapper.SysUserMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GameRoomServiceTest {
    private final GameRoomMapper rooms = mock(GameRoomMapper.class);
    private final GameRoomMemberMapper members = mock(GameRoomMemberMapper.class);
    private final GameRoomService service = new GameRoomService(rooms, members, mock(GameRoomRoundMapper.class),
            mock(SysUserMapper.class), mock(PointsService.class), mock(StringRedisTemplate.class, RETURNS_DEEP_STUBS),
            mock(tools.jackson.databind.ObjectMapper.class));
    private final GameRoom room = new GameRoom();
    private final GameRoomMember host = member(11L, GameConst.ROOM_ROLE_HOST);
    private final GameRoomMember guest = member(22L, GameConst.ROOM_ROLE_PLAYER);
    private final GameRoomMember watcher = member(33L, GameConst.ROOM_ROLE_SPECTATOR);

    private static GameRoomMember member(long id, String role) {
        GameRoomMember row = new GameRoomMember();
        row.setId(id);
        row.setRoomId(1L);
        row.setUserId(id);
        row.setRole(role);
        return row;
    }

    @BeforeEach
    void setup() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), GameRoom.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), GameRoomMember.class);
        room.setId(1L);
        room.setHostId(11L);
        room.setGameCode(GameConst.GAME_EIGHT_BALL);
        room.setStatus(GameConst.ROOM_PLAYING);
        room.setSeed(Long.MAX_VALUE);
        when(rooms.selectOne(any())).thenReturn(room);
        when(members.selectList(any())).thenReturn(List.of(host, guest, watcher));
        when(members.selectOne(any())).thenReturn(host);
    }

    @Test
    void hostCanRecordGuestVictoryAndRestoreExactRoundToken() {
        when(rooms.update(isNull(), any())).thenAnswer(call -> {
            LambdaUpdateWrapper<GameRoom> update = call.getArgument(1);
            assertTrue(update.getSqlSet().contains("winner_id"));
            assertTrue(update.getParamNameValuePairs().containsValue(guest.getUserId()));
            room.setStatus(GameConst.ROOM_FINISHED);
            room.setWinnerId(guest.getUserId());
            return 1;
        });
        when(rooms.selectById(1L)).thenReturn(room);
        var result = service.finish(11L, 7L, 1L, 0, 22L, Long.toString(Long.MAX_VALUE));
        assertEquals(Long.toString(Long.MAX_VALUE), result.get("roundToken"));
        assertEquals(2, result.get("maxSeatPlayers"));
        assertEquals(22L, result.get("winnerId"));
        assertEquals(GameConst.ROOM_FINISHED, result.get("status"));
        verify(rooms).update(isNull(), any());
    }

    @Test
    void rejectsGuestSelfReportSpectatorWinnerAndPreviousRound() {
        String token = Long.toString(Long.MAX_VALUE);
        assertThrows(BizException.class, () -> service.finish(22L, 7L, 1L, 0, 22L, token));
        assertThrows(BizException.class, () -> service.finish(11L, 7L, 1L, 0, 33L, token));
        assertThrows(BizException.class, () -> service.finish(11L, 7L, 1L, 0, 11L, "old"));
        verify(rooms, never()).update(isNull(), any());
    }

    @Test
    void hostKeepsAuthorityWhileGuestCanHandOverTheOtherSeat() {
        assertThrows(BizException.class, () -> service.yieldSeat(11L, 7L, 1L, 33L));
        when(members.selectOne(any())).thenReturn(guest, watcher);
        service.yieldSeat(22L, 7L, 1L, 33L);
        verify(members, times(2)).update(isNull(), any());
        verify(rooms, never()).update(isNull(), any());
    }

    @Test
    void rematchRestoresTheHostWhoLeftBeforeTheLastRoundEnded() {
        room.setStatus(GameConst.ROOM_FINISHED);
        when(rooms.update(isNull(), any())).thenReturn(1);
        when(members.selectOne(any())).thenReturn(null);
        service.again(11L, 7L, 1L);
        verify(members).insert(argThat((GameRoomMember row) ->
                row.getUserId().equals(11L) && row.getRole().equals(GameConst.ROOM_ROLE_HOST) && row.getReady() == 1));
    }
}
