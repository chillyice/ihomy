package com.ihomy.common;

import com.ihomy.dto.HealthIngestDTO;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 上报请求体解析:快捷指令把请求体设成 JSON 时,粘贴整段 JSON 会变成 {"": "<json>"};
 * 这里既保证正常 JSON 直通,也保证那种写法能拆开,且非法请求给 400 提示而不是 500。
 */
class HealthIngestJsonTest {

    private final ObjectMapper om = new ObjectMapper();
    private static final String BODY = """
            {"provider":"SHORTCUT","timezone":"+08:00",
             "samples":[{"metric":"STEPS","period":"DAY","date":"2026-11-18","value":8240,"source":"APPLE"}]}
            """;

    @Test
    void parsesPlainJson() {
        HealthIngestDTO dto = HealthIngestJson.parse(om, BODY);
        assertThat(dto.getProvider()).isEqualTo("SHORTCUT");
        assertThat(dto.getSamples()).hasSize(1);
        assertThat(dto.getSamples().get(0).getMetric()).isEqualTo("STEPS");
    }

    @Test
    void unwrapsJsonPastedAsEmptyKeyValue() {
        String wrapped = "{\"\":\"" + BODY.replace("\"", "\\\"").replace("\n", "\\n") + "\"}";
        HealthIngestDTO dto = HealthIngestJson.parse(om, wrapped);
        assertThat(dto.getProvider()).isEqualTo("SHORTCUT");
        assertThat(dto.getTimezone()).isEqualTo("+08:00");
    }

    @Test
    void unwrapsJsonPastedAsKeyWithEmptyValue() {
        String wrapped = "{\"" + BODY.replace("\"", "\\\"").replace("\n", "\\n") + "\":\"\"}";
        assertThat(HealthIngestJson.parse(om, wrapped).getProvider()).isEqualTo("SHORTCUT");
    }

    @Test
    void keepsSingleKeyObjectThatIsNotWrappedJson() {
        HealthIngestDTO dto = HealthIngestJson.parse(om, "{\"provider\":\"SHORTCUT\"}");
        assertThat(dto.getProvider()).isEqualTo("SHORTCUT");
    }

    @Test
    void rejectsEmptyBody() {
        assertThatThrownBy(() -> HealthIngestJson.parse(om, "   "))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("请求体是空的");
        assertThatThrownBy(() -> HealthIngestJson.parse(om, null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("请求体是空的");
    }

    @Test
    void rejectsNonJsonBody() {
        assertThatThrownBy(() -> HealthIngestJson.parse(om, "provider=SHORTCUT&timezone=+08:00"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不是合法的 JSON");
        assertThatThrownBy(() -> HealthIngestJson.parse(om, "[1,2,3]"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不是合法的 JSON");
    }
}
