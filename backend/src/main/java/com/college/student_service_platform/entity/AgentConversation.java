package com.college.student_service_platform.entity;

import java.time.LocalDateTime;

public record AgentConversation(long id, long studentId, String title,
                                LocalDateTime createdAt, LocalDateTime updatedAt) { }
