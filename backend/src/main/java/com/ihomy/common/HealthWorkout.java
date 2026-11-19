package com.ihomy.common;

import java.util.Locale;
import java.util.Set;

/**
 * 训练类型归一:上报端可传 HKWorkoutActivityTypeXxx 原码或直接传类型码,
 * 这里折成有限集合(含义在 sys_dict_item 的 health_workout_type 组,前端 utils/healthMetrics.js 同口径)。
 * 认不出的归 OTHER——宁可显示「其他运动」,也不让一条陌生类型把整条上报拒掉。
 */
public final class HealthWorkout {

    private HealthWorkout() {
    }

    public static final Set<String> TYPES = Set.of(
            "WALKING", "RUNNING", "CYCLING", "HIKING", "SWIMMING", "BADMINTON", "BASKETBALL",
            "FOOTBALL", "STRENGTH", "YOGA", "CARDIO", "MIND_BODY", "OTHER");

    public static String normalize(String raw) {
        if (raw == null) {
            return "OTHER";
        }
        String s = raw.trim().toUpperCase(Locale.ROOT).replace("HKWORKOUTACTIVITYTYPE", "");
        if (s.isEmpty()) {
            return "OTHER";
        }
        if (TYPES.contains(s)) {
            return s;
        }
        if (s.contains("BADMINTON")) return "BADMINTON";
        if (s.contains("BASKETBALL")) return "BASKETBALL";
        if (s.contains("SOCCER") || s.contains("FOOTBALL")) return "FOOTBALL";
        if (s.contains("SWIM")) return "SWIMMING";
        if (s.contains("STRENGTH") || s.contains("WEIGHTLIFTING") || s.contains("CORE_TRAINING")) return "STRENGTH";
        if (s.contains("YOGA")) return "YOGA";
        if (s.contains("MIND") || s.contains("TAICHI") || s.contains("PILATES")) return "MIND_BODY";
        if (s.contains("CYCLE") || s.contains("BIKE")) return "CYCLING";
        if (s.contains("HIKE") || s.contains("CLIMB")) return "HIKING";
        if (s.contains("RUN") || s.contains("JOG")) return "RUNNING";
        if (s.contains("WALK")) return "WALKING";
        if (s.contains("CARDIO") || s.contains("HIIT") || s.contains("CROSS") || s.contains("ROW")) return "CARDIO";
        return "OTHER";
    }
}
