package com.college.student_service_platform;

import com.college.student_service_platform.service.WarningRecordService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WarningRecordServiceTest {
    private JdbcTemplate jdbcTemplate;
    private WarningRecordService service;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:warning-record;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("DROP ALL OBJECTS");
        jdbcTemplate.execute("""
                CREATE TABLE t_warning_record (
                    id BIGINT PRIMARY KEY, user_id BIGINT, student_no VARCHAR(50) NOT NULL,
                    transcript_file_id BIGINT, training_plan_id BIGINT, warning_level VARCHAR(50),
                    total_earned_credits DECIMAL(10,2), course_count INT, core_course_count INT,
                    failed_course_count INT, missing_course_count INT, parsed_courses_json TEXT,
                    core_courses_json TEXT, failed_courses_json TEXT, missing_courses_json TEXT,
                    suggestions_json TEXT, analysis_status INT, error_message VARCHAR(1000),
                    created_at TIMESTAMP, updated_at TIMESTAMP
                )
                """);
        service = new WarningRecordService(jdbcTemplate, new ObjectMapper());
    }

    @Test
    void persistsTheStudentFilePlanAndAnalysisDetailsTogether() {
        Map<String, Object> report = Map.of(
                "warning_level", "一般预警",
                "total_earned_credits", 82.5,
                "core_courses", List.of("数据结构"),
                "failed_courses", List.of(Map.of("name", "高等数学", "score", "50")),
                "missing_core_courses", List.of("操作系统"),
                "course_suggestions", List.of("建议重修高等数学")
        );
        Map<String, Object> response = Map.of(
                "status", "success",
                "data", Map.of(
                        "courses", List.of(Map.of("name", "数据结构", "credit", 4)),
                        "report", report
                )
        );

        service.saveFromPythonResponse(11L, "20260001", 22L, 33L, response);

        Map<String, Object> row = jdbcTemplate.queryForMap("SELECT * FROM t_warning_record");
        assertEquals(11L, ((Number) row.get("USER_ID")).longValue());
        assertEquals(22L, ((Number) row.get("TRANSCRIPT_FILE_ID")).longValue());
        assertEquals(33L, ((Number) row.get("TRAINING_PLAN_ID")).longValue());
        assertEquals(1, ((Number) row.get("COURSE_COUNT")).intValue());
        assertEquals(1, ((Number) row.get("CORE_COURSE_COUNT")).intValue());
        assertEquals(1, ((Number) row.get("FAILED_COURSE_COUNT")).intValue());
        assertEquals(1, ((Number) row.get("MISSING_COURSE_COUNT")).intValue());
        assertTrue(String.valueOf(row.get("CORE_COURSES_JSON")).contains("数据结构"));
    }
}
