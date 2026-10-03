package com.college.student_service_platform;

import com.college.student_service_platform.common.ApiException;
import com.college.student_service_platform.common.AuthContext;
import com.college.student_service_platform.controller.FileController;
import com.college.student_service_platform.service.UserIdentityService;
import com.college.student_service_platform.dto.CertificateApplyDetail;
import com.college.student_service_platform.dto.CertificateApplyItem;
import com.college.student_service_platform.dto.CertificateApplySubmitRequest;
import com.college.student_service_platform.dto.CertificateDecisionRequest;
import com.college.student_service_platform.entity.FileRecord;
import com.college.student_service_platform.service.CertificateApplyService;
import com.college.student_service_platform.service.CertificateFileService;
import com.college.student_service_platform.service.FileService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.Resource;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CertificateApplyServiceTest {
    @TempDir
    Path uploadDir;

    private JdbcTemplate jdbcTemplate;
    private CertificateApplyService service;
    private FileService fileService;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:certificate-service;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("DROP ALL OBJECTS");
        jdbcTemplate.execute("""
                CREATE TABLE t_student (
                    id BIGINT PRIMARY KEY,
                    student_no VARCHAR(50) NOT NULL UNIQUE,
                    name VARCHAR(100) NOT NULL,
                    id_card_no VARCHAR(18),
                    class_name VARCHAR(100) NOT NULL,
                    major VARCHAR(100) NOT NULL,
                    grade VARCHAR(50) NOT NULL,
                    education_level VARCHAR(20),
                    contact VARCHAR(100),
                    status INT NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE t_user (
                    id BIGINT PRIMARY KEY,
                    username VARCHAR(100) NOT NULL,
                    role_code VARCHAR(50) NOT NULL,
                    student_no VARCHAR(50),
                    status INT NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE t_file (
                    id BIGINT PRIMARY KEY,
                    original_name VARCHAR(255) NOT NULL,
                    stored_name VARCHAR(255) NOT NULL,
                    file_path VARCHAR(500) NOT NULL,
                    file_type VARCHAR(100),
                    file_size BIGINT,
                    uploader_id BIGINT,
                    business_type VARCHAR(100),
                    created_at TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE t_certificate_apply (
                    id BIGINT PRIMARY KEY,
                    student_no VARCHAR(50) NOT NULL,
                    certificate_type VARCHAR(100) NOT NULL,
                    apply_status VARCHAR(50),
                    extra_data VARCHAR(500),
                    file_id BIGINT,
                    created_at TIMESTAMP,
                    updated_at TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE t_notification (
                    id BIGINT PRIMARY KEY,
                    file_id BIGINT
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE t_approval_task (
                    id BIGINT PRIMARY KEY,
                    apply_id BIGINT NOT NULL,
                    node_order INT NOT NULL,
                    approver_id BIGINT,
                    approver_name VARCHAR(100),
                    approval_status VARCHAR(50),
                    opinion VARCHAR(500),
                    handled_at TIMESTAMP,
                    created_at TIMESTAMP,
                    updated_at TIMESTAMP
                )
                """);
        jdbcTemplate.update("""
                INSERT INTO t_student
                (id, student_no, name, id_card_no, class_name, major, grade, education_level, contact, status)
                VALUES (1, '20260001', '张三', '110101200001010011', '计科2601', '计算机科学与技术', '2026', '本科', '13800000000', 1)
                """);
        jdbcTemplate.update("""
                INSERT INTO t_student
                (id, student_no, name, class_name, major, grade, education_level, status)
                VALUES (2, '20260002', '李四', '计科2602', '软件工程', '2026', '本科', 1)
                """);
        jdbcTemplate.update("""
                INSERT INTO t_user(id, username, role_code, student_no, status)
                VALUES (10, 'admin', 'admin', NULL, 1)
                """);

        CertificateFileService certificateFileService = new CertificateFileService(
                jdbcTemplate,
                new ObjectMapper(),
                uploadDir.toString()
        );
        service = new CertificateApplyService(jdbcTemplate, certificateFileService);
        fileService = new FileService(jdbcTemplate, uploadDir.toString());
    }

    @Test
    void submitApproveGenerateDownloadAndDeleteCertificate() throws Exception {
        Long applyId = service.submit(request("20260001", "在校证明"));

        List<CertificateApplyItem> pending = service.listByStudentNo("20260001");
        assertEquals(1, pending.size());
        assertEquals("待审核", pending.get(0).getApplyStatus());
        assertEquals(0, service.listByStudentNo("20260002").size());

        CertificateDecisionRequest decision = new CertificateDecisionRequest();
        decision.setDecision("approved");
        decision.setOpinion("材料完整，同意出具");
        service.decide(applyId, decision, 10L, "admin");

        CertificateApplyDetail detail = service.detail(applyId);
        assertEquals("已通过", detail.getApply().getApplyStatus());
        assertEquals("已通过", detail.getTasks().get(0).getApprovalStatus());
        assertEquals("材料完整，同意出具", detail.getTasks().get(0).getOpinion());
        assertNotNull(detail.getApply().getFileId());

        Long fileId = detail.getApply().getFileId();
        fileService.assertCanDownload(fileId, "20260001", false);
        assertThrows(ApiException.class, () -> fileService.assertCanDownload(fileId, "20260002", false));

        FileRecord file = fileService.getFileById(fileId);
        Path generatedPath = fileService.getFilePath(file);
        assertTrue(Files.exists(generatedPath));
        assertEquals("certificate", file.getBusinessType());
        assertTrue(file.getOriginalName().endsWith(".docx"));

        MockHttpServletRequest downloadRequest = new MockHttpServletRequest();
        downloadRequest.setAttribute(AuthContext.SUBJECT_ATTRIBUTE, "20260001");
        downloadRequest.setAttribute(AuthContext.ROLE_ATTRIBUTE, "student");
        FileController files = new FileController(fileService, org.mockito.Mockito.mock(UserIdentityService.class));
        ResponseEntity<Resource> download = files.downloadFile(fileId, downloadRequest);
        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                download.getHeaders().getContentType().toString());
        assertEquals(Files.size(generatedPath), download.getHeaders().getContentLength());
        assertTrue(download.getHeaders().getFirst("Content-Disposition").contains(".docx"));
        try (InputStream input = download.getBody().getInputStream();
             XWPFDocument downloaded = new XWPFDocument(input)) {
            assertTrue(downloaded.getParagraphs().stream().anyMatch(p -> p.getText().contains("张三")));
        }

        try (InputStream input = Files.newInputStream(generatedPath);
             XWPFDocument document = new XWPFDocument(input)) {
            String text = document.getParagraphs().stream()
                    .map(paragraph -> paragraph.getText())
                    .reduce("", (left, right) -> left + "\n" + right);
            assertTrue(text.contains("在校证明"));
            assertTrue(text.contains("张三"));
            assertTrue(text.contains("材料完整，同意出具"));
        }

        service.delete(applyId);
        assertEquals(0, service.listByStudentNo("20260001").size());
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM t_file", Integer.class));
        assertFalse(Files.exists(generatedPath));
    }

    @Test
    void rejectionDoesNotGenerateFileAndUnknownStudentCannotSubmit() {
        Long applyId = service.submit(request("20260001", "请假条"));
        CertificateDecisionRequest decision = new CertificateDecisionRequest();
        decision.setDecision("rejected");
        decision.setOpinion("时间范围不完整");
        service.decide(applyId, decision, 10L, "admin");

        CertificateApplyItem rejected = service.detail(applyId).getApply();
        assertEquals("已驳回", rejected.getApplyStatus());
        assertNull(rejected.getFileId());
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM t_file", Integer.class));

        assertThrows(IllegalArgumentException.class, () -> service.submit(request("20999999", "在校证明")));
        assertThrows(IllegalArgumentException.class, () -> service.submit(request("20260001", "未知证明")));
    }

    private CertificateApplySubmitRequest request(String studentNo, String type) {
        CertificateApplySubmitRequest request = new CertificateApplySubmitRequest();
        request.setStudentNo(studentNo);
        request.setCertificateType(type);
        request.setExtraData("{\"reason\":\"申请奖学金\",\"startAt\":\"2026-09-01\",\"endAt\":\"2026-09-30\"}");
        return request;
    }
}
