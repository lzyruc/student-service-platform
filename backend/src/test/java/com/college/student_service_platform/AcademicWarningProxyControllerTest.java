package com.college.student_service_platform;

import com.college.student_service_platform.common.AuthContext;
import com.college.student_service_platform.common.ApiException;
import com.college.student_service_platform.controller.AcademicWarningProxyController;
import com.college.student_service_platform.entity.FileRecord;
import com.college.student_service_platform.dto.TrainingPlanItem;
import com.college.student_service_platform.service.StudentTranscriptService;
import com.college.student_service_platform.service.AcademicAnalysisService;
import com.college.student_service_platform.service.TrainingPlanService;
import com.college.student_service_platform.service.WarningRecordService;
import com.college.student_service_platform.service.external.AcademicWarningClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Map;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.springframework.http.HttpStatus;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AcademicWarningProxyControllerTest {
    private AcademicWarningClient client;
    private TrainingPlanService plans;
    private WarningRecordService records;
    private StudentTranscriptService transcripts;
    private JdbcTemplate jdbc;
    private ObjectMapper mapper;
    private AcademicWarningProxyController controller;
    private MockHttpServletRequest request;
    private TrainingPlanItem plan;
    private Map<String, Object> pythonResponse;

    @BeforeEach
    void setUp() {
        client = mock(AcademicWarningClient.class);
        plans = mock(TrainingPlanService.class);
        records = mock(WarningRecordService.class);
        transcripts = mock(StudentTranscriptService.class);
        jdbc = mock(JdbcTemplate.class);
        mapper = new ObjectMapper();
        controller = new AcademicWarningProxyController(
                new AcademicAnalysisService(client, records, plans, transcripts, jdbc, mapper));
        when(jdbc.queryForList("SELECT major, grade FROM t_student WHERE student_no = ?", "20260001"))
                .thenReturn(List.of(Map.of("major", "计算机科学与技术", "grade", "2026")));
        when(transcripts.requireUserId("20260001")).thenReturn(11L);
        plan = new TrainingPlanItem();
        plan.setId(33L);
        plan.setMajor("计算机科学与技术");
        plan.setGrade("2026");
        plan.setJsonContent("""
                {"courses":[{"category":"部类基础课","courseName":"程序设计","credits":4,"offeredAt":"1"}]}
                """);
        when(plans.getLatest("计算机科学与技术", "2026")).thenReturn(plan);
        pythonResponse = Map.of("data", Map.of("report", Map.of("warning_level", "正常")));
        request = new MockHttpServletRequest();
        request.setAttribute(AuthContext.SUBJECT_ATTRIBUTE, "20260001");
        request.setAttribute(AuthContext.ROLE_ATTRIBUTE, "student");
    }

    @Test
    void forwardsBasicPoliticalAndCommonCoursesWithSemestersAndTrustedStudentIdentity() throws Exception {
        plan.setJsonContent("""
                {"courses":[
                  {"category":"部类核心课","courseName":"高等数学Ⅰ","credits":5,"offeredAt":"1"},
                  {"category":"思想政治理论课","courseName":"思想道德与法治","credits":3,"offeredAt":"1"},
                  {"category":"专业核心课","courseName":"操作系统","credits":4,"offeredAt":"4"},
                  {"category":"部类基础课","courseName":"程序设计","credits":4,"offeredAt":"1"},
                  {"category":"专业选修课","courseName":"摄影","credits":2,"offeredAt":"6"}
                ]}
                """);
        when(client.analyzeTranscript(any(), eq("20260001"), anyString())).thenReturn(pythonResponse);
        when(transcripts.save(eq("20260001"), any())).thenReturn(savedFile());
        controller.analyzeTranscript(new MockMultipartFile("file", "transcript.pdf", "application/pdf", new byte[]{1}), "other-student", request);

        ArgumentCaptor<String> normalized = ArgumentCaptor.forClass(String.class);
        verify(client).analyzeTranscript(any(), eq("20260001"), normalized.capture());
        JsonNode sent = mapper.readTree(normalized.getValue());
        assertEquals(4, sent.get("core_courses").size());
        assertEquals("思想道德与法治", sent.get("core_courses").get(1).asText());
        assertEquals("部类共同课", sent.get("core_course_details").get(0).get("category").asText());
        assertEquals("4", sent.get("core_course_details").get(2).get("offered_at").asText());
        assertEquals("程序设计", sent.get("core_courses").get(3).asText());
        assertEquals("部类基础课", sent.get("core_course_details").get(3).get("category").asText());
        assertEquals("1", sent.get("core_course_details").get(3).get("offered_at").asText());
        assertEquals("2026", sent.get("grade").asText());
        assertEquals(18.0, sent.get("required_credits").asDouble());
        verify(records).saveFromPythonResponse(11L, "20260001", 22L, 33L, pythonResponse);
    }

    @Test
    void reanalyzesSavedFileWithLatestPlanWithoutUploadingAnotherCopy() throws Exception {
        FileRecord file = savedFile();
        Path path = Path.of("uploads", "saved-transcript.pdf");
        when(transcripts.requireCurrent("20260001")).thenReturn(file);
        when(transcripts.resolveForAnalysis("20260001", file)).thenReturn(path);
        when(client.analyzeStoredTranscript(eq(path), eq("transcript.pdf"), eq("20260001"), anyString())).thenReturn(pythonResponse);
        assertEquals(200, controller.analyzeSavedTranscript("other-student", request).getCode());
        verify(records).saveFromPythonResponse(11L, "20260001", 22L, 33L, pythonResponse);

        plan.setId(44L);
        plan.setJsonContent("""
                {"courses":[{"category":"专业核心课","courseName":"操作系统","credits":4,"offeredAt":"4"}]}
                """);
        assertEquals(200, controller.analyzeSavedTranscript(null, request).getCode());
        verify(records).saveFromPythonResponse(11L, "20260001", 22L, 44L, pythonResponse);
        ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
        verify(client, times(2)).analyzeStoredTranscript(eq(path), eq("transcript.pdf"), eq("20260001"), sent.capture());
        assertEquals("程序设计", mapper.readTree(sent.getAllValues().get(0)).get("core_courses").get(0).asText());
        assertEquals("操作系统", mapper.readTree(sent.getAllValues().get(1)).get("core_courses").get(0).asText());
        verify(transcripts, never()).save(anyString(), any());
        verify(client, never()).analyzeTranscript(any(), anyString(), anyString());
    }

    @Test
    void noSavedTranscriptDoesNotCallAnalysisOrSaveAnEmptyReport() {
        when(transcripts.requireCurrent("20260001")).thenThrow(new ApiException(HttpStatus.NOT_FOUND, "请先上传成绩单"));
        ApiException error = assertThrows(ApiException.class, () -> controller.analyzeSavedTranscript(null, request));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatus());
        verifyNoInteractions(client, records);
    }

    private FileRecord savedFile() {
        FileRecord file = new FileRecord();
        file.setId(22L);
        file.setOriginalName("transcript.pdf");
        file.setUploaderId(11L);
        file.setBusinessType("transcript");
        return file;
    }

    @Test
    void missingProfileRetainsLegacy404ResultWithoutWritingAnything() throws Exception {
        when(jdbc.queryForList("SELECT major, grade FROM t_student WHERE student_no = ?", "20260001"))
                .thenReturn(List.of());
        assertEquals(404, controller.analyzeTranscript(
                new MockMultipartFile("file", "grades.pdf", "application/pdf", new byte[]{1}), null, request).getCode());
        verifyNoInteractions(client, records);
        verify(transcripts, never()).save(anyString(), any());
    }

    @Test
    void missingPlanRetainsLegacy404ResultWithoutWritingAnything() throws Exception {
        when(plans.getLatest("计算机科学与技术", "2026")).thenReturn(null);
        assertEquals(404, controller.analyzeTranscript(
                new MockMultipartFile("file", "grades.pdf", "application/pdf", new byte[]{1}), null, request).getCode());
        verifyNoInteractions(client, records);
        verify(transcripts, never()).save(anyString(), any());
    }
}
