package com.college.student_service_platform.service;

import com.college.student_service_platform.agent.*;
import com.college.student_service_platform.dto.AgentConversationDtos.ChatResult;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import java.util.function.Consumer;

@Service
public class ConversationChatService {
    private final AgentConversationService conversations;
    private final SingleAgentService agent;
    public ConversationChatService(AgentConversationService conversations, SingleAgentService agent) {
        this.conversations = conversations; this.agent = agent;
    }
    public record Prepared(AgentConversationService.ChatInput input, AgentRequestContext requestContext) {
        public Prepared {
            if (!input.identity().equals(requestContext.identity())
                    || input.conversationId() != requestContext.conversation().conversationId()
                    || !input.agentRequest().message().equals(requestContext.currentMessage())
                    || !input.agentRequest().history().equals(requestContext.conversation().history()))
                throw new com.college.student_service_platform.common.ApiException(org.springframework.http.HttpStatus.FORBIDDEN,
                        "请求上下文与已验证会话不一致");
        }
    }
    /** Ownership and authentication are checked before any model call or streaming headers. */
    public Prepared prepare(ConversationChatRequest input, HttpServletRequest request) {
        long startedAt = System.nanoTime();
        var owned = conversations.prepareChat(request, input.conversationId(), input.message());
        return new Prepared(owned, agent.prepareRequest(owned, startedAt));
    }
    public ChatResult chat(Prepared prepared, Consumer<AgentExecutionEvent> events) {
        try {
            var response = agent.chat(prepared.requestContext(), events);
            return conversations.saveExchange(prepared.input(), response);
        } catch (RuntimeException failure) {
            prepared.requestContext().fail();
            throw failure;
        }
    }
}
