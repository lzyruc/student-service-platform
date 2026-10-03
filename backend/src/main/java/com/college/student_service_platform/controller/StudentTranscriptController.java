package com.college.student_service_platform.controller;

import com.college.student_service_platform.common.AuthContext;
import com.college.student_service_platform.common.Result;
import com.college.student_service_platform.dto.StudentTranscriptResponse;
import com.college.student_service_platform.service.StudentTranscriptService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/student/transcript")
public class StudentTranscriptController {
    private final StudentTranscriptService transcripts;

    public StudentTranscriptController(StudentTranscriptService transcripts) {
        this.transcripts = transcripts;
    }

    @GetMapping
    public Result<StudentTranscriptResponse> current(
            @RequestParam(value = "studentNo", required = false) String requestedStudentNo, HttpServletRequest request) {
        return Result.success(transcripts.getCurrent(AuthContext.studentNo(request, requestedStudentNo)));
    }

    @PostMapping
    public Result<StudentTranscriptResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "studentNo", required = false) String requestedStudentNo, HttpServletRequest request) throws IOException {
        String studentNo = AuthContext.studentNo(request, requestedStudentNo);
        return Result.success("成绩单已保存，可直接进行课程分析", transcripts.toResponse(transcripts.save(studentNo, file)));
    }
}
