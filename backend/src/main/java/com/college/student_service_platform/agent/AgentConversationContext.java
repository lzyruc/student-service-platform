package com.college.student_service_platform.agent;

import java.util.List;

/** Immutable, owned conversation text. Persistence does not make its claims business facts. */
public record AgentConversationContext(long conversationId, List<AgentChatRequest.Turn> history) {
    public AgentConversationContext {
        if (conversationId <= 0) throw new IllegalArgumentException("缺少已验证归属的会话");
        history = List.copyOf(history);
    }
}
