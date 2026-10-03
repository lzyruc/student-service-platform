package com.college.student_service_platform.agent.academic;

/** Execution status is separate from data sufficiency inside the output DTO. */
public record AcademicToolResult<T>(String tool, String status, String message, T data) { }
