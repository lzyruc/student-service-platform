package com.college.student_service_platform.agent;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Set;

/** HTTP input: history and all client identity fields are rejected. */
public record ConversationChatRequest(long conversationId, String message) {
    public ConversationChatRequest {
        if (conversationId <= 0) throw new IllegalArgumentException("conversationId 必须为正整数");
        if (message == null || message.isBlank() || message.length() > ConversationContextBudget.MAX_USER_CHARS)
            throw new IllegalArgumentException("message 必须为 1～2000 字符的非空文本");
    }
    public static ConversationChatRequest parse(JsonNode body) {
        requireObject(body, Set.of("conversationId", "message"));
        JsonNode id = body.path("conversationId");
        if (!id.isIntegralNumber() || !id.canConvertToLong())
            throw new IllegalArgumentException("conversationId 必须为正整数");
        return new ConversationChatRequest(id.longValue(), text(body, "message"));
    }
    public static void requireObject(JsonNode body, Set<String> allowed) {
        if (body == null || !body.isObject()) throw new IllegalArgumentException("参数必须为 JSON 对象");
        body.fieldNames().forEachRemaining(field -> {
            if (!allowed.contains(field)) throw new IllegalArgumentException("参数包含未允许的字段");
        });
    }
    public static String text(JsonNode body, String field) {
        if (!body.path(field).isTextual()) throw new IllegalArgumentException(field + " 必须为字符串");
        return body.path(field).textValue();
    }
}
