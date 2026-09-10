package com.college.student_service_platform.controller;

import com.college.student_service_platform.common.Result;
import com.college.student_service_platform.common.AuthContext;
import com.college.student_service_platform.dto.AiAskRequest;
import com.college.student_service_platform.service.external.AiServiceClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class AiProxyController {

    private final AiServiceClient aiServiceClient;

    public AiProxyController(AiServiceClient aiServiceClient) {
        this.aiServiceClient = aiServiceClient;
    }

    @PostMapping("/student/ai/ask")
    public Result<Object> ask(@Valid @RequestBody AiAskRequest request, HttpServletRequest httpRequest) {
        request.setStudentNo(AuthContext.studentNo(httpRequest, request.getStudentNo()));
        Object response = aiServiceClient.ask(request);
        return Result.success("AI ask succeeded", response);
    }

    @PostMapping("/admin/ai/ingest-all")
    public Result<Object> ingestAll() {
        Object response = aiServiceClient.ingestAll();
        return Result.success("Knowledge base ingest succeeded", response);
    }
}
