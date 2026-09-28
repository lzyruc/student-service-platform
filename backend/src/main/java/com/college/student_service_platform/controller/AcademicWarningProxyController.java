package com.college.student_service_platform.controller;

import com.college.student_service_platform.common.Result;
import com.college.student_service_platform.common.AuthContext;
import com.college.student_service_platform.dto.FileUploadResponse;
import com.college.student_service_platform.dto.TrainingPlanItem;
import jakarta.servlet.http.HttpServletRequest;
import com.college.student_service_platform.service.FileService;
import com.college.student_service_platform.service.TrainingPlanService;
import com.college.student_service_platform.service.WarningRecordService;
import com.college.student_service_platform.service.external.AcademicWarningClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AcademicWarningProxyController {
    private static final Logger log = LoggerFactory.getLogger(AcademicWarningProxyController.class);

    private final AcademicWarningClient academicWarningClient;
    private final WarningRecordService warningRecordService;
    private final TrainingPlanService trainingPlanService;
    private final FileService fileService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AcademicWarningProxyController(
            AcademicWarningClient academicWarningClient,
            WarningRecordService warningRecordService,
            TrainingPlanService trainingPlanService,
            FileService fileService,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper
    ) {
        this.academicWarningClient = academicWarningClient;
        this.warningRecordService = warningRecordService;
        this.trainingPlanService = trainingPlanService;
        this.fileService = fileService;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/student/warning/analyze")
    public Result<Object> analyzeTranscript(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "studentNo", required = false) String requestedStudentNo,
            HttpServletRequest request
    ) throws IOException {
        String studentNo = AuthContext.studentNo(request, requestedStudentNo);
        try {
            String stuSql = "SELECT major, grade FROM t_student WHERE student_no = ?";
            java.util.List<Map<String, Object>> stus = jdbcTemplate.queryForList(stuSql, studentNo);
            if (stus.isEmpty()) {
                return Result.fail(404, "未找到该学生的专业和年级信息");
            }

            String major = String.valueOf(stus.get(0).get("major"));
            String grade = String.valueOf(stus.get(0).get("grade"));
            TrainingPlanItem plan = trainingPlanService.getLatest(major, grade);
            if (plan == null) {
                return Result.fail(404, "数据库中未找到匹配的培养方案：" + major + " / " + grade);
            }

            String trainingPlanJson = convertTrainingPlanForPython(plan.getJsonContent());
            Long userId = findUserId(studentNo);
            Object response = academicWarningClient.analyzeTranscript(file, studentNo, trainingPlanJson);
            FileUploadResponse transcript = fileService.uploadFile(file, "transcript", userId);
            warningRecordService.saveFromPythonResponse(userId, studentNo, transcript.getId(), plan.getId(), response);
            return Result.success("学业预警分析成功", response);
        } catch (IllegalArgumentException e) {
            return Result.fail(400, e.getMessage());
        } catch (Exception e) {
            log.error("Academic warning analysis failed for student {}", studentNo, e);
            return Result.fail("学业预警分析失败，请稍后重试");
        }
    }

    private Long findUserId(String studentNo) {
        java.util.List<Long> ids = jdbcTemplate.queryForList(
                "SELECT id FROM t_user WHERE student_no = ? LIMIT 1",
                Long.class,
                studentNo
        );
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("未找到该学生的用户账号");
        }
        return ids.get(0);
    }

    @SuppressWarnings("unchecked")
    private String convertTrainingPlanForPython(String rawJson) throws IOException {
        Object root = objectMapper.readValue(rawJson, Object.class);
        Map<String, Object> plan;
        if (root instanceof java.util.List<?> list) {
            if (list.isEmpty() || !(list.get(0) instanceof Map<?, ?> map)) {
                throw new IllegalArgumentException("培养方案内容为空或格式错误");
            }
            plan = (Map<String, Object>) map;
        } else if (root instanceof Map<?, ?> map) {
            plan = (Map<String, Object>) map;
        } else {
            throw new IllegalArgumentException("培养方案 JSON 格式错误");
        }

        Object coursesObj = plan.get("courses");
        if (!(coursesObj instanceof java.util.List<?> courseList) || courseList.isEmpty()) {
            throw new IllegalArgumentException("培养方案中没有课程数据");
        }

        java.util.List<String> coreCourses = new java.util.ArrayList<>();
        double requiredCredits = 0.0;
        for (Object item : courseList) {
            if (!(item instanceof Map<?, ?> course)) continue;
            Object credits = course.get("credits");
            if (credits != null) requiredCredits += Double.parseDouble(String.valueOf(credits));
            Object categoryObj = course.get("category");
            Object courseNameObj = course.get("courseName");
            String category = categoryObj == null ? "" : String.valueOf(categoryObj);
            String courseName = courseNameObj == null ? "" : String.valueOf(courseNameObj);
            if (category.contains("核心") && !courseName.isBlank()) coreCourses.add(courseName);
        }
        if (coreCourses.isEmpty()) {
            throw new IllegalArgumentException("培养方案中未识别到核心课程");
        }

        Map<String, Object> normalized = new java.util.LinkedHashMap<>();
        normalized.put("required_credits", requiredCredits);
        normalized.put("core_courses", coreCourses);
        return objectMapper.writeValueAsString(normalized);
    }
}
