package com.college.student_service_platform;

import com.college.student_service_platform.dto.StudentImportItem;
import com.college.student_service_platform.dto.StudentImportResult;
import com.college.student_service_platform.dto.StudentListItem;
import com.college.student_service_platform.service.PasswordService;
import com.college.student_service_platform.service.StudentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StudentServiceTest {
    private JdbcTemplate jdbcTemplate;
    private StudentService service;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:student-service;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("DROP ALL OBJECTS");
        jdbcTemplate.execute("""
                CREATE TABLE t_student (
                    id BIGINT PRIMARY KEY,
                    student_no VARCHAR(50) NOT NULL UNIQUE,
                    name VARCHAR(100) NOT NULL,
                    id_card_no VARCHAR(18),
                    gender VARCHAR(20),
                    ethnicity VARCHAR(50),
                    class_name VARCHAR(100) NOT NULL,
                    major VARCHAR(100) NOT NULL,
                    grade VARCHAR(50) NOT NULL,
                    education_level VARCHAR(20),
                    contact VARCHAR(100),
                    status INT,
                    created_at TIMESTAMP,
                    updated_at TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE t_user (
                    id BIGINT PRIMARY KEY,
                    username VARCHAR(100) NOT NULL,
                    password VARCHAR(255),
                    role_code VARCHAR(50) NOT NULL,
                    student_no VARCHAR(50) UNIQUE,
                    wechat_openid VARCHAR(100),
                    status INT,
                    created_at TIMESTAMP,
                    updated_at TIMESTAMP
                )
                """);

        service = new StudentService(jdbcTemplate, new PasswordService(10), "default123");
    }

    @Test
    void importsListsUpdatesAndDeletesStudentWithoutRemovedTablesOrColumns() {
        StudentImportItem student = student("20260001", "张三", "计算机科学与技术", "13800000000");

        StudentImportResult created = service.importStudents(List.of(student));
        assertEquals(1, created.getInserted());
        assertEquals(0, created.getUpdated());

        List<StudentListItem> students = service.listStudents("张三");
        assertEquals(1, students.size());
        assertEquals("20260001", students.get(0).getStudentNo());
        assertEquals("计算机科学与技术", students.get(0).getMajor());
        assertEquals("student", students.get(0).getRoleCode());
        assertEquals(1, students.get(0).getStatus());
        assertTrue(jdbcTemplate.queryForObject(
                "SELECT password FROM t_user WHERE student_no = ?",
                String.class,
                "20260001"
        ).startsWith("$2"));

        student.setMajor("软件工程");
        student.setContact("13900000000");
        student.setPassword("");
        StudentImportResult updated = service.importStudents(List.of(student));
        assertEquals(0, updated.getInserted());
        assertEquals(1, updated.getUpdated());

        StudentListItem changed = service.listStudents("20260001").get(0);
        assertEquals("软件工程", changed.getMajor());
        assertEquals("13900000000", changed.getContact());

        service.deleteByStudentNo("20260001");
        assertEquals(0, service.listStudents(null).size());
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_user WHERE student_no = ?",
                Integer.class,
                "20260001"
        ));
    }

    private StudentImportItem student(String studentNo, String name, String major, String contact) {
        StudentImportItem item = new StudentImportItem();
        item.setStudentNo(studentNo);
        item.setName(name);
        item.setGender("男");
        item.setEthnicity("汉族");
        item.setClassName("计科2601");
        item.setMajor(major);
        item.setGrade("2026");
        item.setEducationLevel("本科");
        item.setContact(contact);
        item.setPassword("student123");
        item.setStatus(1);
        return item;
    }
}
