package com.college.student_service_platform.dto;

import java.time.LocalDateTime;

/** Read-only prerequisite check, without parsing the transcript. No identity or file path. */
public record AcademicContextSnapshot(
        boolean profileAvailable, String major, String grade,
        boolean transcriptExists, boolean transcriptAvailable, LocalDateTime transcriptUploadedAt,
        boolean trainingPlanExists, String trainingPlanVersion, LocalDateTime trainingPlanUpdatedAt
) {
    public static AcademicContextSnapshot fromAnalysis(AcademicAnalysisSnapshot snapshot) {
        return new AcademicContextSnapshot(true, snapshot.major(), snapshot.grade(), true, true,
                snapshot.transcriptUploadedAt(), true, snapshot.trainingPlanVersion(), snapshot.trainingPlanUpdatedAt());
    }
}
