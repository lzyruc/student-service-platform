package com.college.student_service_platform.controller;

import com.college.student_service_platform.common.Result;
import com.college.student_service_platform.common.AuthContext;
import jakarta.servlet.http.HttpServletRequest;
import com.college.student_service_platform.dto.FileUploadResponse;
import com.college.student_service_platform.entity.FileRecord;
import com.college.student_service_platform.service.FileService;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

@RestController
@RequestMapping("/api/file")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping("/upload")
    public Result<FileUploadResponse> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "businessType", required = false) String businessType,
            @RequestParam(value = "uploaderId", required = false) Long uploaderId
    ) throws IOException {
        FileUploadResponse response = fileService.uploadFile(file, businessType, uploaderId);
        return Result.success("文件上传成功", response);
    }

    @GetMapping("/list")
    public Result<List<FileRecord>> listFiles(
            @RequestParam(value = "businessType", required = false) String businessType
    ) {
        return Result.success("文件列表查询成功", fileService.listFiles(businessType));
    }

    @GetMapping("/download/{fileId}")
    public ResponseEntity<Resource> downloadFile(@PathVariable Long fileId, HttpServletRequest request) throws IOException {
        fileService.assertCanDownload(fileId, AuthContext.subject(request), AuthContext.isAdmin(request));
        FileRecord fileRecord = fileService.getFileById(fileId);
        Path filePath = fileService.getFilePath(fileRecord);

        Resource resource = new UrlResource(filePath.toUri());

        if (!resource.exists() || !resource.isReadable()) {
            return ResponseEntity.notFound().build();
        }

        String encodedFileName = URLEncoder.encode(fileRecord.getOriginalName(), StandardCharsets.UTF_8)
                .replaceAll("\\+", "%20");

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFileName)
                .body(resource);
    }
}
