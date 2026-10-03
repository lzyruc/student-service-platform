package com.college.student_service_platform.controller;

import com.college.student_service_platform.common.ApiException;
import com.college.student_service_platform.common.AuthContext;
import com.college.student_service_platform.common.Result;
import com.college.student_service_platform.service.AcademicAnalysisService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class AcademicWarningProxyController {
    private static final Logger log = LoggerFactory.getLogger(AcademicWarningProxyController.class);
    private final AcademicAnalysisService analysis;

    public AcademicWarningProxyController(AcademicAnalysisService analysis) {
        this.analysis = analysis;
    }

    @PostMapping("/student/warning/analyze")
    public Result<Object> analyzeTranscript(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "studentNo", required = false) String requestedStudentNo,
            HttpServletRequest request) {
        return analyzeForStudent(AuthContext.studentNo(request, requestedStudentNo), file);
    }

    @PostMapping("/student/warning/analyze-saved")
    public Result<Object> analyzeSavedTranscript(
            @RequestParam(value = "studentNo", required = false) String requestedStudentNo,
            HttpServletRequest request) {
        return analyzeForStudent(AuthContext.studentNo(request, requestedStudentNo), null);
    }

    private Result<Object> analyzeForStudent(String studentNo, MultipartFile file) {
        try {
            Object response = file == null ? analysis.analyzeSavedAndSave(studentNo)
                    : analysis.analyzeUploadedAndSave(studentNo, file);
            return Result.success("学业预警分析成功", response);
        } catch (AcademicAnalysisService.MissingAcademicDataException e) {
            return Result.fail(404, e.getMessage());
        } catch (ApiException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            return Result.fail(400, e.getMessage());
        } catch (Exception e) {
            log.error("Academic warning analysis failed for student {}", studentNo, e);
            return Result.fail("学业预警分析失败，请稍后重试");
        }
    }
}
