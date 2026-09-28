package com.college.student_service_platform.controller;

import com.college.student_service_platform.common.Result;
import com.college.student_service_platform.common.AuthContext;
import jakarta.servlet.http.HttpServletRequest;
import com.college.student_service_platform.dto.CertificateApplySubmitRequest;
import com.college.student_service_platform.service.CertificateApplyService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/api/student/certificate", "/api/student/cert"})
public class CertificateController {

    private final JdbcTemplate jdbcTemplate;
    private final CertificateApplyService certificateApplyService;

    public CertificateController(JdbcTemplate jdbcTemplate, CertificateApplyService certificateApplyService) {
        this.jdbcTemplate = jdbcTemplate;
        this.certificateApplyService = certificateApplyService;
    }

    @PostMapping("/apply")
    public Result<Void> apply(
            @RequestBody(required = false) Map<String, Object> requestBody,
            @RequestParam(value = "studentNo", required = false) String studentNoParam,
            @RequestParam(value = "certificateType", required = false) String certificateTypeParam,
            @RequestParam(value = "extraData", required = false) String extraDataParam,
            HttpServletRequest httpRequest
    ) {
        String requestedStudentNo = pickFirstNonBlank(studentNoParam, bodyValue(requestBody, "studentNo"));
        String studentNo = AuthContext.studentNo(httpRequest, requestedStudentNo);
        String certificateType = pickFirstNonBlank(certificateTypeParam, bodyValue(requestBody, "certificateType"));
        String extraData = pickFirstNonBlank(extraDataParam, bodyValue(requestBody, "extraData"), "无");

        if (!StringUtils.hasText(studentNo) || !StringUtils.hasText(certificateType)) {
            return Result.fail("studentNo、certificateType 不能为空");
        }

        CertificateApplySubmitRequest req = new CertificateApplySubmitRequest();
        req.setStudentNo(studentNo);
        req.setCertificateType(certificateType);
        req.setExtraData(extraData);
        certificateApplyService.submit(req);
        return Result.success("提交成功", null);
    }

    @GetMapping("/history")
    public Result<List<Map<String, Object>>> getHistory(
            @RequestParam(value = "studentNo", required = false) String requestedStudentNo,
            HttpServletRequest request) {
        String studentNo = AuthContext.studentNo(request, requestedStudentNo);
        String sql = "SELECT id, student_no AS \"studentNo\", certificate_type AS \"certificateType\", " +
                "apply_status AS \"applyStatus\", extra_data AS \"extraData\", file_id AS \"fileId\", " +
                "TO_CHAR(created_at, 'YYYY-MM-DD HH24:MI:SS') AS \"createdAt\" " +
                "FROM t_certificate_apply WHERE student_no = ? ORDER BY created_at DESC";

        List<Map<String, Object>> history = jdbcTemplate.queryForList(sql, studentNo);
        return Result.success("申请历史拉取成功", history);
    }

    private String bodyValue(Map<String, Object> requestBody, String key) {
        if (requestBody == null) {
            return null;
        }
        Object value = requestBody.get(key);
        return value == null ? null : String.valueOf(value).trim();
    }

    private String pickFirstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }
}
