package com.ihomy.garden.core;

import java.security.SecureRandom;
import java.time.Instant;

/**
 * ULID 生成(Crockford Base32,26 字符):48bit 毫秒时间戳 + 80bit 随机数。
 * player_uid / op_id / world_id 的唯一标识来源(需求设计说明书 §1.3/§5.3)。
 * 标识生成不属于玩法判定(NFR-02 确定性针对核心纯函数),允许 SecureRandom。
 */
public final class Ulid {

    private static final char[] ENCODING = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private Ulid() {
    }

    /** 生成当前时刻的 ULID */
    public static String next() {
        return of(Instant.now().toEpochMilli());
    }

    /** 指定毫秒时间戳生成(测试用,保证可复现) */
    public static String of(long epochMilli) {
        long ts = Math.max(0, epochMilli);
        char[] out = new char[26];
        // 48bit 时间戳 → 前 10 字符(每字符 5bit,首字符只取高 3bit)
        out[0] = ENCODING[(int) ((ts >>> 45) & 0x07)];
        out[1] = ENCODING[(int) ((ts >>> 40) & 0x1F)];
        out[2] = ENCODING[(int) ((ts >>> 35) & 0x1F)];
        out[3] = ENCODING[(int) ((ts >>> 30) & 0x1F)];
        out[4] = ENCODING[(int) ((ts >>> 25) & 0x1F)];
        out[5] = ENCODING[(int) ((ts >>> 20) & 0x1F)];
        out[6] = ENCODING[(int) ((ts >>> 15) & 0x1F)];
        out[7] = ENCODING[(int) ((ts >>> 10) & 0x1F)];
        out[8] = ENCODING[(int) ((ts >>> 5) & 0x1F)];
        out[9] = ENCODING[(int) (ts & 0x1F)];
        // 80bit 随机数 → 后 16 字符
        for (int i = 10; i < 26; i += 2) {
            int v = RANDOM.nextInt(1024); // 10bit
            out[i] = ENCODING[(v >>> 5) & 0x1F];
            if (i + 1 < 26) {
                out[i + 1] = ENCODING[v & 0x1F];
            }
        }
        return new String(out);
    }

    /** 校验格式(26 位 Crockford Base32:数字 + A–Z 去掉 I/L/O/U) */
    public static boolean isValid(String s) {
        if (s == null || s.length() != 26) {
            return false;
        }
        for (int i = 0; i < 26; i++) {
            char c = s.charAt(i);
            boolean ok = (c >= '0' && c <= '9')
                    || (c >= 'A' && c <= 'Z' && c != 'I' && c != 'L' && c != 'O' && c != 'U');
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    /** Idempotency-Key 归一:合法即用,否则服务端新生成 */
    public static String orNext(String candidate) {
        return isValid(candidate) ? candidate : next();
    }
}
