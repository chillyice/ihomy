package com.ihomy.controller;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 健康检查探活逻辑单测(纯逻辑:DataSource/Redis 用 Mockito 桩,不起 Spring/DB/Redis)。
 * 覆盖 UP 与 DEGRADED 两条分支——DEGRADED 若不测,只能靠在真实环境停依赖来验证。
 */
class HealthControllerTest {

    @SuppressWarnings("unchecked")
    private static StringRedisTemplate redisReturning(String pong) {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisCallback.class))).thenReturn(pong);
        return redis;
    }

    private static DataSource dataSourceValid(boolean valid) throws Exception {
        DataSource ds = mock(DataSource.class);
        Connection conn = mock(Connection.class);
        when(ds.getConnection()).thenReturn(conn);
        when(conn.isValid(anyInt())).thenReturn(valid);
        return ds;
    }

    @SuppressWarnings("unchecked")
    private static String statusOf(Map<String, Object> components, String name) {
        return (String) ((Map<String, Object>) components.get(name)).get("status");
    }

    @Test
    void allDependenciesUp() throws Exception {
        HealthController controller = new HealthController(dataSourceValid(true), redisReturning("PONG"));

        Map<String, Object> components = controller.probeComponents();

        assertThat(statusOf(components, "db")).isEqualTo("UP");
        assertThat(statusOf(components, "redis")).isEqualTo("UP");
    }

    @Test
    void databaseDownDegrades() throws Exception {
        DataSource ds = mock(DataSource.class);
        when(ds.getConnection()).thenThrow(new SQLException("connection refused"));
        HealthController controller = new HealthController(ds, redisReturning("PONG"));

        Map<String, Object> components = controller.probeComponents();

        assertThat(statusOf(components, "db")).isEqualTo("DOWN");
        assertThat(statusOf(components, "redis")).isEqualTo("UP");
        assertThat(controller.detail().getData().get("status")).isEqualTo("DEGRADED");
    }

    @Test
    void redisDownDegrades() throws Exception {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisCallback.class))).thenThrow(new RuntimeException("redis unavailable"));
        HealthController controller = new HealthController(dataSourceValid(true), redis);

        Map<String, Object> components = controller.probeComponents();

        assertThat(statusOf(components, "redis")).isEqualTo("DOWN");
        assertThat(controller.detail().getData().get("status")).isEqualTo("DEGRADED");
    }

    @Test
    void redisWithoutPongIsNotUp() throws Exception {
        // 连得上但没回 PONG(或返回 null)同样算不可用,不能误报 UP
        HealthController controller = new HealthController(dataSourceValid(true), redisReturning(null));

        assertThat(statusOf(controller.probeComponents(), "redis")).isEqualTo("DOWN");
    }
}
