package com.college.student_service_platform.service;

import com.college.student_service_platform.common.ApiException;
import com.college.student_service_platform.dto.FileUploadResponse;
import com.college.student_service_platform.dto.StudentTranscriptResponse;
import com.college.student_service_platform.entity.FileRecord;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class StudentTranscriptService {
    private final JdbcTemplate jdbcTemplate;
    private final FileService fileService;

    public StudentTranscriptService(JdbcTemplate jdbcTemplate, FileService fileService) {
        this.jdbcTemplate = jdbcTemplate;
        this.fileService = fileService;
    }

    public Long requireUserId(String studentNo) {
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT id FROM t_user WHERE student_no = ? AND role_code = 'student' LIMIT 1", Long.class, studentNo);
        if (ids.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "未找到该学生的用户账号");
        return ids.get(0);
    }

    public void validateUpload(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("请选择成绩单 PDF");
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new IllegalArgumentException("仅支持上传 PDF 成绩单");
        }
        if (file.getSize() > 20L * 1024 * 1024) throw new IllegalArgumentException("成绩单不能超过 20MB");
        try (InputStream input = file.getInputStream()) {
            if (!"%PDF-".equals(new String(input.readNBytes(5), StandardCharsets.US_ASCII))) {
                throw new IllegalArgumentException("文件内容不是有效的 PDF 成绩单");
            }
        }
    }

    public FileRecord save(String studentNo, MultipartFile file) throws IOException {
        validateUpload(file);
        FileUploadResponse uploaded = fileService.uploadFile(file, "transcript", requireUserId(studentNo));
        return fileService.getFileById(uploaded.getId());
    }

    public StudentTranscriptResponse getCurrent(String studentNo) {
        FileRecord file = fileService.findLatestFileByUploader(requireUserId(studentNo), "transcript");
        return file == null ? null : toResponse(file);
    }

    public FileRecord requireCurrent(String studentNo) {
        FileRecord file = fileService.findLatestFileByUploader(requireUserId(studentNo), "transcript");
        if (file == null) throw new ApiException(HttpStatus.NOT_FOUND, "请先上传成绩单，再进行课程分析");
        resolveForAnalysis(studentNo, file);
        return file;
    }

    public Path resolveForAnalysis(String studentNo, FileRecord file) {
        if (!Objects.equals(file.getUploaderId(), requireUserId(studentNo)) || !"transcript".equals(file.getBusinessType())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "无权使用这份成绩单");
        }
        Path path = fileService.getFilePath(file);
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "已保存的成绩单文件不可用，请重新上传");
        }
        if (file.getOriginalName() == null || !file.getOriginalName().toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new IllegalArgumentException("已保存的文件不是 PDF 成绩单，请重新上传");
        }
        return path;
    }

    public StudentTranscriptResponse toResponse(FileRecord file) {
        Path path = fileService.getFilePath(file);
        boolean available = Files.isRegularFile(path) && Files.isReadable(path)
                && file.getOriginalName() != null && file.getOriginalName().toLowerCase(Locale.ROOT).endsWith(".pdf");
        return new StudentTranscriptResponse(file.getId(), file.getOriginalName(), file.getFileSize(), file.getCreatedAt(), available);
    }
}
