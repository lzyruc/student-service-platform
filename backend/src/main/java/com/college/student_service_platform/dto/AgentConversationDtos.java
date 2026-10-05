package com.college.student_service_platform.dto;

import com.college.student_service_platform.agent.AgentChatResponse;
import com.college.student_service_platform.entity.AgentConversation;
import com.college.student_service_platform.entity.AgentConversationMessage;
import java.time.LocalDateTime;
import java.util.List;

/** Ownership is deliberately not a client-controlled DTO field. */
public final class AgentConversationDtos {
    private AgentConversationDtos() { }
    public record Summary(long id, String title, LocalDateTime createdAt, LocalDateTime updatedAt) {
        public static Summary from(AgentConversation row) {
            return new Summary(row.id(), row.title(), row.createdAt(), row.updatedAt());
        }
    }
    public record Message(long id, String role, String content, LocalDateTime createdAt) {
        public static Message from(AgentConversationMessage row) {
            return new Message(row.id(), row.role(), row.content(), row.createdAt());
        }
    }
    public record Page(List<Summary> records, long total, int page, int pageSize) { }
    public record Detail(Summary conversation, List<Message> messages, boolean hasMore, Long nextBeforeMessageId) { }
    public record ChatResult(long conversationId, String answer, String status, int toolRounds,
                             List<AgentChatResponse.ToolTrace> toolCalls) {
        public static ChatResult from(long id, AgentChatResponse response) {
            return new ChatResult(id, response.answer(), response.status(), response.toolRounds(), response.toolCalls());
        }
    }
}
