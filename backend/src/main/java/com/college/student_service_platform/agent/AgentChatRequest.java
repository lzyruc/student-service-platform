package com.college.student_service_platform.agent;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** History is untrusted conversation text; it cannot supply server identity or tool messages. */
public record AgentChatRequest(String message, List<Turn> history) {
    public AgentChatRequest {
        if (message == null || message.isBlank() || message.length() > 2000)
            throw new IllegalArgumentException("message 必须为 1～2000 字符的非空文本");
        history = history == null ? List.of() : history;
        if (history.size() > 20 || history.stream().anyMatch(item -> item == null))
            throw new IllegalArgumentException("history 最多 20 条且不能包含空项");
        if (history.stream().mapToInt(item -> item.content().length()).sum() + message.length() > 20_000)
            throw new IllegalArgumentException("当前问题与历史消息总长度不能超过 20000 字符");
        history = List.copyOf(history);
    }

    public record Turn(String role, String content) {
        public Turn {
            if (!"user".equals(role) && !"assistant".equals(role))
                throw new IllegalArgumentException("历史消息只允许 user 或 assistant");
            if (content == null || content.isBlank() || content.length() > 4000)
                throw new IllegalArgumentException("历史消息必须为 1～4000 字符的非空文本");
        }
    }

    public static AgentChatRequest parse(JsonNode body) {
        object(body, Set.of("message", "history"));
        List<Turn> history = new ArrayList<>();
        if (body.has("history")) {
            JsonNode items = body.get("history");
            if (!items.isArray() || items.size() > 20) throw new IllegalArgumentException("history 必须为最多 20 条消息的数组");
            for (JsonNode item : items) {
                object(item, Set.of("role", "content"));
                history.add(new Turn(text(item, "role"), text(item, "content")));
            }
        }
        return new AgentChatRequest(text(body, "message"), history);
    }

    private static void object(JsonNode node, Set<String> fields) {
        if (node == null || !node.isObject()) throw new IllegalArgumentException("聊天参数必须为 JSON 对象");
        node.fieldNames().forEachRemaining(field -> {
            if (!fields.contains(field)) throw new IllegalArgumentException("聊天参数包含未允许的字段");
        });
    }
    private static String text(JsonNode node, String key) {
        if (!node.path(key).isTextual()) throw new IllegalArgumentException(key + " 必须为字符串");
        return node.path(key).textValue();
    }
}
