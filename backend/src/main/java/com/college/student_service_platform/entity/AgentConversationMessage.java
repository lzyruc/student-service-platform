package com.college.student_service_platform.entity;

import java.time.LocalDateTime;

/** Only USER and ASSISTANT are public conversation messages. */
public record AgentConversationMessage(long id, long conversationId, String role, String content,
                                       LocalDateTime createdAt) { }
