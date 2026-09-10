package com.college.student_service_platform.controller;

import com.college.student_service_platform.common.Result;
import com.college.student_service_platform.common.AuthContext;
import com.college.student_service_platform.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import com.college.student_service_platform.dto.CertificateApplyDetail;
import com.college.student_service_platform.dto.CertificateApplyItem;
import com.college.student_service_platform.dto.CertificateApplySubmitRequest;
import com.college.student_service_platform.dto.CertificateDecisionRequest;
import com.college.student_service_platform.service.CertificateApplyService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/certificate")
public class CertificateApplyController {

    private final CertificateApplyService certificateApplyService;
    private final JdbcTemplate jdbcTemplate;

    public CertificateApplyController(CertificateApplyService certificateApplyService, JdbcTemplate jdbcTemplate) {
        this.certificateApplyService = certificateApplyService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostMapping("/apply/submit")
    public Result<Long> submit(@Valid @RequestBody CertificateApplySubmitRequest request, HttpServletRequest httpRequest) {
        request.setStudentNo(AuthContext.studentNo(httpRequest, request.getStudentNo()));
        Long id = certificateApplyService.submit(request);
        return Result.success("提交成功", id);
    }

    @GetMapping("/apply/list")
    public Result<List<CertificateApplyItem>> list(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "keyword", required = false) String keyword,
            HttpServletRequest request
    ) {
        List<CertificateApplyItem> items = certificateApplyService.list(status, keyword);
        if (!AuthContext.isAdmin(request)) {
            String studentNo = AuthContext.subject(request);
            items = items.stream().filter(item -> studentNo.equals(item.getStudentNo())).toList();
        }
        return Result.success(items);
    }

    @GetMapping("/apply/{id}")
    public Result<CertificateApplyDetail> detail(@PathVariable("id") Long id, HttpServletRequest request) {
        CertificateApplyDetail detail = certificateApplyService.detail(id);
        assertCanAccess(detail, request);
        return Result.success(detail);
    }

    @PostMapping("/apply/{id}/decision")
    public Result<Void> decision(
            @PathVariable("id") Long id,
            @Valid @RequestBody CertificateDecisionRequest request,
            HttpServletRequest httpRequest
    ) {
        String username = AuthContext.subject(httpRequest);
        Long approverId = jdbcTemplate.query(
                "SELECT id FROM t_user WHERE username = ? AND role_code = 'admin'",
                rs -> rs.next() ? rs.getLong("id") : null,
                username
        );
        certificateApplyService.decide(id, request, approverId, username);
        return Result.success("处理成功", null);
    }

    @DeleteMapping("/apply/{id}")
    public Result<Void> delete(@PathVariable("id") Long id, HttpServletRequest request) {
        assertCanAccess(certificateApplyService.detail(id), request);
        certificateApplyService.delete(id);
        return Result.success();
    }

    private void assertCanAccess(CertificateApplyDetail detail, HttpServletRequest request) {
        if (AuthContext.isAdmin(request)) return;
        if (detail == null || detail.getApply() == null ||
                !AuthContext.subject(request).equals(detail.getApply().getStudentNo())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "不能访问其他学生的证明申请");
        }
    }
}
