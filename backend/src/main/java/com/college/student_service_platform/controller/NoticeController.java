package com.college.student_service_platform.controller;

import com.college.student_service_platform.common.Result;
import com.college.student_service_platform.common.AuthContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/student/notice")
public class NoticeController {

    private final JdbcTemplate jdbcTemplate;

    public NoticeController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/list")
    public Result<List<Map<String, Object>>> getList(
            @RequestParam(value = "studentNo", required = false) String requestedStudentNo,
            HttpServletRequest request) {
        String studentNo = AuthContext.studentNo(request, requestedStudentNo);
        String sql = "SELECT n.id, n.title, n.content, n.tags, n.is_urgent AS \"isUrgent\", " +
                "TO_CHAR(n.created_at, 'YYYY-MM-DD') AS \"publishTime\", " +
                "CASE WHEN r.id IS NOT NULL THEN true ELSE false END AS \"isConfirmed\", " +
                "f.id AS \"fileId\", f.original_name AS \"originalName\", f.file_type AS \"fileType\", " +
                "f.file_size AS \"fileSize\", f.business_type AS \"fileBusinessType\", " +
                "CASE WHEN f.id IS NOT NULL THEN '/api/file/download/' || f.id ELSE NULL END AS \"downloadUrl\" " +
                "FROM t_notification n " +
                "LEFT JOIN t_notification_receipt r ON n.id = r.notification_id AND r.student_no = ? " +
                "LEFT JOIN t_file f ON n.file_id = f.id " +
                "ORDER BY n.is_urgent DESC, n.created_at DESC";

        List<Map<String, Object>> notices = jdbcTemplate.queryForList(sql, studentNo);
        return Result.success("精准通知拉取成功", notices);
    }

    @PostMapping("/confirm")
    public Result<Void> confirmNotice(@RequestBody Map<String, Object> req, HttpServletRequest request) {
        String requestedStudentNo = req.get("studentNo") == null ? null : String.valueOf(req.get("studentNo"));
        String studentNo = AuthContext.studentNo(request, requestedStudentNo);
        Long notificationId = Long.valueOf(req.get("notificationId").toString());

        String existsSql = "SELECT COUNT(*) FROM t_notification_receipt WHERE notification_id = ? AND student_no = ?";
        Integer exists = jdbcTemplate.queryForObject(existsSql, Integer.class, notificationId, studentNo);
        if (exists != null && exists > 0) {
            String updateSql = "UPDATE t_notification_receipt SET is_confirmed = true, confirmed_at = CURRENT_TIMESTAMP " +
                    "WHERE notification_id = ? AND student_no = ?";
            int rows = jdbcTemplate.update(updateSql, notificationId, studentNo);
            return rows > 0 ? Result.success("回执成功", null) : Result.fail("回执确认失败");
        }

        String insertSql = "INSERT INTO t_notification_receipt (id, notification_id, student_no, is_confirmed, confirmed_at) " +
                "VALUES (?, ?, ?, true, CURRENT_TIMESTAMP)";

        long mockId = System.currentTimeMillis();
        int rows = jdbcTemplate.update(insertSql, mockId, notificationId, studentNo);
        return rows > 0 ? Result.success("回执成功", null) : Result.fail("回执确认失败");
    }
}
