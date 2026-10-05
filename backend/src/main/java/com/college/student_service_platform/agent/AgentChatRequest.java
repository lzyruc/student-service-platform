package com.college.student_service_platform.agent;

import java.util.List;

/** Model-facing input. HTTP controllers build history from owned database messages only. */
public record AgentChatRequest(String message, List<Turn> history) {
    public AgentChatRequest {
        if (message == null || message.isBlank() || message.length() > ConversationContextBudget.MAX_USER_CHARS)
            throw new IllegalArgumentException("message 必须为 1～2000 字符的非空文本");
        history = history == null ? List.of() : history;
        if (history.size() > ConversationContextBudget.MAX_MESSAGES || history.stream().anyMatch(item -> item == null))
            throw new IllegalArgumentException("history 最多 20 条且不能包含空项");
        if (history.stream().mapToInt(item -> item.content().length()).sum() + message.length() > ConversationContextBudget.MAX_CONTEXT_CHARS)
            throw new IllegalArgumentException("当前问题与历史消息总长度不能超过 20000 字符");
        history = List.copyOf(history);
    }

    public record Turn(String role, String content) {
        public Turn {
            if (!"user".equals(role) && !"assistant".equals(role))
                throw new IllegalArgumentException("历史消息只允许 user 或 assistant");
            if (content == null || content.isBlank() || content.length() > ConversationContextBudget.MAX_CONTENT_CHARS)
                throw new IllegalArgumentException("历史消息必须为 1～4000 字符的非空文本");
        }
    }

}
