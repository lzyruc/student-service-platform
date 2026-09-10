package com.college.student_service_platform.controller;

import com.college.student_service_platform.common.Result;
import com.college.student_service_platform.common.AuthContext;
import jakarta.servlet.http.HttpServletRequest;
import com.college.student_service_platform.dto.StudentImportRequest;
import com.college.student_service_platform.dto.StudentImportResult;
import com.college.student_service_platform.dto.StudentListItem;
import com.college.student_service_platform.service.StudentService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/student")
public class StudentController {
    private static final Logger log = LoggerFactory.getLogger(StudentController.class);

    private final JdbcTemplate jdbcTemplate;
    private final StudentService studentService;
    private final ObjectMapper objectMapper;

    public StudentController(JdbcTemplate jdbcTemplate, StudentService studentService, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.studentService = studentService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/import")
    public Result<StudentImportResult> importStudents(@RequestBody StudentImportRequest request) {
        StudentImportResult result = studentService.importStudents(request == null ? null : request.getStudents());
        return Result.success("Import succeeded", result);
    }

    @GetMapping("/list")
    public Result<List<StudentListItem>> listStudents(@RequestParam(value = "keyword", required = false) String keyword) {
        return Result.success(studentService.listStudents(keyword));
    }

    @DeleteMapping("/{studentNo}")
    public Result<Void> deleteStudent(@PathVariable String studentNo) {
        studentService.deleteByStudentNo(studentNo);
        return Result.success();
    }

    @GetMapping("/info")
    public Result<Map<String, Object>> getStudentInfo(
            @RequestParam(value = "account", required = false) String requestedAccount,
            HttpServletRequest request) {
        String account = AuthContext.studentNo(request, requestedAccount);
        Map<String, Object> responseData = new HashMap<>();

        try {
            String sqlStudent = "SELECT name, major, grade FROM t_student WHERE student_no = ?";
            List<Map<String, Object>> students = jdbcTemplate.queryForList(sqlStudent, account);

            Map<String, Object> userInfo = new HashMap<>();
            if (!students.isEmpty()) {
                Map<String, Object> stu = students.get(0);
                userInfo.put("name", stu.get("name"));
                userInfo.put("major", stu.get("major"));
                userInfo.put("grade", stu.get("grade"));
            } else {
                userInfo.put("name", "\u672a\u5f55\u5165");
                userInfo.put("major", "\u672a\u5206\u914d\u4e13\u4e1a");
                userInfo.put("grade", "\u672a\u77e5\u5e74\u7ea7");
            }
            responseData.put("userInfo", userInfo);

            List<Map<String, Object>> courses = new ArrayList<>();
            double totalCredits = 0.0;

            try {
                String sqlWarning = """
                        SELECT parsed_courses_json, total_earned_credits
                        FROM t_warning_record
                        WHERE student_no = ?
                        ORDER BY created_at DESC
                        LIMIT 1
                        """;
                List<Map<String, Object>> warningRecords = jdbcTemplate.queryForList(sqlWarning, account);

                if (!warningRecords.isEmpty()) {
                    Map<String, Object> record = warningRecords.get(0);
                    String coursesJson = record.get("parsed_courses_json") == null
                            ? null
                            : String.valueOf(record.get("parsed_courses_json"));

                    if (coursesJson != null && !coursesJson.isBlank()) {
                        courses = parseCourses(coursesJson);
                    }

                    Object creditsObj = record.get("total_earned_credits");
                    if (creditsObj != null) {
                        totalCredits = Double.parseDouble(creditsObj.toString());
                    }
                }
            } catch (Exception e) {
                log.warn("Unable to parse the latest warning record for student {}", account, e);
            }

            responseData.put("courses", courses);
            responseData.put("totalCredits", totalCredits);

            return Result.success("Student info loaded", responseData);
        } catch (Exception e) {
            log.error("Failed to query student info for {}", account, e);
            return Result.fail("Failed to query student info");
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseCourses(String coursesJson) throws Exception {
        List<Map<String, Object>> rawCourses;
        try {
            rawCourses = objectMapper.readValue(coursesJson, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            Map<String, Object> reportData = objectMapper.readValue(coursesJson, new TypeReference<Map<String, Object>>() {});
            Object coursesObj = reportData.get("courses");
            rawCourses = coursesObj instanceof List<?> ? (List<Map<String, Object>>) coursesObj : new ArrayList<>();
        }

        List<Map<String, Object>> normalized = new ArrayList<>();
        for (Map<String, Object> course : rawCourses) {
            String name = firstNonBlank(course.get("name"), course.get("courseName"), course.get("course_name"), course.get("课程名称"));
            Object credit = firstNonNull(course.get("credit"), course.get("credits"), course.get("courseCredit"), course.get("学分"));
            if (name.isBlank() && credit == null) {
                continue;
            }
            Map<String, Object> item = new HashMap<>();
            item.put("name", name);
            item.put("credit", credit == null ? 0 : credit);
            normalized.add(item);
        }
        return normalized;
    }

    private String firstNonBlank(Object... values) {
        for (Object value : values) {
            if (value != null) {
                String text = String.valueOf(value).trim();
                if (!text.isEmpty()) {
                    return text;
                }
            }
        }
        return "";
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}
