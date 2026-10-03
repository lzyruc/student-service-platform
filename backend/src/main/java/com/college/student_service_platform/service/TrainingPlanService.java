package com.college.student_service_platform.service;

import com.college.student_service_platform.dto.TrainingPlanItem;
import com.college.student_service_platform.dto.TrainingPlanSaveRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class TrainingPlanService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final AtomicLong idSeq = new AtomicLong(System.currentTimeMillis());

    public TrainingPlanService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Long save(TrainingPlanSaveRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求不能为空");
        }
        String major = normalize(request.getMajor());
        String grade = normalize(request.getGrade());
        String version = normalize(request.getVersion());
        String jsonContent = request.getJsonContent() == null ? "" : request.getJsonContent().trim();

        if (major.isEmpty() || grade.isEmpty() || version.isEmpty()) {
            throw new IllegalArgumentException("major/grade/version 不能为空");
        }
        if (jsonContent.isEmpty()) {
            throw new IllegalArgumentException("jsonContent 不能为空");
        }

        try {
            jsonContent = TrainingPlanCourseRules.normalizeJson(objectMapper, jsonContent);
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("培养方案 JSON 格式不正确");
        }
        PlanSummary summary = parseAndValidatePlan(jsonContent);
        String remark = normalize(request.getRemark());
        Integer courseCount = summary.courseCount();
        BigDecimal totalCredits = summary.totalCredits();
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());

        Long id = request.getId();
        Integer duplicateCount = id == null
                ? jdbcTemplate.queryForObject(
                        "SELECT COUNT(1) FROM t_training_plan WHERE major = ? AND grade = ? AND version = ?",
                        Integer.class,
                        major,
                        grade,
                        version
                )
                : jdbcTemplate.queryForObject(
                        "SELECT COUNT(1) FROM t_training_plan WHERE major = ? AND grade = ? AND version = ? AND id <> ?",
                        Integer.class,
                        major,
                        grade,
                        version,
                        id
                );
        if (duplicateCount != null && duplicateCount > 0) {
            throw new IllegalArgumentException("同一专业、年级和版本的培养方案已存在");
        }

        if (id != null) {
            int affected = jdbcTemplate.update(
                    """
                            UPDATE t_training_plan
                            SET major = ?, grade = ?, version = ?, remark = ?, json_content = ?, course_count = ?, total_credits = ?, updated_at = ?
                            WHERE id = ?
                            """,
                    major,
                    grade,
                    version,
                    remark.isEmpty() ? null : remark,
                    jsonContent,
                    courseCount,
                    totalCredits,
                    now,
                    id
            );
            if (affected == 0) {
                throw new IllegalArgumentException("培养方案不存在或已被删除");
            }
            return id;
        }

        Long newId = idSeq.incrementAndGet();
        jdbcTemplate.update(
                """
                        INSERT INTO t_training_plan
                        (id, major, grade, version, remark, json_content, course_count, total_credits, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                newId,
                major,
                grade,
                version,
                remark.isEmpty() ? null : remark,
                jsonContent,
                courseCount,
                totalCredits,
                now,
                now
        );
        return newId;
    }

    public List<TrainingPlanItem> list() {
        String sql = """
                SELECT id, major, grade, version, remark, json_content, course_count, total_credits, created_at, updated_at
                FROM t_training_plan
                ORDER BY updated_at DESC, created_at DESC, id DESC
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> mapItem(rs));
    }

    public TrainingPlanItem getById(Long id) {
        String sql = """
                SELECT id, major, grade, version, remark, json_content, course_count, total_credits, created_at, updated_at
                FROM t_training_plan
                WHERE id = ?
                """;

        return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> mapItem(rs), id);
    }

    public TrainingPlanItem getLatest(String major, String grade) {
        String normalizedMajor = normalize(major);
        String normalizedGrade = normalize(grade);
        if (normalizedMajor.isEmpty() || normalizedGrade.isEmpty()) {
            throw new IllegalArgumentException("专业和年级不能为空");
        }
        try {
            return jdbcTemplate.queryForObject(
                    """
                            SELECT id, major, grade, version, remark, json_content, course_count, total_credits, created_at, updated_at
                            FROM t_training_plan
                            WHERE major = ? AND grade = ?
                            ORDER BY updated_at DESC, created_at DESC, id DESC
                            LIMIT 1
                            """,
                    (rs, rowNum) -> mapItem(rs),
                    normalizedMajor,
                    normalizedGrade
            );
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    @Transactional
    public void delete(Long id) {
        if (id == null) return;
        jdbcTemplate.update("DELETE FROM t_training_plan WHERE id = ?", id);
    }

    private String normalize(String v) {
        return v == null ? "" : v.trim();
    }

    private TrainingPlanItem mapItem(java.sql.ResultSet rs) throws java.sql.SQLException {
        TrainingPlanItem item = new TrainingPlanItem();
        item.setId(rs.getLong("id"));
        item.setMajor(rs.getString("major"));
        item.setGrade(rs.getString("grade"));
        item.setVersion(rs.getString("version"));
        item.setRemark(rs.getString("remark"));
        try {
            item.setJsonContent(TrainingPlanCourseRules.normalizeJson(objectMapper, rs.getString("json_content")));
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("培养方案 JSON 格式不正确");
        }
        int count = rs.getInt("course_count");
        item.setCourseCount(rs.wasNull() ? null : count);
        item.setTotalCredits(rs.getBigDecimal("total_credits"));
        Timestamp createdAt = rs.getTimestamp("created_at");
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        if (createdAt != null) item.setCreatedAt(createdAt.toLocalDateTime());
        if (updatedAt != null) item.setUpdatedAt(updatedAt.toLocalDateTime());
        return item;
    }

    private PlanSummary parseAndValidatePlan(String jsonContent) {
        try {
            Map<String, Object> plan = objectMapper.readValue(jsonContent, new TypeReference<>() {});
            Object coursesValue = plan.get("courses");
            if (!(coursesValue instanceof List<?> courses) || courses.isEmpty()) {
                throw new IllegalArgumentException("培养方案至少需要一门课程");
            }
            int count = 0;
            BigDecimal credits = BigDecimal.ZERO;
            boolean hasCoreCourse = false;
            for (Object value : courses) {
                if (!(value instanceof Map<?, ?> course)) continue;
                String courseName = normalizeObject(course.get("courseName"));
                if (courseName.isEmpty()) continue;
                count++;
                String category = normalizeObject(course.get("category"));
                if (TrainingPlanCourseRules.isCoreCourse(category)) hasCoreCourse = true;
                Object creditValue = course.get("credits");
                if (creditValue != null) {
                    BigDecimal credit = new BigDecimal(String.valueOf(creditValue));
                    if (credit.signum() < 0) throw new IllegalArgumentException("课程学分不能为负数");
                    credits = credits.add(credit);
                }
            }
            if (count == 0) throw new IllegalArgumentException("培养方案至少需要一门有效课程");
            if (!hasCoreCourse) throw new IllegalArgumentException("培养方案至少需要一门核心课程（含部类基础课、部类共同课和思想政治理论课）");
            return new PlanSummary(count, credits);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("培养方案 JSON 格式不正确");
        }
    }

    private String normalizeObject(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private record PlanSummary(int courseCount, BigDecimal totalCredits) {}
}
