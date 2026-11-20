package com.ihomy.websocket;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * 「一起玩」房间临时聊天的内存环形缓冲:roomId → 最近 50 条消息。
 *
 * <p>房间聊天按产品口径「只存内存」:不建表、不落库,服务重启或房间散掉即消失;
 * 中途进房/刷新页面靠 chat:sync 回放最近消息补齐。发号(seq)也在这里完成,
 * 服务端把同一条消息(带 seq)连同发送者一起广播,所有端看到的顺序与去重键一致。
 *
 * <p>内存按家庭场景的并发量估算足够;房间表做两级清理:空闲超过 2h 的整间丢弃、
 * 房间总数超上限时按最久未活动淘汰,避免长期运行后残留大量散房。
 */
@Component
public class RoomChatBuffer {

    /** 单房间保留条数(前端 chat:sync 直接吃这一份) */
    public static final int MAX_PER_ROOM = 50;
    /** 单条消息上限(码点无关,按 UTF-16 长度截断校验) */
    public static final int MAX_TEXT_LEN = 200;
    /** 空闲多久整间丢弃 */
    static final long IDLE_TTL_MS = 2 * 60 * 60 * 1000L;
    /** 房间总数上限:超了按最久未活动淘汰 */
    static final int MAX_ROOMS = 500;
    /** 清理扫描间隔:不是每条消息都遍历房间表 */
    static final long SWEEP_INTERVAL_MS = 5 * 60 * 1000L;

    /** 一条房间消息(seq 房间内自增,at 为服务端毫秒时间戳) */
    public record Msg(long seq, long from, String text, long at) {
    }

    private static final class Room {
        final ArrayDeque<Msg> messages = new ArrayDeque<>();
        long seq = 0;
        long lastAt = 0;

        Room(long now) {
            this.lastAt = now;
        }
    }

    private final Map<Long, Room> rooms = new ConcurrentHashMap<>();
    private final LongSupplier clock;
    private volatile long lastSweepAt = 0;

    public RoomChatBuffer() {
        this(System::currentTimeMillis);
    }

    /** 测试用:注入可控时钟验证空闲清理 */
    RoomChatBuffer(LongSupplier clock) {
        this.clock = clock;
    }

    /** 追加一条消息并入队;返回带 seq/at 的服务端视图 */
    public Msg append(Long roomId, Long from, String text) {
        long now = clock.getAsLong();
        sweepIfDue(now);
        Room room = rooms.computeIfAbsent(roomId, k -> new Room(now));
        synchronized (room) {
            Msg msg = new Msg(++room.seq, from, text, now);
            room.messages.addLast(msg);
            while (room.messages.size() > MAX_PER_ROOM) {
                room.messages.removeFirst();
            }
            room.lastAt = now;
            return msg;
        }
    }

    /** 最近消息副本(按时间正序),房间不存在返回空表 */
    public List<Msg> recent(Long roomId) {
        Room room = rooms.get(roomId);
        if (room == null) {
            return List.of();
        }
        synchronized (room) {
            return List.copyOf(room.messages);
        }
    }

    /** 惰性清理:空闲超 TTL 的整间丢弃,仍超量的按最久未活动淘汰 */
    private void sweepIfDue(long now) {
        if (now - lastSweepAt < SWEEP_INTERVAL_MS) {
            return;
        }
        lastSweepAt = now;
        rooms.entrySet().removeIf(e -> now - e.getValue().lastAt > IDLE_TTL_MS);
        int overflow = rooms.size() - MAX_ROOMS;
        if (overflow <= 0) {
            return;
        }
        List<Long> stale = new ArrayList<>();
        rooms.entrySet().stream()
                .sorted(Comparator.comparingLong(e -> e.getValue().lastAt))
                .limit(overflow)
                .forEach(e -> stale.add(e.getKey()));
        stale.forEach(rooms::remove);
    }
}
