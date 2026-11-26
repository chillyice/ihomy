package com.ihomy.service;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;

class SunServiceTest {
    private final SunService service = new SunService(mock(StringRedisTemplate.class, RETURNS_DEEP_STUBS));

    @Test
    @SuppressWarnings("unchecked")
    void familyCoordinatesSetLocalDateLengthAndDistinguishBothOccurrencesOfAnHour() {
        String[] family = { "40.71", "-74.0", "纽约" };
        var fall = service.getSunInfo(null, LocalDate.of(2026, 11, 1), family);
        assertThat(fall.get("timezone")).isEqualTo("America/New_York");
        assertThat((List<?>) fall.get("slots")).hasSize(288);
        var slots = (List<Map<String, Object>>) fall.get("timeline");
        assertThat(slots).hasSize(300);
        assertThat(slots.get(12).get("time")).isEqualTo("01:00");
        assertThat(slots.get(24).get("time")).isEqualTo("01:00");
        assertThat((long) slots.get(24).get("epochMillis") - (long) slots.get(12).get("epochMillis")).isEqualTo(3_600_000L);
        var midnight = (Map<String, Object>) fall.get("nextMidnight");
        assertThat((long) midnight.get("epochMillis") - (long) slots.getLast().get("epochMillis")).isEqualTo(300_000L);
        var spring = service.getSunInfo(null, LocalDate.of(2026, 3, 8), family);
        assertThat((List<?>) spring.get("slots")).hasSize(288);
        var springSlots = (List<Map<String, Object>>) spring.get("timeline");
        assertThat(springSlots).hasSize(276);
        assertThat(springSlots.get(24).get("time")).isEqualTo("03:00");
    }
}
