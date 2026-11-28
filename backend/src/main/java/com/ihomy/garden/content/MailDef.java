package com.ihomy.garden.content;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * 每日信封文案(content/mail.json):首封、第 1–7 天教学脚本与四季短笺。
 * 种子的确定性推导在 GardenMail,这里只存文案(玩法稿 §18.7)。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MailDef(
        @JsonProperty("first_letter") FirstLetter firstLetter,
        @JsonProperty("script") List<ScriptLine> script,
        @JsonProperty("seasonal") Map<String, List<String>> seasonal,
        @JsonProperty("season_names") Map<String, String> seasonNames,
        @JsonProperty("signature") String signature) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FirstLetter(
            @JsonProperty("type") String type,
            @JsonProperty("text") String text,
            @JsonProperty("signature") String signature) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ScriptLine(
            @JsonProperty("day") int day,
            @JsonProperty("type") String type,
            @JsonProperty("text") String text) {
    }
}
