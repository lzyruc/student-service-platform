package com.college.student_service_platform;

import com.college.student_service_platform.common.ApiException;
import com.college.student_service_platform.dto.PolicyDocumentItem;
import com.college.student_service_platform.dto.PolicyDocumentPageResponse;
import com.college.student_service_platform.dto.PolicyDocumentSaveRequest;
import com.college.student_service_platform.repository.PolicyDocumentRepository;
import com.college.student_service_platform.service.PolicyDocumentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PolicyDocumentServiceTest {
    private JdbcTemplate jdbcTemplate;
    private PolicyDocumentService service;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:policy-document;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("DROP ALL OBJECTS");
        jdbcTemplate.execute("""
                CREATE TABLE t_user (
                    id BIGINT PRIMARY KEY,
                    username VARCHAR(100) NOT NULL,
                    role_code VARCHAR(50) NOT NULL,
                    status INT NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE t_file (
                    id BIGINT PRIMARY KEY,
                    original_name VARCHAR(255) NOT NULL,
                    file_type VARCHAR(100),
                    file_size BIGINT,
                    business_type VARCHAR(100)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE t_policy_doc (
                    id BIGINT PRIMARY KEY,
                    title VARCHAR(200) NOT NULL,
                    category VARCHAR(50) NOT NULL,
                    audience VARCHAR(50) NOT NULL,
                    version VARCHAR(50) NOT NULL,
                    effective_date DATE,
                    expiry_date DATE,
                    tags VARCHAR(2000),
                    content CLOB,
                    keywords VARCHAR(500),
                    official_url VARCHAR(500),
                    remark VARCHAR(1000),
                    file_id BIGINT NOT NULL UNIQUE,
                    created_by BIGINT,
                    doc_status VARCHAR(32) NOT NULL,
                    ingest_status VARCHAR(32) NOT NULL,
                    ingest_error VARCHAR(1000),
                    chunk_count INT NOT NULL,
                    content_hash CHAR(64),
                    last_ingested_at TIMESTAMP,
                    created_at TIMESTAMP NOT NULL,
                    updated_at TIMESTAMP NOT NULL
                )
                """);
        jdbcTemplate.update(
                "INSERT INTO t_user(id, username, role_code, status) VALUES (1, 'admin', 'admin', 1)");
        jdbcTemplate.update("""
                INSERT INTO t_file(id, original_name, file_type, file_size, business_type)
                VALUES (10, '本科生学籍管理规定.pdf', 'application/pdf', 1024, 'policy')
                """);
        jdbcTemplate.update("""
                INSERT INTO t_file(id, original_name, file_type, file_size, business_type)
                VALUES (11, '普通附件.txt', 'text/plain', 128, 'policy')
                """);

        service = new PolicyDocumentService(
                new PolicyDocumentRepository(jdbcTemplate),
                new ObjectMapper()
        );
    }

    @Test
    void createListUpdatePublishAndDeletePolicyDocument() {
        PolicyDocumentSaveRequest createRequest = requestForFile(10L);
        PolicyDocumentItem created = service.create(createRequest, "admin");

        assertEquals("DRAFT", created.docStatus());
        assertEquals("PENDING", created.ingestStatus());
        assertEquals("UNDERGRADUATE", created.audience());
        assertEquals(List.of("本科", "学籍"), created.tags());
        assertEquals("本科生学籍管理规定.pdf", created.fileName());
        assertEquals(1L, created.createdBy());

        PolicyDocumentPageResponse page = service.list(1, 10, "学籍", null, null, null, "pending");
        assertEquals(1L, page.total());
        assertEquals(created.id(), page.records().get(0).id());

        PolicyDocumentItem published = service.publish(created.id());
        assertEquals("PUBLISHED", published.docStatus());

        createRequest.setVersion("v2.0");
        createRequest.setRemark("更新后的备注");
        PolicyDocumentItem updated = service.update(created.id(), createRequest);
        assertEquals("v2.0", updated.version());
        assertEquals("DRAFT", updated.docStatus());
        assertEquals("PENDING", updated.ingestStatus());

        service.delete(created.id());
        ApiException notFound = assertThrows(ApiException.class, () -> service.get(created.id()));
        assertEquals(HttpStatus.NOT_FOUND, notFound.getStatus());
    }

    @Test
    void rejectsDuplicateFileAndNonPdfFile() {
        service.create(requestForFile(10L), "admin");

        ApiException duplicate = assertThrows(
                ApiException.class,
                () -> service.create(requestForFile(10L), "admin")
        );
        assertEquals(HttpStatus.CONFLICT, duplicate.getStatus());

        ApiException nonPdf = assertThrows(
                ApiException.class,
                () -> service.create(requestForFile(11L), "admin")
        );
        assertEquals(HttpStatus.BAD_REQUEST, nonPdf.getStatus());
    }

    @Test
    void rejectsInvalidDateRangeAndDisabledCreator() {
        PolicyDocumentSaveRequest request = requestForFile(10L);
        request.setExpiryDate(LocalDate.of(2026, 1, 1));
        request.setEffectiveDate(LocalDate.of(2026, 2, 1));

        ApiException invalidDates = assertThrows(
                ApiException.class,
                () -> service.create(request, "admin")
        );
        assertEquals(HttpStatus.BAD_REQUEST, invalidDates.getStatus());

        jdbcTemplate.update("UPDATE t_user SET status = 0 WHERE id = 1");
        ApiException disabledAdmin = assertThrows(
                ApiException.class,
                () -> service.create(requestForFile(10L), "admin")
        );
        assertEquals(HttpStatus.FORBIDDEN, disabledAdmin.getStatus());
    }

    private PolicyDocumentSaveRequest requestForFile(Long fileId) {
        PolicyDocumentSaveRequest request = new PolicyDocumentSaveRequest();
        request.setTitle("本科生学籍管理规定");
        request.setCategory("学籍管理");
        request.setAudience("undergraduate");
        request.setVersion("v1.0");
        request.setEffectiveDate(LocalDate.of(2026, 1, 1));
        request.setTags(List.of("本科", "学籍", "本科"));
        request.setKeywords("本科,学籍");
        request.setOfficialUrl("https://example.edu/policy/10");
        request.setRemark("测试政策");
        request.setFileId(fileId);
        return request;
    }
}
