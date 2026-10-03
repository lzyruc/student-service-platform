package com.college.student_service_platform.agent.academic;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Model-facing contracts: explicitly selected evidence, never DB rows or the raw Python response. */
public final class AcademicToolDtos {
    private AcademicToolDtos() { }

    public record EmptyInput() { }

    public record RecentInput(String semester, int limit) {
        public RecentInput {
            if (!"latest".equals(semester) && !"previous".equals(semester))
                throw new IllegalArgumentException("semester 只允许 latest 或 previous");
            if (limit < 1 || limit > 10) throw new IllegalArgumentException("limit 必须为 1～10 的整数");
        }
    }

    public record TrendInput(int lastSemesters) {
        public TrendInput {
            if (lastSemesters < 2 || lastSemesters > 8)
                throw new IllegalArgumentException("lastSemesters 必须为 2～8 的整数");
        }
    }

    public record Source(String major, String grade, LocalDateTime transcriptUploadedAt,
                         String trainingPlanVersion, LocalDateTime trainingPlanUpdatedAt) { }

    public record ContextOutput(String status, boolean profileAvailable, String major, String grade,
                                boolean transcriptExists, boolean transcriptAvailable, LocalDateTime transcriptUploadedAt,
                                boolean trainingPlanExists, String trainingPlanVersion, LocalDateTime trainingPlanUpdatedAt,
                                List<String> nextActions) {
        public ContextOutput { nextActions = List.copyOf(nextActions); }
    }

    public record FailedCourse(String name, String score) { }

    public record AssessmentOutput(Source source, BigDecimal totalEarnedCredits,
                                   boolean officialGpaAvailable, BigDecimal officialGpa, String warningLevel,
                                   Integer completedSemester, Integer issueCourseCount, Integer coreCourseCount,
                                   List<FailedCourse> failedCourses, List<String> completedCoreCourses,
                                   List<String> missingCoreCourses, List<String> pendingCoreCourses,
                                   List<String> unscheduledCoreCourses, List<String> unknownScoreCourses,
                                   List<String> courseSuggestions, List<String> analysisNotes) {
        public AssessmentOutput {
            failedCourses = List.copyOf(failedCourses);
            completedCoreCourses = List.copyOf(completedCoreCourses);
            missingCoreCourses = List.copyOf(missingCoreCourses);
            pendingCoreCourses = List.copyOf(pendingCoreCourses);
            unscheduledCoreCourses = List.copyOf(unscheduledCoreCourses);
            unknownScoreCourses = List.copyOf(unknownScoreCourses);
            courseSuggestions = List.copyOf(courseSuggestions);
            analysisNotes = List.copyOf(analysisNotes);
        }
    }

    public record Rules(BigDecimal lowScoreMin, BigDecimal lowScoreMaxExclusive, List<String> semesterOrder,
                        String weightedGpa, String averageNumericScore, String trendDirection) {
        public Rules { semesterOrder = List.copyOf(semesterOrder); }
    }

    public record FocusCourse(String name, String score, BigDecimal numericScore, BigDecimal credit,
                              Integer page, Boolean passed, String currentFailureStatus, String reason) { }

    public record SemesterChange(String semester, BigDecimal weightedGpaDelta, String gpaDirection,
                                 BigDecimal averageNumericScoreDelta, String numericScoreDirection,
                                 Integer failedCourseCountDelta) { }

    public record SemesterSummary(String semester, Integer courseCount, Integer validScoreCourseCount,
                                  Integer passedCourseCount, Integer failedCourseCount, Integer unknownScoreCourseCount,
                                  BigDecimal attemptedCredits, Integer validCreditCourseCount,
                                  BigDecimal weightedGpa, Integer gpaCourseCount, BigDecimal gpaCredits,
                                  BigDecimal gpaCourseCoverage, BigDecimal gpaCreditCoverage,
                                  BigDecimal averageNumericScore, Integer numericScoreCourseCount,
                                  BigDecimal numericScoreCoverage, SemesterChange changeFromPrevious,
                                  List<String> analysisNotes) {
        public SemesterSummary { analysisNotes = List.copyOf(analysisNotes); }
    }

    public record RecentOutput(Source source, String status, String message, String requestedSemester,
                               String semester, int limit, Rules rules, SemesterSummary summary,
                               int focusCourseCount, boolean truncated, List<FocusCourse> focusCourses,
                               List<String> analysisNotes) {
        public RecentOutput {
            focusCourses = List.copyOf(focusCourses);
            analysisNotes = List.copyOf(analysisNotes);
        }
    }

    public record TrendOutput(Source source, String status, int requestedSemesterCount, int semesterCount,
                              Rules rules, List<SemesterSummary> semesters, int gpaComparisonCount, String gpaTrendStatus,
                              int numericScoreComparisonCount, String numericScoreTrendStatus, List<String> analysisNotes) {
        public TrendOutput {
            semesters = List.copyOf(semesters);
            analysisNotes = List.copyOf(analysisNotes);
        }
    }
}
