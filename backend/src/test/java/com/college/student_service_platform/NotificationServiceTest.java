package com.college.student_service_platform;

import com.college.student_service_platform.dto.NotificationItem;
import com.college.student_service_platform.dto.NotificationReceiptItem;
import com.college.student_service_platform.dto.NotificationSaveRequest;
import com.college.student_service_platform.dto.StudentNotificationItem;
import com.college.student_service_platform.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationServiceTest {
    private JdbcTemplate jdbcTemplate;
    private NotificationService service;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:notification-service;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("DROP ALL OBJECTS");
        jdbcTemplate.execute("""
                CREATE TABLE t_student (
                    id BIGINT PRIMARY KEY, student_no VARCHAR(50) UNIQUE, name VARCHAR(100),
                    status INT, created_at TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE t_file (
                    id BIGINT PRIMARY KEY, original_name VARCHAR(255), stored_name VARCHAR(255),
                    file_path VARCHAR(500), file_type VARCHAR(100), file_size BIGINT,
                    uploader_id BIGINT, business_type VARCHAR(100), created_at TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE t_notification (
                    id BIGINT PRIMARY KEY, title VARCHAR(200), content TEXT, tags VARCHAR(255),
                    is_urgent BOOLEAN, file_id BIGINT, publisher_id BIGINT,
                    created_at TIMESTAMP, updated_at TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE t_notification_receipt (
                    id BIGINT PRIMARY KEY, notification_id BIGINT NOT NULL, student_no VARCHAR(50) NOT NULL,
                    is_confirmed BOOLEAN, confirmed_at TIMESTAMP, created_at TIMESTAMP,
                    UNIQUE(notification_id, student_no)
                )
                """);
        jdbcTemplate.update("INSERT INTO t_student VALUES (1, '20260001', '张三', 1, CURRENT_TIMESTAMP)");
        jdbcTemplate.update("INSERT INTO t_student VALUES (2, '20260002', '李四', 1, CURRENT_TIMESTAMP)");
        jdbcTemplate.update("INSERT INTO t_student VALUES (3, '20260003', '停用学生', 0, CURRENT_TIMESTAMP)");
        jdbcTemplate.update("""
                INSERT INTO t_file
                VALUES (100, '选课通知.pdf', 'stored.pdf', 'uploads/stored.pdf', 'application/pdf', 1024, 9, 'notice', CURRENT_TIMESTAMP)
                """);
        service = new NotificationService(jdbcTemplate);
    }

    @Test
    void completesPublishViewConfirmAndReconfirmFlow() {
        NotificationSaveRequest request = request();
        Long notificationId = service.save(request, 9L);

        assertEquals(9L, jdbcTemplate.queryForObject(
                "SELECT publisher_id FROM t_notification WHERE id = ?", Long.class, notificationId));
        assertEquals(2, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_notification_receipt WHERE notification_id = ?", Integer.class, notificationId));

        NotificationItem published = service.list(null).get(0);
        assertEquals(0, published.getConfirmedCount());
        assertEquals(2, published.getTotalCount());
        assertEquals("选课通知.pdf", published.getOriginalName());

        StudentNotificationItem beforeConfirm = service.listForStudent("20260001").get(0);
        assertFalse(beforeConfirm.getIsConfirmed());
        assertEquals("选课通知.pdf", beforeConfirm.getOriginalName());

        service.confirm(notificationId, "20260001");
        StudentNotificationItem afterConfirm = service.listForStudent("20260001").get(0);
        assertTrue(afterConfirm.getIsConfirmed());
        assertEquals(1, service.list(null).get(0).getConfirmedCount());

        request.setId(notificationId);
        request.setContent("通知内容已经修改");
        service.save(request, 9L);
        assertFalse(service.listForStudent("20260001").get(0).getIsConfirmed());
        assertEquals(0, service.list(null).get(0).getConfirmedCount());
    }

    @Test
    void receiptViewIncludesActiveStudentsAddedAfterPublication() {
        Long notificationId = service.save(request(), 9L);
        jdbcTemplate.update("INSERT INTO t_student VALUES (4, '20260004', '王五', 1, CURRENT_TIMESTAMP)");

        List<NotificationReceiptItem> receipts = service.listReceipts(notificationId);

        assertEquals(3, receipts.size());
        NotificationReceiptItem newStudent = receipts.stream()
                .filter(item -> "20260004".equals(item.getStudentNo()))
                .findFirst()
                .orElseThrow();
        assertFalse(newStudent.getIsConfirmed());
        assertEquals("王五", newStudent.getStudentName());
    }

    @Test
    void rejectsInvalidAttachmentAndUnknownNotification() {
        NotificationSaveRequest request = request();
        request.setFileId(999L);
        assertThrows(IllegalArgumentException.class, () -> service.save(request, 9L));
        assertThrows(IllegalArgumentException.class, () -> service.confirm(999L, "20260001"));
    }

    private NotificationSaveRequest request() {
        NotificationSaveRequest request = new NotificationSaveRequest();
        request.setTitle("关于选课的通知");
        request.setContent("请按时完成选课");
        request.setTags("教学,选课");
        request.setIsUrgent(true);
        request.setFileId(100L);
        return request;
    }
}
