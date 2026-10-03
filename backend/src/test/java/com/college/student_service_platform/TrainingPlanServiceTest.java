package com.college.student_service_platform;

import com.college.student_service_platform.dto.TrainingPlanItem;
import com.college.student_service_platform.dto.TrainingPlanSaveRequest;
import com.college.student_service_platform.service.TrainingPlanService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrainingPlanServiceTest {
    private TrainingPlanService service;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:training-plan;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("DROP ALL OBJECTS");
        jdbcTemplate.execute("""
                CREATE TABLE t_training_plan (
                    id BIGINT PRIMARY KEY,
                    major VARCHAR(100) NOT NULL,
                    grade VARCHAR(50) NOT NULL,
                    version VARCHAR(50) NOT NULL,
                    remark VARCHAR(500),
                    json_content TEXT NOT NULL,
                    course_count INT,
                    total_credits DECIMAL(10, 2),
                    created_at TIMESTAMP,
                    updated_at TIMESTAMP
                )
                """);
        service = new TrainingPlanService(jdbcTemplate, new ObjectMapper());
    }

    @Test
    void derivesStatisticsRejectsDuplicatesAndReturnsMostRecentlyUpdatedPlan() throws Exception {
        TrainingPlanSaveRequest v1 = request("v1.0", """
                {"courses":[
                  {"category":"专业核心课","courseName":"数据结构","credits":4},
                  {"category":"专业选修课","courseName":"人工智能","credits":3.5}
                ]}
                """);
        v1.setCourseCount(99);
        v1.setTotalCredits(new BigDecimal("999"));
        Long v1Id = service.save(v1);

        TrainingPlanItem stored = service.getById(v1Id);
        assertEquals(2, stored.getCourseCount());
        assertEquals(0, new BigDecimal("7.50").compareTo(stored.getTotalCredits()));

        assertThrows(IllegalArgumentException.class, () -> service.save(request("v1.0", v1.getJsonContent())));

        Long v2Id = service.save(request("v2.0", v1.getJsonContent()));
        assertEquals(v2Id, service.getLatest("计算机科学与技术", "2026").getId());

        Thread.sleep(5);
        v1.setId(v1Id);
        v1.setRemark("修订版");
        service.save(v1);
        assertEquals(v1Id, service.getLatest("计算机科学与技术", "2026").getId());
    }

    @Test
    void acceptsPoliticalAndCommonCoursesAndNormalizesStoredLegacyCategory() throws Exception {
        Long id = service.save(request("v1.0", """
                {"courses":[
                  {"category":"部类核心课","courseName":"高等数学Ⅰ","credits":5,"offeredAt":"1"},
                  {"category":"思想政治理论课","courseName":"思想道德与法治","credits":3,"offeredAt":"1"}
                ]}
                """));
        TrainingPlanItem stored = service.getById(id);
        assertTrue(stored.getJsonContent().contains("部类共同课"));
        assertTrue(!stored.getJsonContent().contains("部类核心课"));
        assertEquals(2, stored.getCourseCount());

        Long politicalOnly = service.save(request("v2.0", """
                {"courses":[{"category":"思想政治理论课","courseName":"思想道德与法治","credits":3}]}
                """));
        assertEquals(1, service.getById(politicalOnly).getCourseCount());
        Long commonOnly = service.save(request("v3.0", """
                {"courses":[{"category":"部类共同课","courseName":"高等数学Ⅰ","credits":5}]}
                """));
        assertEquals(1, service.getById(commonOnly).getCourseCount());
        Long basicOnly = service.save(request("v4.0", """
                {"courses":[{"category":"部类基础课","courseName":"程序设计","credits":4,"offeredAt":"1"}]}
                """));
        assertEquals(1, service.getById(basicOnly).getCourseCount());
    }

    @Test
    void rejectsPlanThatCannotDriveCoreCourseAnalysis() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.save(request("v1.0", """
                        {"courses":[{"category":"选修课","courseName":"摄影","credits":2}]}
                        """))
        );
        assertTrue(error.getMessage().contains("核心"));
    }

    private TrainingPlanSaveRequest request(String version, String json) {
        TrainingPlanSaveRequest request = new TrainingPlanSaveRequest();
        request.setMajor("计算机科学与技术");
        request.setGrade("2026");
        request.setVersion(version);
        request.setJsonContent(json);
        return request;
    }
}
