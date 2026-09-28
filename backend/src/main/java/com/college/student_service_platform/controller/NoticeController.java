package com.college.student_service_platform.controller;

import com.college.student_service_platform.common.Result;
import com.college.student_service_platform.common.AuthContext;
import com.college.student_service_platform.dto.StudentNotificationItem;
import com.college.student_service_platform.service.NotificationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/student/notice")
public class NoticeController {

    private final NotificationService notificationService;

    public NoticeController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/list")
    public Result<List<StudentNotificationItem>> getList(
            @RequestParam(value = "studentNo", required = false) String requestedStudentNo,
            HttpServletRequest request) {
        String studentNo = AuthContext.studentNo(request, requestedStudentNo);
        return Result.success("精准通知拉取成功", notificationService.listForStudent(studentNo));
    }

    @PostMapping("/confirm")
    public Result<Void> confirmNotice(@RequestBody Map<String, Object> req, HttpServletRequest request) {
        String requestedStudentNo = req.get("studentNo") == null ? null : String.valueOf(req.get("studentNo"));
        String studentNo = AuthContext.studentNo(request, requestedStudentNo);
        Object notificationIdValue = req.get("notificationId");
        if (notificationIdValue == null) {
            return Result.fail(400, "notificationId 不能为空");
        }
        Long notificationId;
        try {
            notificationId = Long.valueOf(String.valueOf(notificationIdValue));
        } catch (NumberFormatException e) {
            return Result.fail(400, "notificationId 格式不正确");
        }
        notificationService.confirm(notificationId, studentNo);
        return Result.success("回执成功", null);
    }
}
