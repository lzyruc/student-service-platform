package com.college.student_service_platform.service;

import com.college.student_service_platform.dto.NotificationItem;
import com.college.student_service_platform.dto.NotificationReceiptItem;
import com.college.student_service_platform.dto.NotificationSaveRequest;
import com.college.student_service_platform.dto.StudentNotificationItem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class NotificationService {
    private static final DateTimeFormatter NOTICE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final JdbcTemplate jdbcTemplate;
    private final AtomicLong idSeq = new AtomicLong(System.currentTimeMillis());

    public NotificationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public Long save(NotificationSaveRequest request, Long publisherId) {
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
        if (publisherId == null) {
            throw new IllegalArgumentException("未找到当前管理员账号");
        }
        validateAttachment(fileId);

        Timestamp now = Timestamp.valueOf(LocalDateTime.now());

        Long id = request.getId();
        if (id != null) {
            int affected = jdbcTemplate.update(
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
            if (affected == 0) {
                throw new IllegalArgumentException("通知不存在或已被删除");
            }
            jdbcTemplate.update(
                    "UPDATE t_notification_receipt SET is_confirmed = FALSE, confirmed_at = NULL WHERE notification_id = ?",
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
                SELECT n.id, n.title, n.content, n.tags, n.is_urgent, n.file_id, n.publisher_id,
                       f.original_name, f.file_type,
                       n.created_at, n.updated_at,
                       (SELECT COUNT(1) FROM t_student s WHERE s.status = 1) AS total_count,
                       (SELECT COUNT(1)
                          FROM t_notification_receipt r
                          JOIN t_student s ON s.student_no = r.student_no AND s.status = 1
                         WHERE r.notification_id = n.id AND r.is_confirmed = TRUE) AS confirmed_count
                FROM t_notification n
                LEFT JOIN t_file f ON f.id = n.file_id
                """;
        List<Object> args = new ArrayList<>();
        if (has) {
            sql += """
                    WHERE LOWER(n.title) LIKE ?
                       OR LOWER(COALESCE(n.tags, '')) LIKE ?
                    """;
            String like = "%" + k + "%";
            args.add(like);
            args.add(like);
        }
        sql += " ORDER BY n.is_urgent DESC, n.created_at DESC";

        return jdbcTemplate.query(sql, args.toArray(), (rs, rowNum) -> mapNotification(rs, true));
    }

    public NotificationItem getById(Long id) {
        String sql = """
                SELECT n.id, n.title, n.content, n.tags, n.is_urgent, n.file_id, n.publisher_id,
                       n.created_at, n.updated_at, f.original_name, f.file_type
                FROM t_notification n
                LEFT JOIN t_file f ON f.id = n.file_id
                WHERE n.id = ?
                """;

        return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> mapNotification(rs, false), id);
    }

    @Transactional
    public void delete(Long id) {
        if (id == null) return;
        jdbcTemplate.update("DELETE FROM t_notification_receipt WHERE notification_id = ?", id);
        jdbcTemplate.update("DELETE FROM t_notification WHERE id = ?", id);
    }

    public List<NotificationReceiptItem> listReceipts(Long notificationId) {
        if (notificationId == null) throw new IllegalArgumentException("通知 ID 不能为空");
        Integer notificationExists = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM t_notification WHERE id = ?", Integer.class, notificationId);
        if (notificationExists == null || notificationExists == 0) {
            throw new IllegalArgumentException("通知不存在或已被删除");
        }
        String sql = """
                SELECT r.id, ? AS notification_id, s.student_no, s.name AS student_name,
                       COALESCE(r.is_confirmed, FALSE) AS is_confirmed,
                       r.confirmed_at, COALESCE(r.created_at, s.created_at) AS created_at
                FROM t_student s
                LEFT JOIN t_notification_receipt r
                  ON r.student_no = s.student_no AND r.notification_id = ?
                WHERE s.status = 1
                ORDER BY COALESCE(r.is_confirmed, FALSE), s.student_no
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            NotificationReceiptItem item = new NotificationReceiptItem();
            long id = rs.getLong("id");
            item.setId(rs.wasNull() ? null : id);
            item.setNotificationId(rs.getLong("notification_id"));
            item.setStudentNo(rs.getString("student_no"));
            item.setStudentName(rs.getString("student_name"));
            item.setIsConfirmed(rs.getBoolean("is_confirmed"));
            Timestamp confirmedAt = rs.getTimestamp("confirmed_at");
            if (confirmedAt != null) item.setConfirmedAt(confirmedAt.toLocalDateTime());
            Timestamp createdAt = rs.getTimestamp("created_at");
            if (createdAt != null) item.setCreatedAt(createdAt.toLocalDateTime());
            return item;
        }, notificationId, notificationId);
    }

    public List<StudentNotificationItem> listForStudent(String studentNo) {
        String no = normalize(studentNo);
        assertActiveStudent(no);
        String sql = """
                SELECT n.id, n.title, n.content, n.tags, n.is_urgent, n.created_at,
                       COALESCE(r.is_confirmed, FALSE) AS is_confirmed,
                       f.id AS file_id, f.original_name, f.file_type, f.file_size
                FROM t_notification n
                LEFT JOIN t_notification_receipt r
                  ON r.notification_id = n.id AND r.student_no = ?
                LEFT JOIN t_file f ON n.file_id = f.id
                ORDER BY n.is_urgent DESC, n.created_at DESC
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            StudentNotificationItem item = new StudentNotificationItem();
            item.setId(rs.getLong("id"));
            item.setTitle(rs.getString("title"));
            item.setContent(rs.getString("content"));
            item.setTags(rs.getString("tags"));
            item.setIsUrgent(rs.getBoolean("is_urgent"));
            Timestamp createdAt = rs.getTimestamp("created_at");
            item.setPublishTime(createdAt == null ? "" : NOTICE_DATE_FORMAT.format(createdAt.toLocalDateTime()));
            item.setIsConfirmed(rs.getBoolean("is_confirmed"));
            long fileId = rs.getLong("file_id");
            if (!rs.wasNull()) {
                item.setFileId(fileId);
                item.setDownloadUrl("/api/file/download/" + fileId);
            }
            item.setOriginalName(rs.getString("original_name"));
            item.setFileType(rs.getString("file_type"));
            long fileSize = rs.getLong("file_size");
            item.setFileSize(rs.wasNull() ? null : fileSize);
            return item;
        }, no);
    }

    @Transactional
    public void confirm(Long notificationId, String studentNo) {
        String no = normalize(studentNo);
        if (notificationId == null) throw new IllegalArgumentException("notificationId 不能为空");
        assertActiveStudent(no);
        Integer noticeExists = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM t_notification WHERE id = ?", Integer.class, notificationId);
        if (noticeExists == null || noticeExists == 0) {
            throw new IllegalArgumentException("通知不存在或已被删除");
        }
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        int updated = jdbcTemplate.update(
                """
                        UPDATE t_notification_receipt
                        SET is_confirmed = TRUE, confirmed_at = ?
                        WHERE notification_id = ? AND student_no = ? AND COALESCE(is_confirmed, FALSE) = FALSE
                        """,
                now,
                notificationId,
                no
        );
        if (updated == 0) {
            Integer exists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM t_notification_receipt WHERE notification_id = ? AND student_no = ?",
                    Integer.class,
                    notificationId,
                    no
            );
            if (exists != null && exists > 0) return;
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
                "SELECT student_no FROM t_student WHERE status = 1",
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

    private void validateAttachment(Long fileId) {
        if (fileId == null) return;
        Integer valid = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM t_file WHERE id = ? AND LOWER(COALESCE(business_type, '')) = 'notice'",
                Integer.class,
                fileId
        );
        if (valid == null || valid == 0) {
            throw new IllegalArgumentException("通知附件不存在或文件类型不是 notice");
        }
    }

    private void assertActiveStudent(String studentNo) {
        if (studentNo.isEmpty()) throw new IllegalArgumentException("学号不能为空");
        Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM t_student WHERE student_no = ? AND status = 1",
                Integer.class,
                studentNo
        );
        if (exists == null || exists == 0) {
            throw new IllegalArgumentException("学生不存在或账号已停用");
        }
    }

    private NotificationItem mapNotification(java.sql.ResultSet rs, boolean withCounts) throws java.sql.SQLException {
        NotificationItem item = new NotificationItem();
        item.setId(rs.getLong("id"));
        item.setTitle(rs.getString("title"));
        item.setContent(rs.getString("content"));
        item.setTags(rs.getString("tags"));
        item.setIsUrgent(rs.getBoolean("is_urgent"));
        long fileId = rs.getLong("file_id");
        item.setFileId(rs.wasNull() ? null : fileId);
        item.setOriginalName(rs.getString("original_name"));
        item.setFileType(rs.getString("file_type"));
        long publisherId = rs.getLong("publisher_id");
        item.setPublisherId(rs.wasNull() ? null : publisherId);
        Timestamp createdAt = rs.getTimestamp("created_at");
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        if (createdAt != null) item.setCreatedAt(createdAt.toLocalDateTime());
        if (updatedAt != null) item.setUpdatedAt(updatedAt.toLocalDateTime());
        if (withCounts) {
            item.setConfirmedCount(rs.getInt("confirmed_count"));
            item.setTotalCount(rs.getInt("total_count"));
        }
        return item;
    }
}
