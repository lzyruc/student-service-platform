package com.college.student_service_platform;

import com.college.student_service_platform.dto.AcademicAnalysisSnapshot;
import com.college.student_service_platform.service.AcademicAnalysisReadContext;
import com.college.student_service_platform.service.AcademicStatisticsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AcademicStatisticsServiceTest {
    private final AcademicStatisticsService service = new AcademicStatisticsService();
    private final ObjectMapper mapper = new ObjectMapper();
    private AcademicAnalysisReadContext context;
    private ObjectNode response;

    @BeforeEach
    void setUp() throws Exception {
        context = mock(AcademicAnalysisReadContext.class);
        response = (ObjectNode) mapper.readTree("""
                {"data":{"courses":[{"name":"程序设计","score":"65"}],"report":{"warning_level":"正常"},
                  "statistics":{"schema_version":1,
                    "rules":{"low_score_min":60,"low_score_max_exclusive":70},
                    "analysis_notes":["成绩单年级与培养方案不同"],
                    "latest_semester":"2025-2026学年秋季学期",
                    "previous_semester":"2024-2025学年春季学期",
                    "valid_semesters":["2024-2025学年秋季学期","2024-2025学年春季学期","2025-2026学年秋季学期"],
                    "semesters":[
                      {"semester":"2024-2025学年秋季学期","weighted_gpa":2,"focus_courses":[],
                       "change_from_previous":null},
                      {"semester":"2024-2025学年春季学期","weighted_gpa":3,"focus_courses":[],
                       "change_from_previous":{"semester":"2024-2025学年秋季学期","weighted_gpa_delta":1,"average_numeric_score_delta":10}},
                      {"semester":"2025-2026学年秋季学期","weighted_gpa":3.5,
                       "focus_courses":[{"name":"程序设计","reason":"LOW_NUMERIC_SCORE"},{"name":"微积分","reason":"LOW_NUMERIC_SCORE"}],
                       "change_from_previous":{"semester":"2024-2025学年春季学期","weighted_gpa_delta":0.5,"average_numeric_score_delta":5}}
                    ]}}}
                """);
        useResponse();
    }

    private void useResponse() {
        when(context.getSnapshot()).thenReturn(new AcademicAnalysisSnapshot(
                "20260001", 22L, "grades.pdf", null, 33L, "计算机科学与技术", "2026", "v1", null, response));
    }

    @Test
    void recentSelectionUsesCanonicalLabelAndLimitsFocusWithoutMutatingSnapshot() {
        JsonNode first = service.recentPerformance(context, "latest", 1);
        assertEquals("2025-2026学年秋季学期", first.path("semester").asText());
        assertEquals(2, first.path("focus_course_count").asInt());
        assertEquals(1, first.path("focus_courses").size());
        assertTrue(first.path("truncated").asBoolean());
        assertEquals("成绩单年级与培养方案不同", first.path("analysis_notes").get(0).asText());
        ((ObjectNode) first.path("focus_courses").get(0)).put("name", "tampered");
        JsonNode second = service.recentPerformance(context, "latest", 10);
        assertEquals(2, second.path("focus_courses").size());
        assertEquals("程序设计", second.path("focus_courses").get(0).path("name").asText());
        assertFalse(second.path("truncated").asBoolean());
    }

    @Test
    void previousUsesActualPreviousRecordedSemesterAndMissingPeriodIsExplicit() {
        assertEquals("2024-2025学年春季学期",
                service.recentPerformance(context, "previous", 5).path("semester").asText());
        ((ObjectNode) response.path("data").path("statistics")).putNull("previous_semester");
        useResponse();
        JsonNode result = service.recentPerformance(context, "previous", 5);
        assertEquals("INSUFFICIENT_DATA", result.path("status").asText());
        assertTrue(result.path("semester").isNull());
    }

    @Test
    void trendSelectsLastPeriodsAndDoesNotCompareAgainstAnOmittedSemester() {
        JsonNode result = service.trend(context, 2);
        assertEquals(2, result.path("semester_count").asInt());
        assertEquals("2024-2025学年春季学期", result.path("semesters").get(0).path("semester").asText());
        assertTrue(result.path("semesters").get(0).path("change_from_previous").isNull());
        assertEquals(0.5, result.path("semesters").get(1).path("change_from_previous").path("weighted_gpa_delta").asDouble());
        assertEquals(1, result.path("gpa_comparison_count").asInt());
        assertFalse(result.path("semesters").get(1).has("focus_courses"));
        assertEquals(2, context.getSnapshot().statistics().path("semesters").get(2).path("focus_courses").size());
    }

    @Test
    void onePeriodAndMissingGpaDoNotInventTrends() {
        ObjectNode statistics = (ObjectNode) response.path("data").path("statistics");
        ArrayNode valid = (ArrayNode) statistics.path("valid_semesters");
        valid.removeAll().add("2025-2026学年秋季学期");
        useResponse();
        JsonNode result = service.trend(context, 4);
        assertEquals("INSUFFICIENT_DATA", result.path("status").asText());
        assertEquals("INSUFFICIENT_DATA", result.path("gpa_trend_status").asText());
        valid.insert(0, "2024-2025学年春季学期");
        ((ObjectNode) statistics.path("semesters").get(2).path("change_from_previous")).putNull("weighted_gpa_delta");
        useResponse();
        result = service.trend(context, 4);
        assertEquals("OK", result.path("status").asText());
        assertEquals("INSUFFICIENT_DATA", result.path("gpa_trend_status").asText());
        assertEquals("OK", result.path("numeric_score_trend_status").asText());
    }

    @Test
    void invalidSelectorsAndLimitsFailBeforeLoadingAnyData() {
        assertThrows(IllegalArgumentException.class, () -> service.recentPerformance(context, "other-student", 5));
        assertThrows(IllegalArgumentException.class, () -> service.recentPerformance(context, "latest", 0));
        assertThrows(IllegalArgumentException.class, () -> service.recentPerformance(context, "latest", 11));
        assertThrows(IllegalArgumentException.class, () -> service.trend(context, 1));
        assertThrows(IllegalArgumentException.class, () -> service.trend(context, 9));
        verifyNoInteractions(context);
    }

    @Test
    void oldPythonResponseGivesAnActionableUpgradeErrorInsteadOfFallbackReanalysis() {
        ((ObjectNode) response.path("data")).remove("statistics");
        useResponse();
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> service.trend(context, 4));
        assertTrue(error.getMessage().contains("8002"));
        verify(context, times(1)).getSnapshot();
    }
}
