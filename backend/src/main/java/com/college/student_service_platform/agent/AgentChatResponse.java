package com.college.student_service_platform.agent;

import java.util.List;

public record AgentChatResponse(String answer, String status, int toolRounds, List<ToolTrace> toolCalls) {
    public AgentChatResponse { toolCalls = List.copyOf(toolCalls); }
    public record ToolTrace(String tool, String status, String dataStatus) { }
}
