package com.college.student_service_platform.agent.academic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Set;

/** Validate runtime input as well as publishing schemas; Jackson coercion is intentionally avoided. */
final class AcademicToolArguments {
    private AcademicToolArguments() { }

    static void object(JsonNode args, String... allowed) {
        if (args == null || !args.isObject()) throw new IllegalArgumentException("工具参数必须为 JSON 对象");
        Set<String> names = Set.of(allowed);
        args.fieldNames().forEachRemaining(name -> {
            if (!names.contains(name)) throw new IllegalArgumentException("工具参数包含未允许的字段");
        });
    }

    static String text(JsonNode args, String key, String defaultValue) {
        if (!args.has(key)) return defaultValue;
        if (!args.get(key).isTextual()) throw new IllegalArgumentException(key + " 必须为字符串");
        return args.get(key).textValue();
    }

    static int integer(JsonNode args, String key, int defaultValue) {
        if (!args.has(key)) return defaultValue;
        JsonNode value = args.get(key);
        if (!value.isIntegralNumber() || !value.canConvertToInt())
            throw new IllegalArgumentException(key + " 必须为整数");
        return value.intValue();
    }

    static ObjectNode schema() {
        ObjectNode schema = JsonNodeFactory.instance.objectNode();
        schema.put("type", "object");
        schema.putObject("properties");
        schema.putArray("required");
        schema.put("additionalProperties", false);
        return schema;
    }

    static ObjectNode integerSchema(int minimum, int maximum, int defaultValue) {
        return JsonNodeFactory.instance.objectNode().put("type", "integer")
                .put("minimum", minimum).put("maximum", maximum).put("default", defaultValue);
    }
}
