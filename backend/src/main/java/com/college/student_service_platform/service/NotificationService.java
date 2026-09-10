package com.college.student_service_platform.service;

import com.college.student_service_platform.dto.NotificationItem;
import com.college.student_service_platform.dto.NotificationReceiptItem;
import com.college.student_service_platform.dto.NotificationSaveRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class NotificationService {

    private final JdbcTemplate jdbcTemplate;
    private final AtomicLong idSeq = new AtomicLong(System.currentTimeMillis());

    public NotificationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public Long save(NotificationSaveRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求不能为空");
        }
        String title = normalize(request.getTitle());
        if (title.isEmpty()) {
            throw new IllegalArgumentException("标题不能为空");
        }
        String content = request.getContent() == null ? null : request.getContent().trim();
        String tags = normalize(request.getTags());
        Boolean isUrgent = request.getIsUrgent() != null && request.getIsUrgent();
        Long fileId = request.getFileId();
        Long publisherId = request.getPublisherId();

        Timestamp now = Timestamp.valueOf(LocalDateTime.now());

        Long id = request.getId();
        if (id != null) {
            jdbcTemplate.update(
                    """
                            UPDATE t_notification
                            SET title = ?, content = ?, tags = ?, is_urgent = ?, file_id = ?, publisher_id = ?, updated_at = ?
                            WHERE id = ?
                            """,
                    title,
                    content,
                    tags.isEmpty() ? null : tags,
                    isUrgent,
                    fileId,
                    publisherId,
                    now,
                    id
            );
            ensureReceipts(id);
            return id;
        }

        Long newId = idSeq.incrementAndGet();
        jdbcTemplate.update(
                """
                        INSERT INTO t_notification
                        (id, title, content, tags, is_urgent, file_id, publisher_id, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                newId,
                title,
                content,
                tags.isEmpty() ? null : tags,
                isUrgent,
                fileId,
                publisherId,
                now,
                now
        );
        ensureReceipts(newId);
        return newId;
    }

    public List<NotificationItem> list(String keyword) {
        String k = normalize(keyword).toLowerCase();
        boolean has = !k.isEmpty();
        String sql = """
                SELECT id, title, content, tags, is_urgent, file_id, publisher_id, created_at, updated_at
                FROM t_notification
                """;
        List<Object> args = new ArrayList<>();
        if (has) {
            sql += """
                    WHERE LOWER(title) LIKE ?
                       OR LOWER(COALESCE(tags, '')) LIKE ?
                    """;
            String like = "%" + k + "%";
            args.add(like);
            args.add(like);
        }
        sql += " ORDER BY created_at DESC";

        return jdbcTemplate.query(sql, args.toArray(), (rs, rowNum) -> {
            NotificationItem item = new NotificationItem();
            item.setId(rs.getLong("id"));
            item.setTitle(rs.getString("title"));
            item.setContent(rs.getString("content"));
            item.setTags(rs.getString("tags"));
            item.setIsUrgent(rs.getBoolean("is_urgent"));
            long fileId = rs.getLong("file_id");
            item.setFileId(rs.wasNull() ? null : fileId);
            long publisherId = rs.getLong("publisher_id");
            item.setPublisherId(rs.wasNull() ? null : publisherId);
            Timestamp createdAt = rs.getTimestamp("created_at");
            Timestamp updatedAt = rs.getTimestamp("updated_at");
            if (createdAt != null) item.setCreatedAt(createdAt.toLocalDateTime());
            if (updatedAt != null) item.setUpdatedAt(updatedAt.toLocalDateTime());
            return item;
        });
    }

    public NotificationItem getById(Long id) {
        String sql = """
                SELECT id, title, content, tags, is_urgent, file_id, publisher_id, created_at, updated_at
                FROM t_notification
                WHERE id = ?
                """;

        return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> {
            NotificationItem item = new NotificationItem();
            item.setId(rs.getLong("id"));
            item.setTitle(rs.getString("title"));
            item.setContent(rs.getString("content"));
            item.setTags(rs.getString("tags"));
            item.setIsUrgent(rs.getBoolean("is_urgent"));
            long fileId = rs.getLong("file_id");
            item.setFileId(rs.wasNull() ? null : fileId);
            long publisherId = rs.getLong("publisher_id");
            item.setPublisherId(rs.wasNull() ? null : publisherId);
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
        jdbcTemplate.update("DELETE FROM t_notification_receipt WHERE notification_id = ?", id);
        jdbcTemplate.update("DELETE FROM t_notification WHERE id = ?", id);
    }

    public List<NotificationReceiptItem> listReceipts(Long notificationId) {
        String sql = """
                SELECT id, notification_id, student_no, is_confirmed, confirmed_at, created_at
                FROM t_notification_receipt
                WHERE notification_id = ?
                ORDER BY created_at DESC
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            NotificationReceiptItem item = new NotificationReceiptItem();
            item.setId(rs.getLong("id"));
            item.setNotificationId(rs.getLong("notification_id"));
            item.setStudentNo(rs.getString("student_no"));
            item.setIsConfirmed(rs.getBoolean("is_confirmed"));
            Timestamp confirmedAt = rs.getTimestamp("confirmed_at");
            if (confirmedAt != null) item.setConfirmedAt(confirmedAt.toLocalDateTime());
            Timestamp createdAt = rs.getTimestamp("created_at");
            if (createdAt != null) item.setCreatedAt(createdAt.toLocalDateTime());
            return item;
        }, notificationId);
    }

    @Transactional
    public void confirm(Long notificationId, String studentNo) {
        String no = normalize(studentNo);
        if (notificationId == null || no.isEmpty()) return;
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        int updated = jdbcTemplate.update(
                """
                        UPDATE t_notification_receipt
                        SET is_confirmed = TRUE, confirmed_at = ?
                        WHERE notification_id = ? AND student_no = ?
                        """,
                now,
                notificationId,
                no
        );
        if (updated == 0) {
            Long id = idSeq.incrementAndGet();
            jdbcTemplate.update(
                    """
                            INSERT INTO t_notification_receipt
                            (id, notification_id, student_no, is_confirmed, confirmed_at, created_at)
                            VALUES (?, ?, ?, TRUE, ?, ?)
                            """,
                    id,
                    notificationId,
                    no,
                    now,
                    now
            );
        }
    }

    private void ensureReceipts(Long notificationId) {
        List<String> studentNos = jdbcTemplate.query(
                "SELECT student_no FROM t_student",
                (rs, rowNum) -> rs.getString("student_no")
        );
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        for (String studentNo : studentNos) {
            if (studentNo == null || studentNo.isBlank()) continue;
            Integer exists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM t_notification_receipt WHERE notification_id = ? AND student_no = ?",
                    Integer.class,
                    notificationId,
                    studentNo
            );
            if (exists != null && exists > 0) continue;
            Long id = idSeq.incrementAndGet();
            jdbcTemplate.update(
                    """
                            INSERT INTO t_notification_receipt
                            (id, notification_id, student_no, is_confirmed, created_at)
                            VALUES (?, ?, ?, FALSE, ?)
                            """,
                    id,
                    notificationId,
                    studentNo,
                    now
            );
        }
    }

    private String normalize(String v) {
        return v == null ? "" : v.trim();
    }
}
