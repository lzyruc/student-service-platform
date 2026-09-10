package com.college.student_service_platform.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class WarningRecordService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public WarningRecordService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public Long saveFromPythonResponse(Long userId, String studentNo, Long transcriptFileId, Object pythonResponse) {
        Map<String, Object> root = asMap(pythonResponse);
        Map<String, Object> pythonResponseMap = asMap(root.get("pythonResponse"));
        Map<String, Object> data = firstMap(
                pythonResponseMap.get("data"),
                root.get("data"),
                root
        );
        Map<String, Object> nestedData = asMap(data.get("data"));
        Map<String, Object> report = firstMap(
                nestedData.get("report"),
                data.get("report"),
                root.get("report")
        );

        if (report.isEmpty() && hasAnyKey(data, "warning_level", "riskLevel", "missingCredits", "suggestions")) {
            report = data;
        }

        List<Object> courses = firstList(
                nestedData.get("courses"),
                data.get("courses"),
                report.get("courses"),
                root.get("courses")
        );

        String warningLevel = firstString(
                report.get("warning_level"),
                report.get("riskLevel"),
                report.get("warningLevel")
        );
        BigDecimal totalEarnedCredits = firstBigDecimal(
                report.get("total_earned_credits"),
                report.get("totalCredits"),
                report.get("earnedCredits")
        );

        List<Object> failedCourses = firstList(
                report.get("failed_courses"),
                report.get("failedCourses")
        );
        List<Object> missingCourses = firstList(
                report.get("missing_core_courses"),
                report.get("missingCourses"),
                report.get("missingCoreCourses")
        );
        List<Object> suggestions = firstList(
                report.get("course_suggestions"),
                report.get("suggestions"),
                data.get("suggestions")
        );

        int courseCount = courses == null ? 0 : courses.size();
        int failedCourseCount = failedCourses == null ? 0 : failedCourses.size();
        int missingCourseCount = missingCourses == null ? 0 : missingCourses.size();

        Long id = System.currentTimeMillis();

        String sql = """
                INSERT INTO t_warning_record
                (id, user_id, student_no, transcript_file_id, training_plan_id, warning_level,
                 total_earned_credits, course_count, core_course_count, failed_course_count, missing_course_count,
                 parsed_courses_json, core_courses_json, failed_courses_json, missing_courses_json,
                 suggestions_json, analysis_status, error_message, created_at, updated_at)
                VALUES
                (?, ?, ?, ?, ?, ?,
                 ?, ?, ?, ?, ?,
                 ?, ?, ?, ?,
                 ?, ?, ?, ?, ?)
                """;

        jdbcTemplate.update(
                sql,
                id,
                userId,
                studentNo,
                transcriptFileId,
                null,
                warningLevel == null || warningLevel.isBlank() ? "未知" : warningLevel,
                totalEarnedCredits,
                courseCount,
                0, // core_course_count
                failedCourseCount,
                missingCourseCount,
                toJson(courses == null ? List.of() : courses),
                null, // core_courses_json
                toJson(failedCourses),
                toJson(missingCourses),
                toJson(suggestions),
                1, // analysis_status
                null, // error_message
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        return id;
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private boolean hasAnyKey(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            if (map.containsKey(key)) {
                return true;
            }
        }
        return false;
    }

    private Map<String, Object> firstMap(Object... values) {
        for (Object value : values) {
            Map<String, Object> map = asMap(value);
            if (!map.isEmpty()) {
                return map;
            }
        }
        return Map.of();
    }

    private List<Object> firstList(Object... values) {
        for (Object value : values) {
            List<Object> list = asList(value);
            if (list != null) {
                return list;
            }
        }
        return null;
    }

    private String firstString(Object... values) {
        for (Object value : values) {
            String text = asString(value);
            if (text != null && !text.isBlank()) {
                return text;
            }
        }
        return null;
    }

    private BigDecimal firstBigDecimal(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return asBigDecimal(value);
            }
        }
        return BigDecimal.ZERO;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value == null) {
            return Map.of();
        }
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private List<Object> asList(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof List<?> list) {
            return (List<Object>) list;
        }
        return null;
    }

    private String asString(Object value) {
        if (value == null) {
            return null;
        }
        return String.valueOf(value);
    }

    private BigDecimal asBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (Exception ignored) {
            return BigDecimal.ZERO;
        }
    }
}

