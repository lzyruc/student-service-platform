package com.college.student_service_platform.controller;

import com.college.student_service_platform.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping("/api/health")
    public Result<Void> health() {
        return Result.success("系统运行正常", null);
    }
}
