package com.college.student_service_platform.controller;

import com.college.student_service_platform.common.ApiException;
import com.college.student_service_platform.common.AuthContext;
import com.college.student_service_platform.common.Result;
import com.college.student_service_platform.dto.PolicyDocumentItem;
import com.college.student_service_platform.dto.PolicyDocumentPageResponse;
import com.college.student_service_platform.dto.PolicyDocumentSaveRequest;
import com.college.student_service_platform.service.PolicyDocumentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/knowledge/documents")
public class PolicyDocumentController {
    private final PolicyDocumentService policyDocumentService;

    public PolicyDocumentController(PolicyDocumentService policyDocumentService) {
        this.policyDocumentService = policyDocumentService;
    }

    @PostMapping
    public Result<PolicyDocumentItem> create(
            @Valid @RequestBody PolicyDocumentSaveRequest requestBody,
            HttpServletRequest request
    ) {
        String adminUsername = requireAdmin(request);
        return Result.success("政策文档创建成功", policyDocumentService.create(requestBody, adminUsername));
    }

    @GetMapping
    public Result<PolicyDocumentPageResponse> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String audience,
            @RequestParam(required = false) String docStatus,
            @RequestParam(required = false) String ingestStatus,
            HttpServletRequest request
    ) {
        requireAdmin(request);
        return Result.success(policyDocumentService.list(
                page,
                pageSize,
                keyword,
                category,
                audience,
                docStatus,
                ingestStatus
        ));
    }

    @GetMapping("/{id}")
    public Result<PolicyDocumentItem> get(@PathVariable Long id, HttpServletRequest request) {
        requireAdmin(request);
        return Result.success(policyDocumentService.get(id));
    }

    @PutMapping("/{id}")
    public Result<PolicyDocumentItem> update(
            @PathVariable Long id,
            @Valid @RequestBody PolicyDocumentSaveRequest requestBody,
            HttpServletRequest request
    ) {
        requireAdmin(request);
        return Result.success("政策文档更新成功", policyDocumentService.update(id, requestBody));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        requireAdmin(request);
        policyDocumentService.delete(id);
        return Result.success("政策文档删除成功", null);
    }

    @PostMapping("/{id}/publish")
    public Result<PolicyDocumentItem> publish(@PathVariable Long id, HttpServletRequest request) {
        requireAdmin(request);
        return Result.success("政策文档发布成功", policyDocumentService.publish(id));
    }

    private String requireAdmin(HttpServletRequest request) {
        if (!AuthContext.isAdmin(request)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "仅管理员可以管理知识库");
        }
        return AuthContext.subject(request);
    }
}
