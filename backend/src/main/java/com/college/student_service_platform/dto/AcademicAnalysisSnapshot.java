package com.college.student_service_platform.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.Objects;

/** Internal evidence for one analysis; tools must project their own DTOs from this snapshot. */
public record AcademicAnalysisSnapshot(
        String studentNo,
        Long transcriptFileId,
        String transcriptOriginalName,
        LocalDateTime transcriptUploadedAt,
        Long trainingPlanId,
        String major,
        String grade,
        String trainingPlanVersion,
        LocalDateTime trainingPlanUpdatedAt,
        JsonNode response
) {
    public AcademicAnalysisSnapshot {
        Objects.requireNonNull(response, "analysis response");
        JsonNode data = response.path("data");
        if (!data.isObject() || !data.path("report").isObject()
                || data.path("report").isEmpty() || !data.path("courses").isArray()
                || data.path("courses").isEmpty()) {
            throw new IllegalStateException("学业分析服务未返回有效课程和完整报告");
        }
        response = response.deepCopy();
    }

    @Override
    public JsonNode response() {
        return response.deepCopy();
    }

    public JsonNode courses() {
        return response.path("data").path("courses").deepCopy();
    }

    public JsonNode report() {
        return response.path("data").path("report").deepCopy();
    }

    public JsonNode statistics() {
        return response.path("data").path("statistics").deepCopy();
    }
}
