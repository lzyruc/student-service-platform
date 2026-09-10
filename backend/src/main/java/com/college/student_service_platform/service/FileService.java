package com.college.student_service_platform.service;

import com.college.student_service_platform.dto.FileUploadResponse;
import com.college.student_service_platform.entity.FileRecord;
import com.college.student_service_platform.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class FileService {

    private final JdbcTemplate jdbcTemplate;

    @Value("${file.upload-dir}")
    private String uploadDir;

    public FileService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public FileUploadResponse uploadFile(MultipartFile file, String businessType, Long uploaderId) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }

        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.trim().isEmpty()) {
            throw new IllegalArgumentException("文件名不能为空");
        }

        originalName = Paths.get(originalName).getFileName().toString();
        Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(uploadPath);

        String extension = getFileExtension(originalName);
        String storedName = UUID.randomUUID() + extension;

        Path targetPath = uploadPath.resolve(storedName).normalize();
        if (!targetPath.startsWith(uploadPath)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "非法文件名");
        }
        Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

        Long id = System.currentTimeMillis();

        String filePath = uploadDir + "/" + storedName;
        String fileType = file.getContentType();
        Long fileSize = file.getSize();

        String sql = """
                INSERT INTO t_file
                (id, original_name, stored_name, file_path, file_type, file_size, uploader_id, business_type, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        jdbcTemplate.update(
                sql,
                id,
                originalName,
                storedName,
                filePath,
                fileType,
                fileSize,
                uploaderId,
                businessType,
                LocalDateTime.now()
        );

        return new FileUploadResponse(
                id,
                originalName,
                storedName,
                filePath,
                fileType,
                fileSize,
                businessType
        );
    }

    public FileRecord getFileById(Long fileId) {
        String sql = """
                SELECT id, original_name, stored_name, file_path, file_type, file_size, uploader_id, business_type, created_at
                FROM t_file
                WHERE id = ?
                """;

        return jdbcTemplate.queryForObject(sql, this::mapFileRecord, fileId);
    }

    public List<FileRecord> listFiles(String businessType) {
        StringBuilder sql = new StringBuilder("""
                SELECT id, original_name, stored_name, file_path, file_type, file_size, uploader_id, business_type, created_at
                FROM t_file
                """);

        if (businessType != null && !businessType.isBlank()) {
            sql.append(" WHERE business_type = ? ORDER BY created_at DESC");
            return jdbcTemplate.query(sql.toString(), this::mapFileRecord, businessType.trim());
        }

        sql.append(" ORDER BY created_at DESC");
        return jdbcTemplate.query(sql.toString(), this::mapFileRecord);
    }

    public Path getFilePath(FileRecord fileRecord) {
        Path root = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path resolved = root.resolve(fileRecord.getStoredName()).normalize();
        if (!resolved.startsWith(root)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "非法文件路径");
        }
        return resolved;
    }

    public void assertCanDownload(Long fileId, String subject, boolean admin) {
        if (admin) return;
        Integer allowed = jdbcTemplate.queryForObject("""
                SELECT COUNT(1) FROM t_file f
                WHERE f.id = ? AND (
                    EXISTS (SELECT 1 FROM t_notification n WHERE n.file_id = f.id)
                    OR EXISTS (SELECT 1 FROM t_certificate_apply a WHERE a.file_id = f.id AND TRIM(a.student_no) = ?)
                    OR EXISTS (SELECT 1 FROM t_user u WHERE u.id = f.uploader_id AND TRIM(u.student_no) = ?)
                )
                """, Integer.class, fileId, subject, subject);
        if (allowed == null || allowed == 0) {
            throw new ApiException(HttpStatus.FORBIDDEN, "无权下载该文件");
        }
    }

    private FileRecord mapFileRecord(ResultSet rs, int rowNum) throws SQLException {
        FileRecord record = new FileRecord();
        record.setId(rs.getLong("id"));
        record.setOriginalName(rs.getString("original_name"));
        record.setStoredName(rs.getString("stored_name"));
        record.setFilePath(rs.getString("file_path"));
        record.setFileType(rs.getString("file_type"));
        record.setFileSize(rs.getLong("file_size"));
        record.setUploaderId(rs.getLong("uploader_id"));
        record.setBusinessType(rs.getString("business_type"));
        record.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        return record;
    }

    private String getFileExtension(String originalName) {
        int index = originalName.lastIndexOf(".");
        if (index == -1) {
            return "";
        }
        return originalName.substring(index);
    }
}
