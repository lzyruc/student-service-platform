package com.college.student_service_platform.agent.academic;

import com.college.student_service_platform.service.AcademicAnalysisReadContext;
import com.fasterxml.jackson.databind.JsonNode;

/** One stable, typed, read-only business capability. */
public interface AcademicTool<I, O> {
    AcademicToolDefinition definition();
    I parseInput(JsonNode arguments);
    O execute(I input, AcademicAnalysisReadContext context);

    default O invoke(JsonNode arguments, AcademicAnalysisReadContext context) {
        return execute(parseInput(arguments), context);
    }
}
