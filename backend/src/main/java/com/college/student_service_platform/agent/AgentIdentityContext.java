package com.college.student_service_platform.agent;

/** Internal identity captured after JWT verification and active student lookup. Never sent to the LLM. */
public record AgentIdentityContext(long studentId, String studentNo) {
    public AgentIdentityContext {
        if (studentId <= 0 || studentNo == null || studentNo.isBlank())
            throw new IllegalArgumentException("缺少可信学生身份");
    }
}
