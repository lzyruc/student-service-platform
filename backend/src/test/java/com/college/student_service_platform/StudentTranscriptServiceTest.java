package com.college.student_service_platform;

import com.college.student_service_platform.common.ApiException;
import com.college.student_service_platform.common.AuthContext;
import com.college.student_service_platform.common.JwtUtil;
import com.college.student_service_platform.config.AuthFilter;
import com.college.student_service_platform.common.GlobalExceptionHandler;
import com.college.student_service_platform.controller.StudentTranscriptController;
import com.college.student_service_platform.controller.AcademicWarningProxyController;
import com.college.student_service_platform.dto.TrainingPlanSaveRequest;
import com.college.student_service_platform.dto.StudentTranscriptResponse;
import com.college.student_service_platform.entity.FileRecord;
import com.college.student_service_platform.service.FileService;
import com.college.student_service_platform.service.AcademicAnalysisService;
import com.college.student_service_platform.service.StudentTranscriptService;
import com.college.student_service_platform.service.TrainingPlanService;
import com.college.student_service_platform.service.WarningRecordService;
import com.college.student_service_platform.service.external.AcademicWarningClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class StudentTranscriptServiceTest {
    @TempDir Path uploads;
    private JdbcTemplate jdbc;
    private FileService files;
    private StudentTranscriptService service;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:student-transcript;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("DROP ALL OBJECTS");
        jdbc.execute("CREATE TABLE t_user(id BIGINT PRIMARY KEY, student_no VARCHAR(50), role_code VARCHAR(50))");
        jdbc.execute("INSERT INTO t_user VALUES(11,'20260001','student'),(12,'20260002','student')");
        jdbc.execute("""
                CREATE TABLE t_file (
                    id BIGINT PRIMARY KEY, original_name VARCHAR(255), stored_name VARCHAR(255), file_path VARCHAR(500),
                    file_type VARCHAR(100), file_size BIGINT, uploader_id BIGINT, business_type VARCHAR(100), created_at TIMESTAMP)
                """);
        files = new FileService(jdbc, uploads.toString());
        service = new StudentTranscriptService(jdbc, files);
    }

    @Test
    void savedPdfSurvivesNewServiceInstanceAndRemainsPrivate() throws Exception {
        FileRecord saved = service.save("20260001", pdf("grades.pdf"));
        StudentTranscriptService reopened = new StudentTranscriptService(jdbc, new FileService(jdbc, uploads.toString()));
        StudentTranscriptResponse current = reopened.getCurrent("20260001");
        assertEquals(saved.getId(), current.fileId());
        assertEquals("grades.pdf", current.originalName());
        assertTrue(current.available());
        assertArrayEquals(pdf("grades.pdf").getBytes(), Files.readAllBytes(reopened.resolveForAnalysis("20260001", saved)));
        assertNull(reopened.getCurrent("20260002"));
        ApiException forbidden = assertThrows(ApiException.class, () -> reopened.resolveForAnalysis("20260002", saved));
        assertEquals(HttpStatus.FORBIDDEN, forbidden.getStatus());
    }

    @Test
    void latestTranscriptWinsWithoutSelectingOtherStudentsOrOtherBusinessFiles() throws Exception {
        FileRecord old = service.save("20260001", pdf("old.pdf"));
        FileRecord latest = service.save("20260001", pdf("new.pdf"));
        service.save("20260002", pdf("other-student.pdf"));
        files.uploadFile(pdf("policy.pdf"), "policy", 11L);
        // 同一时间的上传使用 id 稳定排序。
        jdbc.update("UPDATE t_file SET created_at = TIMESTAMP '2026-10-02 12:00:00'");
        assertEquals(latest.getId(), service.requireCurrent("20260001").getId());
        assertTrue(Files.isRegularFile(files.getFilePath(old)));
    }

    @Test
    void missingDiskFilePromptsReuploadAndDoesNotPretendItIsAvailable() throws Exception {
        FileRecord saved = service.save("20260001", pdf("grades.pdf"));
        Files.delete(files.getFilePath(saved));
        assertFalse(service.getCurrent("20260001").available());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ApiException.class, () -> service.requireCurrent("20260001")).getStatus());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ApiException.class, () -> service.requireCurrent("20260002")).getStatus());
    }

    @Test
    void rejectsFakePdfAndNonPdfWithoutCreatingFileRows() {
        assertThrows(IllegalArgumentException.class, () -> service.save("20260001",
                new MockMultipartFile("file", "fake.pdf", "application/pdf", "not a PDF".getBytes(StandardCharsets.UTF_8))));
        assertThrows(IllegalArgumentException.class, () -> service.save("20260001",
                new MockMultipartFile("file", "grades.txt", "text/plain", "%PDF-1.7".getBytes(StandardCharsets.US_ASCII))));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM t_file", Integer.class));
    }

    @Test
    void uploadOnceThenAnalyzeRepeatedlyWritesReportsWithoutDuplicatingFiles() throws Exception {
        jdbc.execute("CREATE TABLE t_student(student_no VARCHAR(50), major VARCHAR(100), grade VARCHAR(50))");
        jdbc.execute("INSERT INTO t_student VALUES('20260001','计算机科学与技术','2026')");
        jdbc.execute("""
                CREATE TABLE t_training_plan (
                    id BIGINT PRIMARY KEY, major VARCHAR(100), grade VARCHAR(50), version VARCHAR(50), remark VARCHAR(500),
                    json_content TEXT, course_count INT, total_credits DECIMAL(10,2), created_at TIMESTAMP, updated_at TIMESTAMP)
                """);
        jdbc.execute("""
                CREATE TABLE t_warning_record (
                    id BIGINT PRIMARY KEY, user_id BIGINT, student_no VARCHAR(50), transcript_file_id BIGINT,
                    training_plan_id BIGINT, warning_level VARCHAR(50), total_earned_credits DECIMAL(10,2),
                    course_count INT, core_course_count INT, failed_course_count INT, missing_course_count INT,
                    parsed_courses_json TEXT, core_courses_json TEXT, failed_courses_json TEXT, missing_courses_json TEXT,
                    suggestions_json TEXT, analysis_status INT, error_message VARCHAR(1000), created_at TIMESTAMP, updated_at TIMESTAMP)
                """);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        TrainingPlanService plans = new TrainingPlanService(jdbc, mapper);
        TrainingPlanSaveRequest first = plan("v1.0", "程序设计");
        Long firstPlan = plans.save(first);
        FileRecord transcript = service.save("20260001", pdf("grades.pdf"));
        AcademicWarningClient client = mock(AcademicWarningClient.class);
        when(client.analyzeStoredTranscript(any(), eq("grades.pdf"), eq("20260001"), anyString()))
                .thenReturn(Map.of("data", Map.of("courses", List.of(Map.of("name", "操作系统", "credit", 4)),
                        "report", Map.of("warning_level", "正常", "total_earned_credits", 4))));
        StudentTranscriptService reopened = new StudentTranscriptService(jdbc, new FileService(jdbc, uploads.toString()));
        AcademicAnalysisService analysis = new AcademicAnalysisService(
                client, new WarningRecordService(jdbc, mapper), plans, reopened, jdbc, mapper);
        AcademicWarningProxyController controller = new AcademicWarningProxyController(analysis);
        JwtUtil jwt = new JwtUtil("0123456789abcdef0123456789abcdef", "test", Duration.ofHours(1));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new AuthFilter(jwt, mapper)).build();
        String token = "Bearer " + jwt.createToken("20260001", "student");
        mvc.perform(post("/api/student/warning/analyze-saved")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/student/warning/analyze-saved").header("Authorization", token)).andExpect(status().isForbidden());
        mvc.perform(post("/api/student/warning/analyze-saved").param("studentNo", "20260002").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        Long latestPlan = plans.save(plan("v2.0", "操作系统"));
        mvc.perform(post("/api/student/warning/analyze-saved").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM t_file", Integer.class));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM t_warning_record", Integer.class));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM t_warning_record WHERE transcript_file_id = ? AND student_no = ?",
                Integer.class, transcript.getId(), "20260001"));
        assertEquals(List.of(firstPlan, latestPlan), jdbc.queryForList("SELECT training_plan_id FROM t_warning_record ORDER BY id", Long.class));
        MockHttpServletRequest chatRequest = new MockHttpServletRequest();
        chatRequest.setAttribute(AuthContext.SUBJECT_ATTRIBUTE, "20260001");
        chatRequest.setAttribute(AuthContext.ROLE_ATTRIBUTE, "student");
        chatRequest.setParameter("studentNo", "20260002");
        var chat = analysis.createReadContext(chatRequest);
        var snapshot = chat.getSnapshot();
        assertSame(snapshot, chat.getSnapshot());
        assertEquals(latestPlan, snapshot.trainingPlanId());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM t_file", Integer.class));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM t_warning_record", Integer.class));
        verify(client, times(3)).analyzeStoredTranscript(eq(files.getFilePath(transcript)), eq("grades.pdf"), eq("20260001"), anyString());
    }

    @Test
    void studentApiUploadsAndReadsFromJwtEvenWhenClientSpoofsStudentNumber() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        JwtUtil jwt = new JwtUtil("0123456789abcdef0123456789abcdef", "test", Duration.ofHours(1));
        String token = "Bearer " + jwt.createToken("20260001", "student");
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new StudentTranscriptController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).addFilters(new AuthFilter(jwt, mapper)).build();
        mvc.perform(get("/api/student/transcript")).andExpect(status().isUnauthorized());
        mvc.perform(multipart("/api/student/transcript").file(pdf("saved.pdf"))
                        .param("studentNo", "20260002").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.originalName").value("saved.pdf"))
                .andExpect(jsonPath("$.data.available").value(true)).andExpect(jsonPath("$.data.filePath").doesNotExist());
        mvc.perform(get("/api/student/transcript").param("studentNo", "20260002").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.originalName").value("saved.pdf"));
        assertNull(service.getCurrent("20260002"));
        mvc.perform(put("/api/student/transcript").header("Authorization", token)).andExpect(status().isForbidden());
        mvc.perform(get("/api/student/transcript/other-file").header("Authorization", token)).andExpect(status().isForbidden());
    }

    private MockMultipartFile pdf(String filename) {
        return new MockMultipartFile("file", filename, "application/pdf", "%PDF-1.7\nstudent-transcript-test".getBytes(StandardCharsets.US_ASCII));
    }

    private TrainingPlanSaveRequest plan(String version, String course) {
        TrainingPlanSaveRequest request = new TrainingPlanSaveRequest();
        request.setMajor("计算机科学与技术");
        request.setGrade("2026");
        request.setVersion(version);
        request.setJsonContent("{\"courses\":[{\"category\":\"部类基础课\",\"courseName\":\"" + course + "\",\"credits\":4,\"offeredAt\":\"1\"}]}");
        return request;
    }
}
