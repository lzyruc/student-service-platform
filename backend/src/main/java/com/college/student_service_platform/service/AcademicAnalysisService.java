package com.college.student_service_platform.service;

import com.college.student_service_platform.common.ApiException;
import com.college.student_service_platform.common.AuthContext;
import com.college.student_service_platform.dto.AcademicAnalysisSnapshot;
import com.college.student_service_platform.dto.AcademicContextSnapshot;
import com.college.student_service_platform.dto.StudentTranscriptResponse;
import com.college.student_service_platform.dto.TrainingPlanItem;
import com.college.student_service_platform.entity.FileRecord;
import com.college.student_service_platform.service.external.AcademicWarningClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AcademicAnalysisService {
    private final AcademicWarningClient client;
    private final WarningRecordService records;
    private final TrainingPlanService plans;
    private final StudentTranscriptService transcripts;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public AcademicAnalysisService(AcademicWarningClient client, WarningRecordService records,
                                   TrainingPlanService plans, StudentTranscriptService transcripts,
                                   JdbcTemplate jdbc, ObjectMapper mapper) {
        this.client = client;
        this.records = records;
        this.plans = plans;
        this.transcripts = transcripts;
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    /** Explicit user action: analyze an upload, save its file, and create a formal warning record. */
    public Object analyzeUploadedAndSave(String authenticatedStudentNo, MultipartFile file) throws IOException {
        if (file == null) throw new IllegalArgumentException("请选择成绩单 PDF");
        return analyzeAndSave(authenticatedStudentNo, file);
    }

    /** Explicit user action: reuse the saved file and create a formal warning record. */
    public Object analyzeSavedAndSave(String authenticatedStudentNo) throws IOException {
        return analyzeAndSave(authenticatedStudentNo, null);
    }

    /**
     * Create a fresh context for one Agent HTTP request, bound to the verified student.
     * The caller shares it across that request's tools; it is not cached by conversation or user.
     */
    public AcademicAnalysisReadContext createReadContext(HttpServletRequest request) {
        String studentNo = AuthContext.subject(request);
        if (!"student".equals(AuthContext.role(request))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "学业 Agent 仅支持学生查询本人数据");
        }
        return new AcademicAnalysisReadContext(() -> analyzeReadOnly(studentNo), () -> readContext(studentNo));
    }

    private AcademicContextSnapshot readContext(String studentNo) {
        List<Map<String, Object>> students = jdbc.queryForList(
                "SELECT major, grade FROM t_student WHERE student_no = ?", studentNo);
        String major = students.isEmpty() ? null : profileValue(students.get(0).get("major"));
        String grade = students.isEmpty() ? null : profileValue(students.get(0).get("grade"));
        boolean profileAvailable = major != null && grade != null;
        StudentTranscriptResponse transcript = transcripts.getCurrent(studentNo);
        TrainingPlanItem plan = profileAvailable ? plans.getLatest(major, grade) : null;
        return new AcademicContextSnapshot(profileAvailable, major, grade, transcript != null,
                transcript != null && transcript.available(), transcript == null ? null : transcript.uploadedAt(),
                plan != null, plan == null ? null : plan.getVersion(), plan == null ? null : plan.getUpdatedAt());
    }

    private String profileValue(Object value) {
        return value == null || String.valueOf(value).isBlank() ? null : String.valueOf(value).trim();
    }

    private Object analyzeAndSave(String studentNo, MultipartFile file) throws IOException {
        FileRecord saved = file == null ? transcripts.requireCurrent(studentNo) : null;
        if (file != null) transcripts.validateUpload(file);
        TrainingPlanItem plan = requireMatchingPlan(studentNo);
        String normalizedPlan = convertTrainingPlanForPython(plan.getJsonContent(), plan.getMajor(), plan.getGrade());
        Long userId = transcripts.requireUserId(studentNo);
        Object response;
        Long transcriptId;
        if (file != null) {
            response = client.analyzeTranscript(file, studentNo, normalizedPlan);
            transcriptId = transcripts.save(studentNo, file).getId();
        } else {
            response = client.analyzeStoredTranscript(
                    transcripts.resolveForAnalysis(studentNo, saved), saved.getOriginalName(), studentNo, normalizedPlan);
            transcriptId = saved.getId();
        }
        records.saveFromPythonResponse(userId, studentNo, transcriptId, plan.getId(), response);
        return response;
    }

    /** No business writes: Python uses a temporary PDF and deletes it in its existing endpoint. */
    private AcademicAnalysisSnapshot analyzeReadOnly(String studentNo) {
        FileRecord saved = transcripts.requireCurrent(studentNo);
        TrainingPlanItem plan = requireMatchingPlan(studentNo);
        try {
            String normalizedPlan = convertTrainingPlanForPython(plan.getJsonContent(), plan.getMajor(), plan.getGrade());
            Object response = client.analyzeStoredTranscript(
                    transcripts.resolveForAnalysis(studentNo, saved), saved.getOriginalName(), studentNo, normalizedPlan);
            return new AcademicAnalysisSnapshot(studentNo, saved.getId(), saved.getOriginalName(), saved.getCreatedAt(),
                    plan.getId(), plan.getMajor(), plan.getGrade(), plan.getVersion(), plan.getUpdatedAt(),
                    mapper.valueToTree(response));
        } catch (IOException e) {
            throw new UncheckedIOException("培养方案内容无法读取", e);
        }
    }

    private TrainingPlanItem requireMatchingPlan(String studentNo) {
        List<Map<String, Object>> students = jdbc.queryForList(
                "SELECT major, grade FROM t_student WHERE student_no = ?", studentNo);
        if (students.isEmpty()) {
            throw new MissingAcademicDataException("未找到该学生的专业和年级信息");
        }
        String major = String.valueOf(students.get(0).get("major"));
        String grade = String.valueOf(students.get(0).get("grade"));
        TrainingPlanItem plan = plans.getLatest(major, grade);
        if (plan == null) {
            throw new MissingAcademicDataException("数据库中未找到匹配的培养方案：" + major + " / " + grade);
        }
        return plan;
    }

    /** Legacy HTTP endpoints retain their existing 404 result body for absent profile/plan data. */
    public static final class MissingAcademicDataException extends ApiException {
        private MissingAcademicDataException(String message) {
            super(HttpStatus.NOT_FOUND, message);
        }
    }

    @SuppressWarnings("unchecked")
    private String convertTrainingPlanForPython(String rawJson, String major, String grade) throws IOException {
        Object root = mapper.readValue(rawJson, Object.class);
        Map<String, Object> plan;
        if (root instanceof List<?> list) {
            if (list.isEmpty() || !(list.get(0) instanceof Map<?, ?> map)) {
                throw new IllegalArgumentException("培养方案内容为空或格式错误");
            }
            plan = (Map<String, Object>) list.get(0);
        } else if (root instanceof Map<?, ?> map) {
            plan = (Map<String, Object>) map;
        } else {
            throw new IllegalArgumentException("培养方案 JSON 格式错误");
        }
        Object coursesObj = plan.get("courses");
        if (!(coursesObj instanceof List<?> courseList) || courseList.isEmpty()) {
            throw new IllegalArgumentException("培养方案中没有课程数据");
        }
        List<String> coreCourses = new ArrayList<>();
        List<Map<String, Object>> coreCourseDetails = new ArrayList<>();
        double requiredCredits = 0.0;
        for (Object item : courseList) {
            if (!(item instanceof Map<?, ?> course)) continue;
            Object credits = course.get("credits");
            if (credits != null) requiredCredits += Double.parseDouble(String.valueOf(credits));
            String category = TrainingPlanCourseRules.normalizeCategory(course.get("category"));
            Object courseNameObj = course.get("courseName");
            String courseName = courseNameObj == null ? "" : String.valueOf(courseNameObj);
            if (TrainingPlanCourseRules.isCoreCourse(category) && !courseName.isBlank()) {
                coreCourses.add(courseName);
                Map<String, Object> detail = new LinkedHashMap<>();
                detail.put("name", courseName);
                detail.put("category", category);
                detail.put("credit", credits);
                detail.put("offered_at", course.get("offeredAt"));
                coreCourseDetails.add(detail);
            }
        }
        if (coreCourses.isEmpty()) {
            throw new IllegalArgumentException("培养方案中未识别到核心课程");
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("required_credits", requiredCredits);
        normalized.put("core_courses", coreCourses);
        normalized.put("core_course_details", coreCourseDetails);
        normalized.put("major", major);
        normalized.put("grade", grade);
        return mapper.writeValueAsString(normalized);
    }
}
