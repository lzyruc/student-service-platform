package com.college.student_service_platform;

import com.college.student_service_platform.common.ApiException;
import com.college.student_service_platform.common.AuthContext;
import com.college.student_service_platform.dto.AcademicAnalysisSnapshot;
import com.college.student_service_platform.dto.TrainingPlanItem;
import com.college.student_service_platform.entity.FileRecord;
import com.college.student_service_platform.service.AcademicAnalysisReadContext;
import com.college.student_service_platform.service.AcademicAnalysisService;
import com.college.student_service_platform.service.AcademicStatisticsService;
import com.college.student_service_platform.service.StudentTranscriptService;
import com.college.student_service_platform.service.TrainingPlanService;
import com.college.student_service_platform.service.WarningRecordService;
import com.college.student_service_platform.service.external.AcademicWarningClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AcademicAnalysisServiceTest {
    private AcademicWarningClient client;
    private WarningRecordService records;
    private TrainingPlanService plans;
    private StudentTranscriptService transcripts;
    private JdbcTemplate jdbc;
    private AcademicAnalysisService service;
    private MockHttpServletRequest request;
    private TrainingPlanItem plan;
    private FileRecord file;
    private ObjectNode response;
    private final Path pdf = Path.of("uploads", "saved-transcript.pdf");

    @BeforeEach
    void setUp() {
        client = mock(AcademicWarningClient.class);
        records = mock(WarningRecordService.class);
        plans = mock(TrainingPlanService.class);
        transcripts = mock(StudentTranscriptService.class);
        jdbc = mock(JdbcTemplate.class);
        ObjectMapper mapper = new ObjectMapper();
        service = new AcademicAnalysisService(client, records, plans, transcripts, jdbc, mapper);
        request = new MockHttpServletRequest();
        request.setAttribute(AuthContext.SUBJECT_ATTRIBUTE, "20260001");
        request.setAttribute(AuthContext.ROLE_ATTRIBUTE, "student");
        request.setParameter("studentNo", "other-student");
        file = new FileRecord();
        file.setId(22L);
        file.setOriginalName("grades.pdf");
        file.setCreatedAt(LocalDateTime.of(2026, 10, 2, 10, 0));
        when(transcripts.requireCurrent("20260001")).thenReturn(file);
        when(transcripts.resolveForAnalysis("20260001", file)).thenReturn(pdf);
        when(jdbc.queryForList("SELECT major, grade FROM t_student WHERE student_no = ?", "20260001"))
                .thenReturn(List.of(Map.of("major", "计算机科学与技术", "grade", "2026")));
        plan = new TrainingPlanItem();
        plan.setId(33L);
        plan.setMajor("计算机科学与技术");
        plan.setGrade("2026");
        plan.setVersion("v1.0");
        plan.setJsonContent("""
                {"courses":[{"category":"部类基础课","courseName":"程序设计","credits":4,"offeredAt":"1"}]}
                """);
        when(plans.getLatest("计算机科学与技术", "2026")).thenReturn(plan);
        response = mapper.valueToTree(Map.of("status", "success", "data", Map.of(
                "courses", List.of(Map.of("name", "程序设计", "credit", 4, "score", "78", "semester", "2025-2026学年春季")),
                "report", Map.of("warning_level", "正常", "official_gpa", 3.65,
                        "completed_semester", 4, "analysis_notes", List.of("年级差异需要核实")))));
        when(client.analyzeStoredTranscript(eq(pdf), eq("grades.pdf"), eq("20260001"), anyString()))
                .thenReturn(response);
    }

    @Test
    void readContextIsLazyAndSharesOneFullReadOnlySnapshotAcrossTools() throws Exception {
        AcademicAnalysisReadContext context = service.createReadContext(request);
        verifyNoInteractions(client, transcripts, jdbc, plans, records);
        AcademicAnalysisSnapshot snapshot = context.getSnapshot();
        assertSame(snapshot, context.getSnapshot());
        assertEquals("20260001", snapshot.studentNo());
        assertEquals(22L, snapshot.transcriptFileId());
        assertEquals(33L, snapshot.trainingPlanId());
        assertEquals("v1.0", snapshot.trainingPlanVersion());
        assertEquals(3.65, snapshot.report().path("official_gpa").asDouble());
        assertEquals("年级差异需要核实", snapshot.report().path("analysis_notes").get(0).asText());
        assertEquals("2025-2026学年春季", snapshot.courses().get(0).path("semester").asText());
        verify(client, times(1)).analyzeStoredTranscript(eq(pdf), eq("grades.pdf"), eq("20260001"), anyString());
        verify(transcripts, never()).save(anyString(), any());
        verifyNoInteractions(records);
        verify(jdbc).queryForList("SELECT major, grade FROM t_student WHERE student_no = ?", "20260001");
        verifyNoMoreInteractions(jdbc);
    }

    @Test
    void concurrentToolsAlsoShareExactlyOneAnalysis() throws Exception {
        AcademicAnalysisReadContext context = service.createReadContext(request);
        var executor = Executors.newFixedThreadPool(4);
        try {
            Callable<AcademicAnalysisSnapshot> tool = context::getSnapshot;
            var results = executor.invokeAll(List.of(tool, tool, tool, tool), 5, TimeUnit.SECONDS);
            AcademicAnalysisSnapshot snapshot = results.get(0).get();
            for (var result : results) assertSame(snapshot, result.get());
            verify(client, times(1)).analyzeStoredTranscript(eq(pdf), eq("grades.pdf"), eq("20260001"), anyString());
            verifyNoInteractions(records);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void snapshotCannotBeChangedByTheSourceOrAnotherTool() {
        AcademicAnalysisSnapshot snapshot = service.createReadContext(request).getSnapshot();
        ((ObjectNode) response.path("data").path("report")).put("warning_level", "严重预警");
        ((ObjectNode) snapshot.report()).put("warning_level", "一般预警");
        ((ObjectNode) snapshot.response().path("data").path("report")).put("official_gpa", 0);
        ((ObjectNode) snapshot.courses().get(0)).put("score", "0");
        assertEquals("正常", snapshot.report().path("warning_level").asText());
        assertEquals(3.65, snapshot.report().path("official_gpa").asDouble());
        assertEquals("78", snapshot.courses().get(0).path("score").asText());
    }

    @Test
    void newChatReloadsCurrentFileAndPlanWithoutCreatingRecords() {
        AcademicAnalysisReadContext firstChat = service.createReadContext(request);
        AcademicAnalysisSnapshot first = firstChat.getSnapshot();
        file.setId(44L);
        plan.setId(55L);
        plan.setVersion("v2.0");
        AcademicAnalysisSnapshot second = service.createReadContext(request).getSnapshot();
        assertEquals(22L, first.transcriptFileId());
        assertEquals(33L, first.trainingPlanId());
        assertEquals(44L, second.transcriptFileId());
        assertEquals(55L, second.trainingPlanId());
        assertEquals("v2.0", second.trainingPlanVersion());
        assertSame(first, firstChat.getSnapshot());
        verify(client, times(2)).analyzeStoredTranscript(eq(pdf), eq("grades.pdf"), eq("20260001"), anyString());
        verifyNoInteractions(records);
    }

    @Test
    void failedAnalysisIsNotRetriedByOtherToolsInTheSameChat() {
        RuntimeException failure = new IllegalStateException("Python unavailable");
        when(client.analyzeStoredTranscript(eq(pdf), eq("grades.pdf"), eq("20260001"), anyString()))
                .thenThrow(failure);
        AcademicAnalysisReadContext context = service.createReadContext(request);
        assertSame(failure, assertThrows(IllegalStateException.class, context::getSnapshot));
        assertSame(failure, assertThrows(IllegalStateException.class, context::getSnapshot));
        verify(client, times(1)).analyzeStoredTranscript(eq(pdf), eq("grades.pdf"), eq("20260001"), anyString());
        verifyNoInteractions(records);
    }

    @Test
    void missingSavedFileFailsBeforePythonOrAnyBusinessWrite() {
        when(transcripts.requireCurrent("20260001")).thenThrow(new ApiException(HttpStatus.NOT_FOUND, "请先上传成绩单"));
        AcademicAnalysisReadContext context = service.createReadContext(request);
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ApiException.class, context::getSnapshot).getStatus());
        verifyNoInteractions(client, plans, jdbc, records);
    }

    @Test
    void missingMatchingPlanFailsBeforePythonOrAnyBusinessWrite() throws Exception {
        when(plans.getLatest("计算机科学与技术", "2026")).thenReturn(null);
        assertEquals(HttpStatus.NOT_FOUND,
                assertThrows(ApiException.class, service.createReadContext(request)::getSnapshot).getStatus());
        verifyNoInteractions(client, records);
        verify(transcripts, never()).save(anyString(), any());
    }

    @Test
    void incompletePythonResponseIsRejectedWithoutWritingRecords() {
        when(client.analyzeStoredTranscript(eq(pdf), eq("grades.pdf"), eq("20260001"), anyString()))
                .thenReturn(Map.of("data", Map.of("report", Map.of("warning_level", "正常"))));
        AcademicAnalysisReadContext context = service.createReadContext(request);
        assertThrows(IllegalStateException.class, context::getSnapshot);
        assertThrows(IllegalStateException.class, context::getSnapshot);
        verify(client, times(1)).analyzeStoredTranscript(eq(pdf), eq("grades.pdf"), eq("20260001"), anyString());
        verifyNoInteractions(records);
    }

    @Test
    void unauthenticatedAndNonStudentRequestsCannotCreateReadContext() {
        assertEquals(HttpStatus.UNAUTHORIZED,
                assertThrows(ApiException.class, () -> service.createReadContext(new MockHttpServletRequest())).getStatus());
        request.setAttribute(AuthContext.ROLE_ATTRIBUTE, "admin");
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class, () -> service.createReadContext(request)).getStatus());
        verifyNoInteractions(client, transcripts, jdbc, plans, records);
    }

    @Test
    void trustedIdentityIsCapturedWhenTheContextIsCreated() {
        AcademicAnalysisReadContext context = service.createReadContext(request);
        request.setAttribute(AuthContext.SUBJECT_ATTRIBUTE, "other-student");
        assertEquals("20260001", context.getSnapshot().studentNo());
        verify(transcripts).requireCurrent("20260001");
        verify(transcripts, never()).requireCurrent("other-student");
    }

    @Test
    void recentAndTrendStatisticsShareTheSameReadOnlyAnalysisWithoutExtraPythonCalls() throws Exception {
        ObjectNode statistics = new ObjectMapper().createObjectNode();
        statistics.put("schema_version", 1);
        statistics.put("latest_semester", "2025-2026学年春季学期");
        statistics.putNull("previous_semester");
        statistics.putArray("valid_semesters").add("2025-2026学年春季学期");
        ObjectNode semester = statistics.putArray("semesters").addObject();
        semester.put("semester", "2025-2026学年春季学期");
        semester.putArray("focus_courses");
        ((ObjectNode) response.path("data")).set("statistics", statistics);
        AcademicAnalysisReadContext context = service.createReadContext(request);
        AcademicStatisticsService summaries = new AcademicStatisticsService();
        assertEquals("OK", summaries.recentPerformance(context, "latest", 5).path("status").asText());
        assertEquals("INSUFFICIENT_DATA", summaries.trend(context, 4).path("status").asText());
        verify(client, times(1)).analyzeStoredTranscript(eq(pdf), eq("grades.pdf"), eq("20260001"), anyString());
        verifyNoInteractions(records);
        verify(transcripts, never()).save(anyString(), any());
    }
    @Test
    void concurrentFailedSnapshotLoadsOnlyOnceAndFreshRequestCanRecover() throws Exception {
        var failure=new IllegalStateException("temporary Python failure");
        when(client.analyzeStoredTranscript(eq(pdf),eq("grades.pdf"),eq("20260001"),anyString())).thenThrow(failure).thenReturn(response);
        var failed=service.createReadContext(request);
        var workers=Executors.newFixedThreadPool(4);
        try {
            Callable<RuntimeException> task=() -> assertThrows(IllegalStateException.class,failed::getSnapshot);
            for(var result:workers.invokeAll(List.of(task,task,task,task),5,TimeUnit.SECONDS)) assertSame(failure,result.get());
            verify(client,times(1)).analyzeStoredTranscript(eq(pdf),eq("grades.pdf"),eq("20260001"),anyString());
            assertSame(failure,assertThrows(IllegalStateException.class,failed::getSnapshot));
            var next=service.createReadContext(request);assertNotSame(failed,next);assertNotNull(next.getSnapshot());
            verify(client,times(2)).analyzeStoredTranscript(eq(pdf),eq("grades.pdf"),eq("20260001"),anyString());
            verifyNoInteractions(records);
        } finally { workers.shutdownNow(); }
    }

}
