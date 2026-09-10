package com.college.student_service_platform.service;

import com.college.student_service_platform.dto.TrainingPlanItem;
import com.college.student_service_platform.dto.TrainingPlanSaveRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class TrainingPlanService {

    private final JdbcTemplate jdbcTemplate;
    private final AtomicLong idSeq = new AtomicLong(System.currentTimeMillis());

    public TrainingPlanService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
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

        String remark = normalize(request.getRemark());
        Integer courseCount = request.getCourseCount();
        BigDecimal totalCredits = request.getTotalCredits();
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());

        Long id = request.getId();
        if (id != null) {
            jdbcTemplate.update(
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
                ORDER BY created_at DESC
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            TrainingPlanItem item = new TrainingPlanItem();
            item.setId(rs.getLong("id"));
            item.setMajor(rs.getString("major"));
            item.setGrade(rs.getString("grade"));
            item.setVersion(rs.getString("version"));
            item.setRemark(rs.getString("remark"));
            item.setJsonContent(rs.getString("json_content"));
            int count = rs.getInt("course_count");
            item.setCourseCount(rs.wasNull() ? null : count);
            item.setTotalCredits(rs.getBigDecimal("total_credits"));
            Timestamp createdAt = rs.getTimestamp("created_at");
            Timestamp updatedAt = rs.getTimestamp("updated_at");
            if (createdAt != null) item.setCreatedAt(createdAt.toLocalDateTime());
            if (updatedAt != null) item.setUpdatedAt(updatedAt.toLocalDateTime());
            return item;
        });
    }

    public TrainingPlanItem getById(Long id) {
        String sql = """
                SELECT id, major, grade, version, remark, json_content, course_count, total_credits, created_at, updated_at
                FROM t_training_plan
                WHERE id = ?
                """;

        return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> {
            TrainingPlanItem item = new TrainingPlanItem();
            item.setId(rs.getLong("id"));
            item.setMajor(rs.getString("major"));
            item.setGrade(rs.getString("grade"));
            item.setVersion(rs.getString("version"));
            item.setRemark(rs.getString("remark"));
            item.setJsonContent(rs.getString("json_content"));
            int count = rs.getInt("course_count");
            item.setCourseCount(rs.wasNull() ? null : count);
            item.setTotalCredits(rs.getBigDecimal("total_credits"));
            Timestamp createdAt = rs.getTimestamp("created_at");
            Timestamp updatedAt = rs.getTimestamp("updated_at");
            if (createdAt != null) item.setCreatedAt(createdAt.toLocalDateTime());
            if (updatedAt != null) item.setUpdatedAt(updatedAt.toLocalDateTime());
            return item;
        }, id);
    }

    @Transactional
    public void delete(Long id) {
        if (id == null) return;
        jdbcTemplate.update("DELETE FROM t_training_plan WHERE id = ?", id);
    }

    private String normalize(String v) {
        return v == null ? "" : v.trim();
    }
}
