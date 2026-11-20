package com.ihomy.service.assistant;

import com.ihomy.common.BizException;
import com.ihomy.common.ResultCode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The same field definitions drive both the model's schema and the server's validation. */
record AssistantToolSpec(String name, String description, boolean write, List<Field> fields) {
    record Field(String name, String type, boolean required, int maxLength, List<String> options, long max) {
        Map<String, Object> schema() {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("type", type);
            if (maxLength > 0) {
                out.put("minLength", 1);
                out.put("maxLength", maxLength);
            }
            if (!options.isEmpty()) out.put("enum", options);
            if (max > 0) {
                out.put("minimum", 1);
                out.put("maximum", max);
            }
            return out;
        }

        Object validate(Object value) {
            if ("string".equals(type) && value instanceof String s) {
                String trimmed = s.trim();
                if (!trimmed.isEmpty() && trimmed.length() <= maxLength
                        && (options.isEmpty() || options.contains(trimmed))) return trimmed;
            } else if (value instanceof Number n) {
                try {
                    BigDecimal d = new BigDecimal(n.toString());
                    if ("integer".equals(type)) {
                        long i = d.longValueExact();
                        if (i > 0 && i <= max) return i;
                    } else if ("number".equals(type) && d.signum() > 0
                            && d.compareTo(new BigDecimal("99999999.99")) <= 0 && d.stripTrailingZeros().scale() <= 2) {
                        return d.setScale(2);
                    }
                } catch (ArithmeticException | NumberFormatException ignored) {
                    // Report the same validation failure for all out-of-range numbers.
                }
            } else if ("boolean".equals(type) && value instanceof Boolean) return value;
            throw invalid();
        }
    }

    Map<String, Object> descriptor() {
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        for (Field field : fields) {
            properties.put(field.name(), field.schema());
            if (field.required()) required.add(field.name());
        }
        return Map.of("name", name, "description", description, "inputSchema",
                Map.of("type", "object", "properties", properties, "required", required, "additionalProperties", false));
    }

    Map<String, Object> validate(Map<String, Object> input) {
        if (input == null) throw invalid();
        Map<String, Object> out = new LinkedHashMap<>();
        for (Field field : fields) {
            if (input.containsKey(field.name())) out.put(field.name(), field.validate(input.get(field.name())));
            else if (field.required()) throw invalid();
        }
        if (out.size() != input.size()) throw invalid();
        return out;
    }

    static BizException invalid() {
        return new BizException(ResultCode.BAD_REQUEST, "操作信息不完整或格式有误，请核对后重试");
    }

    static Field text(String name, int max, boolean required) {
        return new Field(name, "string", required, max, List.of(), 0);
    }
    static Field choice(String name, boolean required, String... options) {
        return new Field(name, "string", required, 64, List.of(options), 0);
    }
    static Field integer(String name, long max, boolean required) {
        return new Field(name, "integer", required, 0, List.of(), max);
    }
    static Field money() {
        return new Field("amount", "number", true, 0, List.of(), 0);
    }
    static Field flag(String name) {
        return new Field(name, "boolean", false, 0, List.of(), 0);
    }
}
