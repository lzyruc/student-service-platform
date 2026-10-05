package com.college.student_service_platform.agent;
/** Public execution facts only. Never model reasoning, arguments, identity or transport errors. */
public record AgentExecutionEvent(String type, Object data) {
    public record Progress(String id, String label, String state, String detail) { }
    public static AgentExecutionEvent progress(String id, String label, String state, String detail) {
        return new AgentExecutionEvent("progress", new Progress(id, label, state, detail));
    }
}
