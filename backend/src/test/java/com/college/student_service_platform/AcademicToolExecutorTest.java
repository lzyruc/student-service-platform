package com.college.student_service_platform;

import com.college.student_service_platform.agent.academic.*;
import com.college.student_service_platform.agent.AgentChatRequest;
import com.college.student_service_platform.agent.*;
import com.college.student_service_platform.agent.AgentProperties;
import com.college.student_service_platform.agent.DeepSeekClient;
import com.college.student_service_platform.agent.DeepSeekReply;
import com.college.student_service_platform.agent.SingleAgentService;
import com.college.student_service_platform.common.GlobalExceptionHandler;
import com.college.student_service_platform.common.JwtUtil;
import com.college.student_service_platform.config.AuthFilter;
import com.college.student_service_platform.controller.AgentChatController;
import com.college.student_service_platform.common.ApiException;
import com.college.student_service_platform.common.AuthContext;
import com.college.student_service_platform.dto.StudentTranscriptResponse;
import com.college.student_service_platform.dto.TrainingPlanItem;
import com.college.student_service_platform.entity.FileRecord;
import com.college.student_service_platform.service.*;
import com.college.student_service_platform.service.external.AcademicWarningClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.college.student_service_platform.agent.academic.AcademicToolDtos.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AcademicToolExecutorTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private AcademicWarningClient client;
    private WarningRecordService records;
    private StudentTranscriptService transcripts;
    private TrainingPlanService plans;
    private JdbcTemplate jdbc;
    private AcademicToolExecutor executor;
    private MockHttpServletRequest request;
    private ObjectNode response;
    private final Path pdf = Path.of("uploads", "private-student-grades.pdf");
    private FileRecord file;

    @BeforeEach
    void setUp() throws Exception {
        client = mock(AcademicWarningClient.class);
        records = mock(WarningRecordService.class);
        transcripts = mock(StudentTranscriptService.class);
        plans = mock(TrainingPlanService.class);
        jdbc = mock(JdbcTemplate.class);
        var service = new AcademicAnalysisService(client, records, plans, transcripts, jdbc, mapper);
        var projection = new AcademicToolMapper();
        var statistics = new AcademicStatisticsService();
        executor = new AcademicToolExecutor(service, mapper, new GetAcademicContextTool(projection),
                new GetAcademicAssessmentTool(projection), new GetRecentCoursePerformanceTool(statistics, projection),
                new GetAcademicTrendTool(statistics, projection));
        request = new MockHttpServletRequest();
        request.setAttribute(AuthContext.SUBJECT_ATTRIBUTE, "20260001");
        request.setAttribute(AuthContext.ROLE_ATTRIBUTE, "student");
        request.setParameter("studentNo", "20269999");
        file = new FileRecord();
        file.setId(22L);
        file.setOriginalName("20260001-secret-name.pdf");
        file.setCreatedAt(LocalDateTime.of(2026, 10, 2, 10, 0));
        when(transcripts.requireCurrent("20260001")).thenReturn(file);
        when(transcripts.getCurrent("20260001")).thenReturn(new StudentTranscriptResponse(
                22L, file.getOriginalName(), 100L, file.getCreatedAt(), true));
        when(transcripts.resolveForAnalysis("20260001", file)).thenReturn(pdf);
        when(jdbc.queryForList("SELECT major, grade FROM t_student WHERE student_no = ?", "20260001"))
                .thenReturn(List.of(Map.of("major", "计算机科学与技术", "grade", "2026")));
        TrainingPlanItem plan = new TrainingPlanItem();
        plan.setId(33L);
        plan.setMajor("计算机科学与技术");
        plan.setGrade("2026");
        plan.setVersion("v1.0");
        plan.setJsonContent("""
                {"courses":[{"category":"部类基础课","courseName":"程序设计","credits":4,"offeredAt":"1"}]}
                """);
        when(plans.getLatest("计算机科学与技术", "2026")).thenReturn(plan);
        response = (ObjectNode) mapper.readTree("""
                {"status":"success","data":{
                  "file_name":"20260001-secret-name.pdf","studentNo":"20260001","sql":"private raw data",
                  "courses":[{"name":"程序设计","score":"65","gpa":2,"credit":4}],
                  "report":{"total_earned_credits":40,"official_gpa":0,"official_gpa_available":false,
                    "warning_level":"一般预警","completed_semester":2,"issue_course_count":1,"core_course_count":1,
                    "failed_courses":[{"name":"微积分","score":"59"}],"core_courses":["程序设计"],
                    "missing_core_courses":["离散数学"],"pending_core_courses":["编译原理"],
                    "unscheduled_core_courses":["专业选修课"],"unknown_score_courses":["大学英语"],
                    "course_suggestions":["核实离散数学记录"],"analysis_notes":["成绩单年级与培养方案不同"]},
                  "statistics":{"schema_version":1,
                    "rules":{"low_score_min":60,"low_score_max_exclusive":70,
                      "semester_order":["秋季","春季","国际小学期","夏季/暑期"],
                      "weighted_gpa":"sum(credit * gpa) / sum(valid credit)",
                      "average_numeric_score":"arithmetic mean of valid 0..100 final scores",
                      "trend_direction":"sign of the difference between reported statistics"},
                    "analysis_notes":["数据覆盖不完整"],
                    "latest_semester":"2025-2026学年春季学期","previous_semester":"2025-2026学年秋季学期",
                    "valid_semesters":["2025-2026学年秋季学期","2025-2026学年春季学期"],
                    "semesters":[
                      {"semester":"2025-2026学年秋季学期","course_count":3,"weighted_gpa":2,
                       "average_numeric_score":60,"focus_courses":[],"change_from_previous":null},
                      {"semester":"2025-2026学年春季学期","course_count":3,"valid_score_course_count":2,
                       "passed_course_count":1,"failed_course_count":1,"unknown_score_course_count":1,
                       "attempted_credits":9,"valid_credit_course_count":3,"weighted_gpa":2.5,
                       "gpa_course_count":2,"gpa_credits":7,"gpa_course_coverage":0.6667,"gpa_credit_coverage":0.7778,
                       "average_numeric_score":62,"numeric_score_course_count":2,"numeric_score_coverage":0.6667,
                       "analysis_notes":["部分成绩未知"],
                       "failed_courses":[{"name":"不应重复暴露的完整列表"}],
                       "focus_courses":[
                         {"name":"微积分","score":"59","numeric_score":59,"credit":3,"page":1,
                          "passed":false,"current_failure_status":"UNRESOLVED","reason":"UNRESOLVED_FAILURE"},
                         {"name":"程序设计","score":"65","numeric_score":65,"credit":4,"page":1,
                          "passed":true,"current_failure_status":null,"reason":"LOW_NUMERIC_SCORE"},
                         {"name":"大学英语","score":"","numeric_score":null,"credit":2,"page":2,
                          "passed":null,"current_failure_status":null,"reason":"SCORE_UNAVAILABLE"}],
                       "change_from_previous":{"semester":"2025-2026学年秋季学期","weighted_gpa_delta":0.5,
                         "gpa_direction":"UP","average_numeric_score_delta":2,"numeric_score_direction":"UP",
                         "failed_course_count_delta":-1}}
                    ]}}}
                """);
        when(client.analyzeStoredTranscript(eq(pdf), eq(file.getOriginalName()), eq("20260001"), anyString()))
                .thenReturn(response);
    }

    @Test
    void fourToolsInOneRequestShareOneReadOnlyAnalysisAndTrustedIdentity() throws Exception {
        var context = executor.beginRequest(request);
        request.setAttribute(AuthContext.SUBJECT_ATTRIBUTE, "20269999");
        assertEquals("READY", output("get_academic_context", "{}", context, ContextOutput.class).status());
        verifyNoInteractions(client);
        var assessment = output("get_academic_assessment", "{}", context, AssessmentOutput.class);
        var recent = output("get_recent_course_performance", "{}", context, RecentOutput.class);
        var trend = output("get_academic_trend", "{}", context, TrendOutput.class);
        assertEquals(assessment.source(), recent.source());
        assertEquals(recent.source(), trend.source());
        verify(client, times(1)).analyzeStoredTranscript(eq(pdf), eq(file.getOriginalName()), eq("20260001"), anyString());
        verify(transcripts, never()).requireCurrent("20269999");
        verify(transcripts, never()).save(anyString(), any());
        verifyNoInteractions(records);
    }

    @Test
    void contextIsLazyCachedAndDoesNotParsePdf() {
        var context = executor.beginRequest(request);
        verifyNoInteractions(client, transcripts, jdbc, plans, records);
        var first = output("get_academic_context", "{}", context, ContextOutput.class);
        assertTrue(first.transcriptAvailable());
        assertEquals("v1.0", first.trainingPlanVersion());
        output("get_academic_context", "{}", context, ContextOutput.class);
        verify(transcripts, times(1)).getCurrent("20260001");
        verify(plans, times(1)).getLatest("计算机科学与技术", "2026");
        verifyNoInteractions(client, records);
    }

    @Test
    void contextListsMissingPrerequisitesWithoutFabricatingAssessment() {
        when(transcripts.getCurrent("20260001")).thenReturn(null);
        when(plans.getLatest("计算机科学与技术", "2026")).thenReturn(null);
        var data = output("get_academic_context", "{}", executor.beginRequest(request), ContextOutput.class);
        assertEquals("MISSING_DATA", data.status());
        assertFalse(data.transcriptExists());
        assertFalse(data.trainingPlanExists());
        assertEquals(2, data.nextActions().size());
        verifyNoInteractions(client, records);
    }

    @Test
    void contextDistinguishesBrokenFileAndMissingProfile() {
        when(transcripts.getCurrent("20260001")).thenReturn(new StudentTranscriptResponse(
                22L, file.getOriginalName(), 100L, file.getCreatedAt(), false));
        when(jdbc.queryForList("SELECT major, grade FROM t_student WHERE student_no = ?", "20260001"))
                .thenReturn(List.of());
        var data = output("get_academic_context", "{}", executor.beginRequest(request), ContextOutput.class);
        assertFalse(data.profileAvailable());
        assertTrue(data.transcriptExists());
        assertFalse(data.transcriptAvailable());
        assertEquals(List.of("完善本人专业和年级信息", "重新上传可用的成绩单 PDF"), data.nextActions());
        verifyNoInteractions(plans, client);
    }

    @Test
    void assessmentRetainsRiskAndSeparateCoreStatusesAndDoesNotTreatMissingGpaAsZero() {
        var data = output("get_academic_assessment", "{}", executor.beginRequest(request), AssessmentOutput.class);
        assertEquals("一般预警", data.warningLevel());
        assertEquals(40, data.totalEarnedCredits().intValue());
        assertFalse(data.officialGpaAvailable());
        assertNull(data.officialGpa());
        assertEquals(List.of("程序设计"), data.completedCoreCourses());
        assertEquals(List.of("离散数学"), data.missingCoreCourses());
        assertEquals(List.of("编译原理"), data.pendingCoreCourses());
        assertEquals(List.of("专业选修课"), data.unscheduledCoreCourses());
        assertTrue(data.analysisNotes().stream().anyMatch(note -> note.contains("需核实")));
        assertThrows(UnsupportedOperationException.class, () -> data.missingCoreCourses().clear());
        ((ObjectNode) response.path("data").path("report")).put("official_gpa_available", true);
        var zero = output("get_academic_assessment", "{}", executor.beginRequest(request), AssessmentOutput.class);
        assertTrue(zero.officialGpaAvailable());
        assertEquals(0, zero.officialGpa().intValue());
    }

    @Test
    void missingReportEvidenceDoesNotBecomeAnEmptyHealthyReport() {
        ((ObjectNode) response.path("data").path("report")).remove("missing_core_courses");
        assertEquals("SERVICE_UNAVAILABLE",
                executor.execute("get_academic_assessment", "{}", executor.beginRequest(request)).status());
        verifyNoInteractions(records);
    }

    @Test
    void recentRespectsLimitsEvidenceNullsAndCanonicalSemesterFromStatistics() {
        var context = executor.beginRequest(request);
        var recent = output("get_recent_course_performance", "{\"limit\":1}", context, RecentOutput.class);
        assertEquals("latest", recent.requestedSemester());
        assertEquals("2025-2026学年春季学期", recent.semester());
        assertEquals(3, recent.focusCourseCount());
        assertEquals(1, recent.focusCourses().size());
        assertTrue(recent.truncated());
        assertEquals("UNRESOLVED_FAILURE", recent.focusCourses().get(0).reason());
        assertFalse(recent.focusCourses().get(0).passed());
        assertEquals(0.6667, recent.summary().gpaCourseCoverage().doubleValue());
        var full = output("get_recent_course_performance", "{}", context, RecentOutput.class);
        assertEquals(5, full.limit());
        assertFalse(full.truncated());
        assertNull(full.focusCourses().get(2).numericScore());
        assertNull(full.focusCourses().get(2).passed());
        var previous = output("get_recent_course_performance", "{\"semester\":\"previous\"}", context, RecentOutput.class);
        assertEquals("2025-2026学年秋季学期", previous.semester());
    }

    @Test
    void trendKeepsPrecomputedDeltasDirectionsAndSampleCoverage() {
        var data = output("get_academic_trend", "{\"lastSemesters\":2}", executor.beginRequest(request), TrendOutput.class);
        assertEquals("OK", data.status());
        assertEquals(2, data.requestedSemesterCount());
        assertEquals(2, data.semesterCount());
        assertEquals("2025-2026学年秋季学期", data.semesters().get(0).semester());
        assertNull(data.semesters().get(0).changeFromPrevious());
        assertEquals(0.5, data.semesters().get(1).changeFromPrevious().weightedGpaDelta().doubleValue());
        assertEquals("UP", data.semesters().get(1).changeFromPrevious().gpaDirection());
        assertEquals(-1, data.semesters().get(1).changeFromPrevious().failedCourseCountDelta());
        assertEquals(1, data.gpaComparisonCount());
        assertEquals("OK", data.numericScoreTrendStatus());
    }

    @Test
    void insufficientRecentAndTrendDataAreSuccessfulQueriesWithExplicitDataStatus() {
        ObjectNode stats = (ObjectNode) response.path("data").path("statistics");
        stats.putNull("previous_semester");
        stats.putArray("valid_semesters").add("2025-2026学年春季学期");
        var context = executor.beginRequest(request);
        var recent = output("get_recent_course_performance", "{\"semester\":\"previous\"}", context, RecentOutput.class);
        assertEquals("INSUFFICIENT_DATA", recent.status());
        assertNull(recent.semester());
        assertNull(recent.summary());
        assertTrue(recent.focusCourses().isEmpty());
        var trend = output("get_academic_trend", "{}", context, TrendOutput.class);
        assertEquals(4, trend.requestedSemesterCount());
        assertEquals("INSUFFICIENT_DATA", trend.status());
        assertEquals("INSUFFICIENT_DATA", trend.gpaTrendStatus());
    }

    @Test
    void strictParametersAndUnknownToolsFailBeforeAnyBusinessReads() {
        var context = executor.beginRequest(request);
        for (String args : List.of("{\"studentNo\":\"other\"}", "{\"studentId\":1}", "{\"fileId\":1}",
                "{\"path\":\"D:/x.pdf\"}", "{\"sql\":\"select\"}", "{\"url\":\"http://x\"}", "[]", "null")) {
            assertEquals("INVALID_ARGUMENT", executor.execute("get_academic_context", args, context).status(), args);
            assertEquals("INVALID_ARGUMENT", executor.execute("get_academic_assessment", args, context).status(), args);
        }
        for (String args : List.of("{\"limit\":0}", "{\"limit\":11}", "{\"limit\":1.0}", "{\"limit\":\"5\"}",
                "{\"limit\":true}", "{\"limit\":null}", "{\"limit\":999999999999999999999}",
                "{\"semester\":\"2025-2026\"}", "{\"semester\":null}", "{\"semester\":1}",
                "{\"studentNo\":\"other\"}", "{\"limit\":1,\"limit\":2}", "{} {}", "{bad", ""))
            assertEquals("INVALID_ARGUMENT", executor.execute("get_recent_course_performance", args, context).status(), args);
        for (String args : List.of("{\"lastSemesters\":1}", "{\"lastSemesters\":9}", "{\"lastSemesters\":2.1}",
                "{\"lastSemesters\":true}", "{\"lastSemesters\":\"4\"}", "{\"lastSemesters\":null}", "{\"extra\":1}"))
            assertEquals("INVALID_ARGUMENT", executor.execute("get_academic_trend", args, context).status(), args);
        assertEquals("UNKNOWN_TOOL", executor.execute("save_warning", "{}", context).status());
        assertEquals("UNKNOWN_TOOL", executor.execute("/api/admin/students", "{}", context).status());
        verifyNoInteractions(client, transcripts, jdbc, plans, records);
    }

    @Test
    void runtimeJsonNodeInputsAreAlsoValidatedAndSchemasMatchDefaultsAndBoundaries() throws Exception {
        var context = executor.beginRequest(request);
        assertEquals("INVALID_ARGUMENT", executor.execute("get_academic_trend", mapper.readTree("{\"studentNo\":\"other\"}"), context).status());
        var definitions = executor.definitions();
        assertEquals(List.of("get_academic_context", "get_academic_assessment", "get_recent_course_performance", "get_academic_trend"),
                definitions.stream().map(AcademicToolDefinition::name).toList());
        for (var definition : definitions) assertFalse(definition.parameters().path("additionalProperties").asBoolean());
        JsonNode recent = definitions.get(2).parameters().path("properties");
        assertEquals(1, recent.path("limit").path("minimum").asInt());
        assertEquals(10, recent.path("limit").path("maximum").asInt());
        assertEquals(5, recent.path("limit").path("default").asInt());
        assertEquals("latest", recent.path("semester").path("default").asText());
        assertEquals(4, definitions.get(3).parameters().path("properties").path("lastSemesters").path("default").asInt());
        ((ObjectNode) definitions.get(2).parameters()).put("additionalProperties", true);
        assertFalse(executor.definitions().get(2).parameters().path("additionalProperties").asBoolean());
        assertThrows(IllegalArgumentException.class, () -> new RecentInput("today", 5));
        assertThrows(IllegalArgumentException.class, () -> new TrendInput(9));
        verifyNoInteractions(client, transcripts, records);
    }

    @Test
    void modelOutputsContainOnlyDtosAndExcludeIdentityPathsAndRawLists() throws Exception {
        var context = executor.beginRequest(request);
        for (var definition : executor.definitions()) {
            String json = mapper.writeValueAsString(executor.execute(definition.name(), "{}", context));
            assertFalse(json.contains("20260001"));
            assertFalse(json.contains("studentNo"));
            assertFalse(json.contains("studentId"));
            assertFalse(json.contains("fileId"));
            assertFalse(json.contains("trainingPlanId"));
            assertFalse(json.contains(".pdf"));
            assertFalse(json.contains("sql"));
            assertFalse(json.contains("private raw data"));
            assertFalse(json.contains("不应重复暴露的完整列表"));
            assertFalse(json.contains("\"response\""));
            assertFalse(json.contains("\"courses\""));
        }
    }

    @Test
    void failedAnalysisIsNotRetriedAndExceptionDetailsAreNotExposed() {
        when(client.analyzeStoredTranscript(eq(pdf), anyString(), eq("20260001"), anyString()))
                .thenThrow(new IllegalStateException("password=secret D:/private.pdf jdbc:mysql://private"));
        var context = executor.beginRequest(request);
        for (String name : List.of("get_academic_assessment", "get_recent_course_performance", "get_academic_trend")) {
            var result = executor.execute(name, "{}", context);
            assertEquals("SERVICE_UNAVAILABLE", result.status());
            assertNull(result.data());
            assertFalse(result.message().contains("secret"));
            assertFalse(result.message().contains("private"));
        }
        verify(client, times(1)).analyzeStoredTranscript(eq(pdf), anyString(), eq("20260001"), anyString());
        verifyNoInteractions(records);
    }

    @Test
    void missingFilesAndNonStudentIdentityDoNotReachPython() {
        when(transcripts.requireCurrent("20260001")).thenThrow(new ApiException(HttpStatus.NOT_FOUND, "missing file"));
        assertEquals("MISSING_DATA", executor.execute("get_academic_assessment", "{}", executor.beginRequest(request)).status());
        assertEquals(HttpStatus.UNAUTHORIZED,
                assertThrows(ApiException.class, () -> executor.beginRequest(new MockHttpServletRequest())).getStatus());
        request.setAttribute(AuthContext.ROLE_ATTRIBUTE, "admin");
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class, () -> executor.beginRequest(request)).getStatus());
        verifyNoInteractions(client, records);
    }

    @Test
    void oldStatisticsFailExplicitlyWithoutCallingPythonAgainOrWritingRecords() {
        ((ObjectNode) response.path("data")).remove("statistics");
        var context = executor.beginRequest(request);
        assertEquals("SERVICE_UNAVAILABLE", executor.execute("get_recent_course_performance", "{}", context).status());
        assertEquals("SERVICE_UNAVAILABLE", executor.execute("get_academic_trend", "{}", context).status());
        verify(client, times(1)).analyzeStoredTranscript(eq(pdf), anyString(), eq("20260001"), anyString());
        verifyNoInteractions(records);
    }

    @Test
    void concurrentDifferentToolsStillShareOneAnalysis() throws Exception {
        var context = executor.beginRequest(request);
        var workers = Executors.newFixedThreadPool(3);
        try {
            List<Callable<AcademicToolResult<?>>> tasks = List.of(
                    () -> executor.execute("get_academic_assessment", "{}", context),
                    () -> executor.execute("get_recent_course_performance", "{}", context),
                    () -> executor.execute("get_academic_trend", "{}", context));
            for (var result : workers.invokeAll(tasks, 5, TimeUnit.SECONDS)) assertEquals("OK", result.get().status());
            verify(client, times(1)).analyzeStoredTranscript(eq(pdf), anyString(), eq("20260001"), anyString());
            verifyNoInteractions(records);
        } finally {
            workers.shutdownNow();
        }
    }

    @Test
    void jwtChatEndpointAndRealFourToolsParseOncePerRequestAndRefreshOnFollowup() throws Exception {
        DeepSeekClient model = mock(DeepSeekClient.class);
        AtomicInteger steps = new AtomicInteger();
        List<com.fasterxml.jackson.databind.node.ArrayNode> sent = new ArrayList<>();
        when(model.complete(any(), anyList(), anyString())).thenAnswer(call -> {
            sent.add(((com.fasterxml.jackson.databind.node.ArrayNode) call.getArgument(0)).deepCopy());
            int step = steps.incrementAndGet();
            ObjectNode message = mapper.createObjectNode().put("role", "assistant");
            if (step == 3 || step == 5) return new DeepSeekReply(message.put("content", "依据本请求工具结果生成样本回答"), "stop");
            var calls = message.putArray("tool_calls");
            List<String> names = step == 1 ? List.of("get_academic_context", "get_academic_assessment", "get_recent_course_performance")
                    : step == 2 ? List.of("get_academic_trend") : List.of("get_academic_assessment");
            for (String name : names) {
                calls.addObject().put("id", "call_" + step + "_" + name).put("type", "function")
                        .putObject("function").put("name", name).put("arguments", "{}");
            }
            return new DeepSeekReply(message, "tool_calls");
        });
        var properties = new AgentProperties();
        var agent = new SingleAgentService(model, executor, properties, mapper, new com.college.student_service_platform.agent.academic.AcademicSkill(properties));
        var jwt = new JwtUtil("0123456789abcdef0123456789abcdef", "test", Duration.ofHours(1));
        var conversationFixture = ConversationTestSupport.create();
        long conversationId = conversationFixture.service().create(request, null).id();
        var mvc = MockMvcBuilders.standaloneSetup(new AgentChatController(new ConversationChatService(conversationFixture.service(), agent)))
                .setControllerAdvice(new GlobalExceptionHandler()).addFilters(new AuthFilter(jwt, mapper)).build();
        String token = "Bearer " + jwt.createToken("20260001", "student");
        mvc.perform(post("/api/student/agent/chat").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"conversationId\":" + conversationId + ",\"message\":\"分析最近学习情况\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.toolRounds").value(2)).andExpect(jsonPath("$.data.toolCalls.length()").value(4));
        verify(client, times(1)).analyzeStoredTranscript(eq(pdf), anyString(), eq("20260001"), anyString());
        // Owned persisted conversation, new HTTP request: do not reuse the previous request's analysis.
        mvc.perform(post("/api/student/agent/chat").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"conversationId\":" + conversationId + ",\"message\":\"再看一下\"}"))
                .andExpect(status().isOk());
        verify(client, times(2)).analyzeStoredTranscript(eq(pdf), anyString(), eq("20260001"), anyString());
        for (var batch : sent) for (var message : batch) if ("tool".equals(message.path("role").asText())) {
            String content = message.path("content").asText();
            assertFalse(content.contains("20260001"));
            assertFalse(content.contains(file.getOriginalName()));
            assertFalse(content.contains("transcriptFileId"));
            assertFalse(content.contains("private raw data"));
        }
        mvc.perform(post("/api/student/agent/chat").header("Authorization", "Bearer " + jwt.createToken("admin", "admin"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"conversationId\":" + conversationId + ",\"message\":\"分析\"}"))
                .andExpect(status().isForbidden());
        assertEquals(5, steps.get());
        verifyNoInteractions(records);
        verify(transcripts, never()).save(anyString(), any());
    }


    @Test
    void ownedDatabaseHistoryCannotOverrideCurrentPythonGpaAndNextRequestGetsFreshSnapshot() throws Exception {
        var fixture=ConversationTestSupport.create();long id=fixture.service().create(request,null).id();
        fixture.service().saveExchange(fixture.service().prepareChat(request,id,"我的 GPA 是 4.0"),
                new AgentChatResponse("上轮自述 GPA=4.0，不是当前核验结果","COMPLETED",0,List.of()));
        ((ObjectNode)response.path("data").path("report")).put("official_gpa_available",true).put("official_gpa",2.8);
        var model=mock(DeepSeekClient.class);var sent=new ArrayList<com.fasterxml.jackson.databind.node.ArrayNode>();
        when(model.complete(any(),anyList(),anyString())).thenAnswer(call -> {
            var messages=((com.fasterxml.jackson.databind.node.ArrayNode)call.getArgument(0)).deepCopy();sent.add(messages);
            boolean hasTool=java.util.stream.StreamSupport.stream(messages.spliterator(),false).anyMatch(m -> "tool".equals(m.path("role").asText()));
            var message=mapper.createObjectNode().put("role","assistant");
            if(hasTool) return new DeepSeekReply(message.put("content","你的 GPA 是 4.0，没有问题"),"stop");
            message.putArray("tool_calls").addObject().put("id","same-request-local-id").put("type","function")
                    .putObject("function").put("name","get_academic_assessment").put("arguments","{}");
            return new DeepSeekReply(message,"tool_calls");
        });
        var properties=new AgentProperties();var agent=new SingleAgentService(model,executor,properties,mapper,new AcademicSkill(properties));
        var chats=new ConversationChatService(fixture.service(),agent);
        var first=chats.prepare(new ConversationChatRequest(id,"那我情况怎么样？"),request);
        assertTrue(first.requestContext().business().results().isEmpty());verifyNoInteractions(client);
        var firstResult=chats.chat(first,event -> { });
        assertTrue(firstResult.answer().contains("2.8"));assertFalse(firstResult.answer().contains("4.0"));
        assertTrue(sent.get(0).get(2).path("content").asText().contains("4.0"));
        assertEquals(1,first.requestContext().toolCallCount());
        ((ObjectNode)response.path("data").path("report")).put("official_gpa",3.1);
        var second=chats.prepare(new ConversationChatRequest(id,"现在呢？"),request);
        assertNotSame(first.requestContext(),second.requestContext());
        assertNotSame(first.requestContext().business().analysis(),second.requestContext().business().analysis());
        assertEquals(0,second.requestContext().toolCallCount());
        var secondResult=chats.chat(second,event -> { });assertTrue(secondResult.answer().contains("3.1"));
        assertEquals(2.8,first.requestContext().business().analysis().getSnapshot().report().path("official_gpa").asDouble());
        assertEquals(3.1,second.requestContext().business().analysis().getSnapshot().report().path("official_gpa").asDouble());
        verify(client,times(2)).analyzeStoredTranscript(eq(pdf),anyString(),eq("20260001"),anyString());
        verifyNoInteractions(records);
    }

    @Test
    void severalToolsInFailedRequestDoNotRetryPythonButNextRequestCanRecover() throws Exception {
        when(client.analyzeStoredTranscript(eq(pdf),anyString(),eq("20260001"),anyString()))
                .thenThrow(new IllegalStateException("temporary failure")).thenReturn(response);
        var fixture=ConversationTestSupport.create();long id=fixture.service().create(request,null).id();
        var model=mock(DeepSeekClient.class);
        when(model.complete(any(),anyList(),anyString())).thenAnswer(call -> {
            var messages=(com.fasterxml.jackson.databind.node.ArrayNode)call.getArgument(0);
            boolean hasTool=java.util.stream.StreamSupport.stream(messages.spliterator(),false).anyMatch(m -> "tool".equals(m.path("role").asText()));
            var message=mapper.createObjectNode().put("role","assistant");
            if(hasTool) return new DeepSeekReply(message.put("content","当前 GPA 是 4.0"),"stop");
            var calls=message.putArray("tool_calls");
            for(String name:List.of("get_academic_assessment","get_recent_course_performance","get_academic_trend"))
                calls.addObject().put("id",name).put("type","function").putObject("function").put("name",name).put("arguments","{}");
            return new DeepSeekReply(message,"tool_calls");
        });
        var properties=new AgentProperties();var agent=new SingleAgentService(model,executor,properties,mapper,new AcademicSkill(properties));
        var chats=new ConversationChatService(fixture.service(),agent);
        var failed=chats.prepare(new ConversationChatRequest(id,"分析"),request);
        var failedResult=chats.chat(failed,event -> { });assertEquals("DATA_UNAVAILABLE",failedResult.status());
        assertFalse(failedResult.answer().contains("4.0"));assertEquals(3,failed.requestContext().toolCallCount());
        verify(client,times(1)).analyzeStoredTranscript(eq(pdf),anyString(),eq("20260001"),anyString());
        var recovered=chats.prepare(new ConversationChatRequest(id,"重试"),request);
        assertEquals("COMPLETED",chats.chat(recovered,event -> { }).status());
        verify(client,times(2)).analyzeStoredTranscript(eq(pdf),anyString(),eq("20260001"),anyString());
        verifyNoInteractions(records);
    }


    @Test
    void conversationRequestContextsCannotBeMixedAndFailedPersistenceMarksRequestFailed() throws Exception {
        var fixture=ConversationTestSupport.create();long leftId=fixture.service().create(request,null).id();long rightId=fixture.service().create(request,null).id();
        var model=mock(DeepSeekClient.class);var properties=new AgentProperties();
        var agent=new SingleAgentService(model,executor,properties,mapper,new AcademicSkill(properties));
        var chats=new ConversationChatService(fixture.service(),agent);
        var left=chats.prepare(new ConversationChatRequest(leftId,"左侧会话"),request);
        var right=chats.prepare(new ConversationChatRequest(rightId,"右侧会话"),request);
        assertEquals(HttpStatus.FORBIDDEN,assertThrows(ApiException.class,() -> new ConversationChatService.Prepared(left.input(),right.requestContext())).getStatus());
        verifyNoInteractions(model,client);
        // Another completed exchange makes the prepared history stale before the model finishes.
        fixture.service().saveExchange(fixture.service().prepareChat(request,leftId,"先完成的问题"),new AgentChatResponse("先完成的回答","COMPLETED",0,List.of()));
        when(model.complete(any(),anyList(),anyString())).thenReturn(new DeepSeekReply(mapper.createObjectNode().put("role","assistant")
                .put("content","{\"responseType\":\"NON_ANALYSIS\",\"reason\":\"NEEDS_CLARIFICATION\"}"),"stop"));
        assertEquals(HttpStatus.CONFLICT,assertThrows(ApiException.class,() -> chats.chat(left,event -> { })).getStatus());
        assertEquals(AgentRequestContext.State.FAILED,left.requestContext().state());
        assertEquals(AgentRequestContext.State.PREPARED,right.requestContext().state());
        assertEquals(2,fixture.service().detail(request,leftId,null,100).messages().size());
    }

    private <T> T output(String name, String args, AcademicAnalysisReadContext context, Class<T> type) {
        var result = executor.execute(name, args, context);
        assertEquals("OK", result.status(), result.message());
        return type.cast(result.data());
    }
}
