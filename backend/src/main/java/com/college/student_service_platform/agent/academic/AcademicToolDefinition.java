package com.college.student_service_platform.agent.academic;

import com.fasterxml.jackson.databind.JsonNode;

/** Function metadata; the later model client can adapt this without exposing controllers. */
public record AcademicToolDefinition(String name, String description, JsonNode parameters) {
    public AcademicToolDefinition { parameters = parameters.deepCopy(); }
    @Override public JsonNode parameters() { return parameters.deepCopy(); }
}
