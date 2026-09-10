package com.college.student_service_platform.controller;

import com.college.student_service_platform.common.Result;
import com.college.student_service_platform.service.AdminAuthService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AdminAuthService authService;

    public AuthController(AdminAuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        AdminAuthService.LoginResult login = authService.authenticate(
                body.get("username"), body.get("password"), body.get("role"));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", login.id());
        data.put("username", login.username());
        data.put("role_code", login.role());
        if ("student".equals(login.role())) data.put("student_no", login.subject());
        data.put("token", login.token());
        return Result.success("登录成功", data);
    }
}
