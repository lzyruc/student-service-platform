package com.college.student_service_platform.controller;

import com.college.student_service_platform.common.Result;
import com.college.student_service_platform.dto.NotificationItem;
import com.college.student_service_platform.dto.NotificationReceiptItem;
import com.college.student_service_platform.dto.NotificationSaveRequest;
import com.college.student_service_platform.service.NotificationService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notification")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping("/save")
    public Result<Long> save(@RequestBody NotificationSaveRequest request) {
        Long id = notificationService.save(request);
        return Result.success("保存成功", id);
    }

    @GetMapping("/list")
    public Result<List<NotificationItem>> list(@RequestParam(value = "keyword", required = false) String keyword) {
        return Result.success(notificationService.list(keyword));
    }

    @GetMapping("/{id}")
    public Result<NotificationItem> get(@PathVariable Long id) {
        return Result.success(notificationService.getById(id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        notificationService.delete(id);
        return Result.success();
    }

    @GetMapping("/{id}/receipts")
    public Result<List<NotificationReceiptItem>> receipts(@PathVariable Long id) {
        return Result.success(notificationService.listReceipts(id));
    }

    @PostMapping("/{id}/confirm")
    public Result<Void> confirm(@PathVariable Long id, @RequestParam("studentNo") String studentNo) {
        notificationService.confirm(id, studentNo);
        return Result.success();
    }
}

