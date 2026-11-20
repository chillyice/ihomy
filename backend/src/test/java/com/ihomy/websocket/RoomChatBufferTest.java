package com.ihomy.websocket;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 房间临时聊天缓冲:发号递增、容量截断、房间隔离、空闲清理。
 * 发号顺序是各端去重与排序的唯一依据,截断只保留最近 MAX_PER_ROOM 条。
 */
class RoomChatBufferTest {

    @Test
    void appendAssignsIncreasingSeqInOrder() {
        RoomChatBuffer buf = new RoomChatBuffer();
        buf.append(1L, 7L, "a");
        buf.append(1L, 8L, "b");

        List<RoomChatBuffer.Msg> recent = buf.recent(1L);
        assertThat(recent).extracting(RoomChatBuffer.Msg::seq).containsExactly(1L, 2L);
        assertThat(recent).extracting(RoomChatBuffer.Msg::text).containsExactly("a", "b");
        assertThat(recent.get(1).from()).isEqualTo(8L);
    }

    @Test
    void keepsOnlyNewestWhenOverCapacity() {
        RoomChatBuffer buf = new RoomChatBuffer();
        for (int i = 0; i < RoomChatBuffer.MAX_PER_ROOM + 10; i++) {
            buf.append(1L, 7L, "m" + i);
        }

        List<RoomChatBuffer.Msg> recent = buf.recent(1L);
        assertThat(recent).hasSize(RoomChatBuffer.MAX_PER_ROOM);
        assertThat(recent.get(0).text()).isEqualTo("m10");
        assertThat(recent.get(recent.size() - 1).seq())
                .isEqualTo(RoomChatBuffer.MAX_PER_ROOM + 10L);
    }

    @Test
    void roomsAreIsolated() {
        RoomChatBuffer buf = new RoomChatBuffer();
        buf.append(1L, 7L, "one");
        buf.append(2L, 7L, "two");

        assertThat(buf.recent(1L)).extracting(RoomChatBuffer.Msg::text).containsExactly("one");
        assertThat(buf.recent(2L)).extracting(RoomChatBuffer.Msg::text).containsExactly("two");
        assertThat(buf.recent(99L)).isEmpty();
    }

    @Test
    void idleRoomDroppedOnNextSweep() {
        long[] now = {1_000_000L}; // 高于扫描间隔,首次 append 就会触发一次清理
        RoomChatBuffer buf = new RoomChatBuffer(() -> now[0]);
        buf.append(1L, 7L, "old");

        now[0] += RoomChatBuffer.IDLE_TTL_MS + RoomChatBuffer.SWEEP_INTERVAL_MS + 1;
        buf.append(2L, 8L, "new");

        assertThat(buf.recent(1L)).isEmpty();
        assertThat(buf.recent(2L)).hasSize(1);
    }
}
